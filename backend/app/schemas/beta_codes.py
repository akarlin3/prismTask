from pydantic import BaseModel, Field


class RedeemRequest(BaseModel):
    code: str = Field(..., min_length=1, max_length=64)


class RedeemResponse(BaseModel):
    granted: bool
    pro_until: str | None = None
