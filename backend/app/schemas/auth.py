from pydantic import BaseModel, EmailStr


class UserCreate(BaseModel):
    email: EmailStr
    name: str
    password: str


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class TokenRefresh(BaseModel):
    refresh_token: str


class UpdateTierRequest(BaseModel):
    tier: str
    # Google Play purchase receipt. Required for any non-FREE tier so the
    # server can validate the purchase before elevating the user. Downgrades
    # to FREE (e.g. cancellation, refund) do not require a token.
    purchase_token: str | None = None
    product_id: str | None = None


class FirebaseTokenLogin(BaseModel):
    firebase_token: str
    name: str | None = None


class UserResponse(BaseModel):
    id: int
    email: str
    name: str
    tier: str = "FREE"
    is_admin: bool = False
    effective_tier: str = "FREE"

    model_config = {"from_attributes": True}


class DeletionRequest(BaseModel):
    """Body for POST /auth/me/deletion. ``initiated_from`` is one of
    ``"android"`` / ``"web"`` / ``"email"`` so support can tell where a
    deletion request came from after the fact."""

    initiated_from: str = "android"


class DeletionStatusResponse(BaseModel):
    """Returned by POST /auth/me/deletion + GET /auth/me/deletion. The Android
    sign-in guard reads ``deletion_scheduled_for`` to decide whether to offer
    restore or trigger permanent deletion."""

    deletion_pending_at: str | None = None
    deletion_scheduled_for: str | None = None
    deletion_initiated_from: str | None = None
    grace_period_days: int = 30
