import hashlib
import os

from fastapi import APIRouter, Depends, Form, Header, HTTPException, UploadFile, File
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import get_db
from app.models import AppRelease
from app.schemas.app_update import ReleaseCreateResponse, VersionResponse

RELEASES_DIR = "/app/releases"

router = APIRouter(prefix="/app", tags=["App Updates"])


async def verify_deploy_key(authorization: str = Header(...)):
    if not settings.DEPLOY_API_KEY:
        raise HTTPException(503, "Deploy key not configured")
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

    apk_path = os.path.join(RELEASES_DIR, f"averytask-{rel.version_name}.apk")
    if not os.path.exists(apk_path):
        raise HTTPException(404, "APK file not found")

    return FileResponse(
        apk_path,
        media_type="application/vnd.android.package-archive",
        filename=f"AveryTask-{rel.version_name}.apk",
    )


@router.post("/releases", response_model=ReleaseCreateResponse)
async def create_release(
    version_code: int = Form(...),
    version_name: str = Form(...),
    release_notes: str = Form(""),
    is_mandatory: bool = Form(False),
    apk: UploadFile = File(...),
    _: None = Depends(verify_deploy_key),
    db: AsyncSession = Depends(get_db),
):
    """Upload new release. Called by GitHub Actions with a deploy key."""
    os.makedirs(RELEASES_DIR, exist_ok=True)
    apk_path = os.path.join(RELEASES_DIR, f"averytask-{version_name}.apk")

    contents = await apk.read()
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
