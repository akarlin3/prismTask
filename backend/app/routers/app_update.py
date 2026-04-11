import hashlib
import os
import re

from fastapi import APIRouter, Depends, Form, HTTPException, Request, UploadFile, File
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import get_db
from app.models import AppRelease
from app.schemas.app_update import ReleaseCreateResponse, VersionResponse

RELEASES_DIR = "/app/releases"
MAX_APK_SIZE = 200 * 1024 * 1024  # 200 MB
APK_MAGIC_BYTES = b"PK\x03\x04"  # ZIP/APK signature
VERSION_NAME_PATTERN = re.compile(r"^[a-zA-Z0-9.\-_]+$")

router = APIRouter(prefix="/app", tags=["App Updates"])


async def verify_deploy_key(request: Request):
    # Read the Authorization header directly from the Request instead of
    # declaring it as a required ``Header(...)`` parameter. Using ``Header(...)``
    # causes FastAPI to surface a 422 validation error when the header is
    # missing, which collides with Form body validation errors on this
    # endpoint and masks the auth rejection. Reading it via Request lets us
    # return explicit 401/403/503 responses before body parsing runs.
    if not settings.DEPLOY_API_KEY:
        raise HTTPException(503, "Deploy key not configured")
    authorization = request.headers.get("authorization")
    if not authorization:
        raise HTTPException(401, "Missing deploy key")
    if authorization != f"Bearer {settings.DEPLOY_API_KEY}":
        raise HTTPException(403, "Invalid deploy key")


@router.get("/version", response_model=VersionResponse)
async def get_latest_version(db: AsyncSession = Depends(get_db)):
    """Check for updates. No auth required."""
    result = await db.execute(
        select(AppRelease).order_by(AppRelease.version_code.desc()).limit(1)
    )
    latest = result.scalar_one_or_none()
    if not latest:
        raise HTTPException(404, "No releases found")
    return VersionResponse(
        version_code=latest.version_code,
        version_name=latest.version_name,
        release_notes=latest.release_notes,
        apk_url=latest.apk_url,
        apk_size_bytes=latest.apk_size_bytes,
        sha256=latest.sha256,
        is_mandatory=latest.is_mandatory,
    )


@router.get("/download/{version_code}")
async def download_apk(version_code: int, db: AsyncSession = Depends(get_db)):
    """Download APK file. No auth required."""
    result = await db.execute(
        select(AppRelease).where(AppRelease.version_code == version_code)
    )
    rel = result.scalar_one_or_none()
    if not rel:
        raise HTTPException(404, "Release not found")

    if not VERSION_NAME_PATTERN.match(rel.version_name):
        raise HTTPException(400, "Invalid version name")
    apk_path = os.path.join(RELEASES_DIR, f"prismtask-{rel.version_name}.apk")
    apk_path = os.path.realpath(apk_path)
    if not apk_path.startswith(os.path.realpath(RELEASES_DIR)):
        raise HTTPException(400, "Invalid file path")
    if not os.path.exists(apk_path):
        raise HTTPException(404, "APK file not found")

    return FileResponse(
        apk_path,
        media_type="application/vnd.android.package-archive",
        filename=f"PrismTask-{rel.version_name}.apk",
    )


@router.post(
    "/releases",
    response_model=ReleaseCreateResponse,
    dependencies=[Depends(verify_deploy_key)],
)
async def create_release(
    version_code: int = Form(...),
    version_name: str = Form(...),
    release_notes: str = Form(""),
    is_mandatory: bool = Form(False),
    apk: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
):
    """Upload new release. Called by GitHub Actions with a deploy key."""
    if not VERSION_NAME_PATTERN.match(version_name):
        raise HTTPException(400, "Invalid version_name: only alphanumeric, dots, hyphens, underscores allowed")

    os.makedirs(RELEASES_DIR, exist_ok=True)
    apk_path = os.path.join(RELEASES_DIR, f"prismtask-{version_name}.apk")
    apk_path = os.path.realpath(apk_path)
    if not apk_path.startswith(os.path.realpath(RELEASES_DIR)):
        raise HTTPException(400, "Invalid file path")

    contents = await apk.read()

    if len(contents) > MAX_APK_SIZE:
        raise HTTPException(413, f"APK file too large ({len(contents)} bytes, max {MAX_APK_SIZE})")
    if not contents[:4] == APK_MAGIC_BYTES:
        raise HTTPException(400, "Uploaded file is not a valid APK (invalid magic bytes)")

    sha256 = hashlib.sha256(contents).hexdigest()

    with open(apk_path, "wb") as f:
        f.write(contents)

    release = AppRelease(
        version_code=version_code,
        version_name=version_name,
        release_notes=release_notes,
        apk_url=f"/app/download/{version_code}",
        apk_size_bytes=len(contents),
        sha256=sha256,
        is_mandatory=is_mandatory,
    )
    db.add(release)

    return ReleaseCreateResponse(
        status="ok",
        version_code=version_code,
        sha256=sha256,
    )
