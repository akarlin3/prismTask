import logging
import secrets
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import get_db
from app.middleware.auth import get_current_user
from app.middleware.rate_limit import auth_rate_limiter
from app.models import ProjectMember, User
from app.schemas.auth import DeletionRequest, DeletionStatusResponse, FirebaseTokenLogin, Token, TokenRefresh, UpdateTierRequest, UserCreate, UserLogin, UserResponse
from app.services.auth import (
    create_access_token,
    create_refresh_token,
    decode_token,
    delete_firebase_user,
    hash_password,
    verify_firebase_token,
    verify_password,
)
from app.services.beta_codes import has_active_beta_pro
from app.services.billing import validate_purchase

logger = logging.getLogger(__name__)

# 30-day soft-delete grace window. Any sign-in within this window restores the
# account; a sign-in after the window triggers permanent deletion. The window
# is also documented in the privacy policy and data-safety form — keep them
# in sync if this constant ever changes.
DELETION_GRACE_DAYS = 30
_VALID_INITIATED_FROM = {"android", "web", "email"}


def _apply_admin_allowlist(user: User) -> None:
    """Promote a user to admin if their email is in ADMIN_EMAILS.

    Idempotent and one-way: a user already in the list stays admin, but
    removing them from the env-var list does NOT demote them — admin
    revocation must be done manually in the DB.
    """
    if not user.is_admin and settings.is_admin_email(user.email):
        user.is_admin = True


def _as_utc(dt: datetime) -> datetime:
    """Ensure a datetime is timezone-aware in UTC.

    SQLite (used in tests via aiosqlite) drops tzinfo on DateTime(timezone=True)
    columns when round-tripping, while Postgres preserves it. Normalizing here
    keeps the comparison logic correct on both backends.
    """
    return dt if dt.tzinfo is not None else dt.replace(tzinfo=timezone.utc)

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=Token, status_code=status.HTTP_201_CREATED)
async def register(request: Request, user_data: UserCreate, db: AsyncSession = Depends(get_db)):
    auth_rate_limiter.check(request)
    result = await db.execute(select(User).where(User.email == user_data.email))
    if result.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Email already registered")

    user = User(
        email=user_data.email,
        name=user_data.name,
        hashed_password=hash_password(user_data.password),
    )
    _apply_admin_allowlist(user)
    db.add(user)
    await db.flush()
    await db.refresh(user)

    token_data = {"sub": str(user.id), "email": user.email}
    return Token(
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data),
    )


@router.post("/login", response_model=Token)
async def login(request: Request, credentials: UserLogin, db: AsyncSession = Depends(get_db)):
    auth_rate_limiter.check(request)
    result = await db.execute(select(User).where(User.email == credentials.email))
    user = result.scalar_one_or_none()

    if not user or not verify_password(credentials.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid email or password")

    _apply_admin_allowlist(user)
    await db.flush()

    token_data = {"sub": str(user.id), "email": user.email}
    return Token(
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data),
    )


@router.post("/firebase", response_model=Token)
async def firebase_login(
    request: Request, body: FirebaseTokenLogin, db: AsyncSession = Depends(get_db)
):
    """Authenticate with a Firebase ID token.

    Verifies the token with Firebase Admin SDK, then finds or creates
    the corresponding backend user and returns JWT tokens.
    """
    auth_rate_limiter.check(request)

    decoded = verify_firebase_token(body.firebase_token)
    if decoded is None:
        raise HTTPException(status_code=401, detail="Invalid Firebase token")

    uid: str = decoded["uid"]
    email: str | None = decoded.get("email")
    if not email:
        raise HTTPException(status_code=400, detail="Firebase account has no email")

    # Look up by firebase_uid first, then by email
    result = await db.execute(select(User).where(User.firebase_uid == uid))
    user = result.scalar_one_or_none()

    if user is None:
        # Check if a user with this email already exists (created before Firebase linking)
        result = await db.execute(select(User).where(User.email == email))
        user = result.scalar_one_or_none()

        if user is not None:
            # Link existing account to this Firebase UID
            user.firebase_uid = uid
        else:
            # Create a new user.
            # The hashed_password column is NOT NULL, so we store a hash of
            # a random secret that is never exposed. Firebase-linked users
            # authenticate via the /auth/firebase endpoint — the password
            # login path cannot succeed against this value.
            display_name = decoded.get("name") or body.name or email.split("@")[0]
            user = User(
                email=email,
                name=display_name,
                hashed_password=hash_password(secrets.token_urlsafe(32)),
                firebase_uid=uid,
            )
            db.add(user)

        _apply_admin_allowlist(user)
        await db.flush()
        await db.refresh(user)
    else:
        _apply_admin_allowlist(user)
        await db.flush()

    token_data = {"sub": str(user.id), "email": user.email}
    return Token(
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data),
    )


@router.post("/refresh", response_model=Token)
async def refresh(request: Request, body: TokenRefresh, db: AsyncSession = Depends(get_db)):
    auth_rate_limiter.check(request)
    payload = decode_token(body.refresh_token)
    if payload is None or payload.get("type") != "refresh":
        raise HTTPException(status_code=401, detail="Invalid refresh token")

    user_id = payload.get("sub")
    result = await db.execute(select(User).where(User.id == int(user_id)))
    user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=401, detail="User not found")

    token_data = {"sub": str(user.id), "email": user.email}
    return Token(
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data),
    )


