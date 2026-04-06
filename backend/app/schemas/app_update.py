from datetime import datetime

from pydantic import BaseModel


class VersionResponse(BaseModel):
    version_code: int
    version_name: str
    release_notes: str | None = None
    apk_url: str
    apk_size_bytes: int | None = None
    sha256: str | None = None
    is_mandatory: bool = False


class ReleaseCreateResponse(BaseModel):
    status: str
    version_code: int
    sha256: str


class ReleaseResponse(BaseModel):
    id: int
    version_code: int
    version_name: str
    release_notes: str | None = None
    apk_url: str
    apk_size_bytes: int | None = None
    sha256: str | None = None
    min_sdk: int
    created_at: datetime
    is_mandatory: bool

    model_config = {"from_attributes": True}
