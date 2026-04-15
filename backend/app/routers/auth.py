import logging
import secrets

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.middleware.rate_limit import auth_rate_limiter
from app.models import User
from app.schemas.auth import FirebaseTokenLogin, Token, TokenRefresh, UpdateTierRequest, UserCreate, UserLogin, UserResponse
from app.services.auth import (
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_firebase_token,
    verify_password,
)
from app.services.billing import validate_purchase

logger = logging.getLogger(__name__)

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

        await db.flush()
        await db.refresh(user)

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
async def get_me(current_user: User = Depends(get_current_user)):
    return current_user


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
