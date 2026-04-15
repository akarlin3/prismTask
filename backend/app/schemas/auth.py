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
