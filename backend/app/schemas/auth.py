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


class UserResponse(BaseModel):
    id: int
    email: str
    name: str
    tier: str = "FREE"
    is_admin: bool = False

    model_config = {"from_attributes": True}
