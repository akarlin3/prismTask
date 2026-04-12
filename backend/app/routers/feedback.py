import json
import secrets

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user, get_optional_user
from app.models import BugReportModel, User
from app.schemas.feedback import BugReportCreate, BugReportResponse, BugReportStatusUpdate

router = APIRouter(prefix="/feedback", tags=["feedback"])

# Admin user ID — for now, hardcoded. Replace with a proper role check later.
ADMIN_USER_ID = 1


@router.post("/bug-report", status_code=status.HTTP_201_CREATED)
async def create_bug_report(
    report_data: BugReportCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User | None = Depends(get_optional_user),
):
    report_id = secrets.token_urlsafe(32)

    report = BugReportModel(
        report_id=report_id,
        user_id=current_user.id if current_user else None,
        category=report_data.category,
        description=report_data.description,
        severity=report_data.severity,
        steps=json.dumps(report_data.steps),
        screenshot_uris=json.dumps(report_data.screenshot_uris),
        device_model=report_data.device_model,
        device_manufacturer=report_data.device_manufacturer,
        android_version=report_data.android_version,
        app_version=report_data.app_version,
        app_version_code=report_data.app_version_code,
        build_type=report_data.build_type,
        user_tier=report_data.user_tier,
        current_screen=report_data.current_screen,
        task_count=report_data.task_count,
        habit_count=report_data.habit_count,
        available_ram_mb=report_data.available_ram_mb,
        free_storage_mb=report_data.free_storage_mb,
        network_type=report_data.network_type,
        battery_percent=report_data.battery_percent,
        is_charging=report_data.is_charging,
        status="SUBMITTED",
        diagnostic_log=report_data.diagnostic_log,
        submitted_via=report_data.submitted_via,
    )

    db.add(report)
    await db.flush()
    await db.refresh(report)

    return {"id": report.report_id, "status": "submitted", "message": "Thanks!"}


@router.get("/bug-reports", response_model=list[BugReportResponse])
async def list_bug_reports(
    status_filter: str | None = None,
    severity: str | None = None,
    page: int = 1,
    limit: int = 20,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    if current_user.id != ADMIN_USER_ID:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin access required")

    query = select(BugReportModel).order_by(BugReportModel.created_at.desc())

    if status_filter:
        query = query.where(BugReportModel.status == status_filter)
    if severity:
        query = query.where(BugReportModel.severity == severity)

    offset = (page - 1) * limit
    query = query.offset(offset).limit(limit)

    result = await db.execute(query)
    reports = result.scalars().all()

    return reports


@router.patch("/bug-reports/{report_id}", response_model=BugReportResponse)
async def update_bug_report_status(
    report_id: str,
    update: BugReportStatusUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    if current_user.id != ADMIN_USER_ID:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin access required")

    result = await db.execute(
        select(BugReportModel).where(BugReportModel.report_id == report_id)
    )
    report = result.scalar_one_or_none()
    if not report:
        raise HTTPException(status_code=404, detail="Report not found")

    valid_statuses = {"SUBMITTED", "ACKNOWLEDGED", "FIXED", "WONT_FIX"}
    if update.status not in valid_statuses:
        raise HTTPException(status_code=422, detail=f"Invalid status. Must be one of: {valid_statuses}")

    report.status = update.status
    if update.admin_notes is not None:
        report.admin_notes = update.admin_notes

    await db.flush()
    await db.refresh(report)

    return report