@router.get("/me", response_model=UserResponse)
async def get_me(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Return the current user. ``effective_tier`` is elevated to PRO
    when the user has an active beta-code redemption (sync property
    on ``User`` already handles the admin override)."""
    payload = UserResponse.model_validate(current_user)
    if payload.effective_tier == "FREE" and await has_active_beta_pro(db, current_user.id):
        payload.effective_tier = "PRO"
    return payload


@router.patch("/me/tier", response_model=UserResponse)
async def update_tier(
    body: UpdateTierRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Update the user's subscription tier.

    Called by the Android app after a purchase is confirmed with Google Play.
    Paid tiers require a ``purchase_token`` + ``product_id`` that the server
    validates with the Google Play Developer API. Downgrades to FREE do not
    require a token.
    """
    valid_tiers = {"FREE", "PRO"}
    if body.tier not in valid_tiers:
        raise HTTPException(status_code=400, detail=f"Invalid tier: {body.tier}")

    result = validate_purchase(
        claimed_tier=body.tier,
        purchase_token=body.purchase_token,
        product_id=body.product_id,
    )
    if not result.ok:
        logger.warning(
            "Tier elevation rejected for user %s: %s", current_user.id, result.detail
        )
        raise HTTPException(status_code=402, detail=result.detail)

    current_user.tier = result.validated_tier or body.tier
    await db.flush()
    await db.refresh(current_user)
    return current_user


def _serialize_deletion_status(user: User) -> DeletionStatusResponse:
    return DeletionStatusResponse(
        deletion_pending_at=user.deletion_pending_at.isoformat() if user.deletion_pending_at else None,
        deletion_scheduled_for=user.deletion_scheduled_for.isoformat() if user.deletion_scheduled_for else None,
        deletion_initiated_from=user.deletion_initiated_from,
        grace_period_days=DELETION_GRACE_DAYS,
    )


@router.get("/me/deletion", response_model=DeletionStatusResponse)
async def get_deletion_status(current_user: User = Depends(get_current_user)):
    """Return the current user's deletion-pending status.

    Used by clients that want to surface a "your account is scheduled for
    deletion" banner outside the sign-in flow (e.g. on settings load).
    """
    return _serialize_deletion_status(current_user)


@router.post("/me/deletion", response_model=DeletionStatusResponse)
async def request_deletion(
    body: DeletionRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Mark the current user as pending deletion.

    Idempotent: requesting deletion twice keeps the original ``deletion_pending_at``
    so the grace window doesn't reset on accidental re-taps. The endpoint does
    NOT delete data — that happens at /auth/me/purge after the grace window
    expires (typically driven by the next sign-in).
    """
    if body.initiated_from not in _VALID_INITIATED_FROM:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid initiated_from: {body.initiated_from}",
        )

    if current_user.deletion_pending_at is None:
        now = datetime.now(timezone.utc)
        current_user.deletion_pending_at = now
        current_user.deletion_scheduled_for = now + timedelta(days=DELETION_GRACE_DAYS)
        current_user.deletion_initiated_from = body.initiated_from
        await db.flush()
        await db.refresh(current_user)
        logger.info(
            "Deletion requested: user_id=%s initiated_from=%s scheduled_for=%s",
            current_user.id,
            body.initiated_from,
            current_user.deletion_scheduled_for.isoformat(),
        )
    return _serialize_deletion_status(current_user)


@router.delete("/me/deletion", response_model=DeletionStatusResponse)
async def cancel_deletion(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Cancel a pending deletion (restore the account).

    Idempotent: clearing deletion fields on an already-active account is a
    no-op. The Android restore flow calls this immediately after the user
    taps "Restore Account" on the post-sign-in dialog.
    """
    if current_user.deletion_pending_at is not None:
        logger.info("Deletion canceled: user_id=%s", current_user.id)
        current_user.deletion_pending_at = None
        current_user.deletion_scheduled_for = None
        current_user.deletion_initiated_from = None
        await db.flush()
        await db.refresh(current_user)
    return _serialize_deletion_status(current_user)


@router.post("/me/purge", status_code=204)
async def purge_account(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Permanently delete the current user.

    Called by the Android sign-in handler when the grace window has expired.
    Refuses to run if deletion is not pending OR the grace window hasn't
    closed — a misbehaving client cannot bypass the soft-delete by hitting
    this endpoint directly. CASCADE FKs handle most dependent rows; we
    explicitly clear ``project_members`` first because that relationship is
    not cascade-delete (preserves shared projects owned by other users).
    The Firebase Auth record is deleted via Firebase Admin SDK; failure
    there does not roll back the database delete (best-effort + logged).
    """
    if current_user.deletion_pending_at is None or current_user.deletion_scheduled_for is None:
        raise HTTPException(
            status_code=409,
            detail="Account is not pending deletion",
        )
    if _as_utc(current_user.deletion_scheduled_for) > datetime.now(timezone.utc):
        raise HTTPException(
            status_code=409,
            detail="Grace period has not expired",
        )

    user_id = current_user.id
    firebase_uid = current_user.firebase_uid

    # ProjectMember has no cascade-delete on the User relationship — would
    # raise FK violation. Clear memberships explicitly before deleting the
    # User row; shared projects owned by other users are preserved.
    await db.execute(delete(ProjectMember).where(ProjectMember.user_id == user_id))
    await db.delete(current_user)
    await db.flush()

    if firebase_uid:
        ok = delete_firebase_user(firebase_uid)
        if not ok:
            logger.warning(
                "Firebase Auth deletion failed for user_id=%s firebase_uid=%s — DB row already deleted",
                user_id,
                firebase_uid,
            )

    logger.info("Account purged: user_id=%s firebase_uid=%s", user_id, firebase_uid)
