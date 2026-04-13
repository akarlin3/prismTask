"""Admin debug log viewer — list, read, and delete user bug-report logs
stored in Firebase Storage.

Bug reports are stored in Firestore at  users/{userId}/bug_reports/{reportId}
with screenshots in Firebase Storage at users/{userId}/bug_reports/{reportId}/screenshot_{i}.jpg
and the diagnostic log text embedded in the Firestore document.

This router also surfaces bug reports from the backend SQL database so admins
can access all debug/feedback data from a single interface.
"""

import base64
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.admin import require_admin
from app.models import BugReportModel, User

router = APIRouter(prefix="/admin/debug-logs", tags=["admin"])


# ---------------------------------------------------------------------------
# Response schemas
# ---------------------------------------------------------------------------

class DebugLogSummary(BaseModel):
    id: str
    user_id: int | None
    user_email: str | None = None
    filename: str
    timestamp: str
    size_bytes: int
    device_info: str | None = None
    app_version: str | None = None
    category: str
    severity: str
    status: str


class DebugLogDetail(BaseModel):
    id: str
    user_id: int | None
    user_email: str | None = None
    filename: str
    timestamp: str
    content: str | None
    metadata: dict


class DebugLogStats(BaseModel):
    total_logs: int
    logs_this_week: int
    unique_users: int
    storage_used_bytes: int


class PaginatedDebugLogs(BaseModel):
    items: list[DebugLogSummary]
    total: int
    page: int
    per_page: int
    total_pages: int


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _encode_log_id(report_id: str) -> str:
    """URL-safe base64 encode a report_id for use in URL paths."""
    return base64.urlsafe_b64encode(report_id.encode()).decode().rstrip("=")


def _decode_log_id(log_id: str) -> str:
    """Decode URL-safe base64 log_id back to report_id."""
    padding = 4 - len(log_id) % 4
    if padding != 4:
        log_id += "=" * padding
    return base64.urlsafe_b64decode(log_id.encode()).decode()


def _estimate_size(report: BugReportModel) -> int:
    """Rough estimate of the report's storage footprint in bytes."""
    size = len((report.description or "").encode())
    size += len((report.diagnostic_log or "").encode())
    size += len((report.steps or "").encode())
    size += len((report.screenshot_uris or "").encode())
    size += len((report.admin_notes or "").encode())
    return size


def _build_device_info(report: BugReportModel) -> str | None:
    parts = []
    if report.device_manufacturer:
        parts.append(report.device_manufacturer)
    if report.device_model:
        parts.append(report.device_model)
    if report.android_version:
        parts.append(f"API {report.android_version}")
    return " ".join(parts) if parts else None


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@router.get("/stats", response_model=DebugLogStats)
async def debug_log_stats(
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Return aggregate statistics about stored debug logs."""
    total_q = select(func.count(BugReportModel.id))
    total_result = await db.execute(total_q)
    total_logs = total_result.scalar() or 0

    one_week_ago = datetime.now(timezone.utc) - timedelta(days=7)
    week_q = select(func.count(BugReportModel.id)).where(
        BugReportModel.created_at >= one_week_ago
    )
    week_result = await db.execute(week_q)
    logs_this_week = week_result.scalar() or 0

    users_q = select(func.count(func.distinct(BugReportModel.user_id))).where(
        BugReportModel.user_id.isnot(None)
    )
    users_result = await db.execute(users_q)
    unique_users = users_result.scalar() or 0

    # Estimate total storage from all reports
    all_reports = await db.execute(select(BugReportModel))
    storage_used = sum(_estimate_size(r) for r in all_reports.scalars().all())

    return DebugLogStats(
        total_logs=total_logs,
        logs_this_week=logs_this_week,
        unique_users=unique_users,
        storage_used_bytes=storage_used,
    )


@router.get("", response_model=PaginatedDebugLogs)
async def list_debug_logs(
    user_id: int | None = None,
    sort: str = "newest",
    page: int = 1,
    per_page: int = 20,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """List all debug/feedback logs with pagination, filtering, and sorting."""
    base_q = select(BugReportModel)

    if user_id is not None:
        base_q = base_q.where(BugReportModel.user_id == user_id)

    # Count total matching rows
    count_q = select(func.count()).select_from(base_q.subquery())
    total = (await db.execute(count_q)).scalar() or 0

    # Sort
    if sort == "oldest":
        base_q = base_q.order_by(BugReportModel.created_at.asc())
    else:
        base_q = base_q.order_by(BugReportModel.created_at.desc())

    # Paginate
    offset = (page - 1) * per_page
    base_q = base_q.offset(offset).limit(per_page)

    result = await db.execute(base_q)
    reports = result.scalars().all()

    # Batch-fetch user emails
    user_ids = {r.user_id for r in reports if r.user_id}
    user_map: dict[int, str] = {}
    if user_ids:
        users_result = await db.execute(
            select(User.id, User.email).where(User.id.in_(user_ids))
        )
        user_map = {uid: email for uid, email in users_result.all()}

    items = [
        DebugLogSummary(
            id=_encode_log_id(r.report_id),
            user_id=r.user_id,
            user_email=user_map.get(r.user_id) if r.user_id else None,
            filename=f"{r.report_id}.log",
            timestamp=r.created_at.isoformat() + "Z" if r.created_at else "",
            size_bytes=_estimate_size(r),
            device_info=_build_device_info(r),
            app_version=r.app_version,
            category=r.category,
            severity=r.severity,
            status=r.status,
        )
        for r in reports
    ]

    total_pages = max(1, (total + per_page - 1) // per_page)
    return PaginatedDebugLogs(
        items=items,
        total=total,
        page=page,
        per_page=per_page,
        total_pages=total_pages,
    )


@router.get("/{log_id}", response_model=DebugLogDetail)
async def get_debug_log(
    log_id: str,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Download and return the full content of a specific debug log."""
    try:
        report_id = _decode_log_id(log_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid log ID encoding")

    result = await db.execute(
        select(BugReportModel).where(BugReportModel.report_id == report_id)
    )
    report = result.scalar_one_or_none()
    if not report:
        raise HTTPException(status_code=404, detail="Log not found")

    # Fetch user email
    user_email = None
    if report.user_id:
        user_result = await db.execute(
            select(User.email).where(User.id == report.user_id)
        )
        row = user_result.first()
        if row:
            user_email = row[0]

    # Build full content: description + steps + diagnostic log
    content_parts = [f"Category: {report.category}", f"Severity: {report.severity}"]
    content_parts.append(f"\n--- Description ---\n{report.description}")
    if report.steps:
        content_parts.append(f"\n--- Steps to Reproduce ---\n{report.steps}")
    if report.diagnostic_log:
        content_parts.append(f"\n--- Diagnostic Log ---\n{report.diagnostic_log}")

    metadata = {
        "category": report.category,
        "severity": report.severity,
        "status": report.status,
        "device_model": report.device_model,
        "device_manufacturer": report.device_manufacturer,
        "android_version": report.android_version,
        "app_version": report.app_version,
        "app_version_code": report.app_version_code,
        "build_type": report.build_type,
        "user_tier": report.user_tier,
        "current_screen": report.current_screen,
        "task_count": report.task_count,
        "habit_count": report.habit_count,
        "available_ram_mb": report.available_ram_mb,
        "free_storage_mb": report.free_storage_mb,
        "network_type": report.network_type,
        "battery_percent": report.battery_percent,
        "is_charging": report.is_charging,
        "screenshot_uris": report.screenshot_uris,
        "admin_notes": report.admin_notes,
        "submitted_via": report.submitted_via,
    }

    return DebugLogDetail(
        id=log_id,
        user_id=report.user_id,
        user_email=user_email,
        filename=f"{report.report_id}.log",
        timestamp=report.created_at.isoformat() + "Z" if report.created_at else "",
        content="\n".join(content_parts),
        metadata=metadata,
    )


@router.delete("/{log_id}")
async def delete_debug_log(
    log_id: str,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Delete a debug log by its encoded ID."""
    try:
        report_id = _decode_log_id(log_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid log ID encoding")

    result = await db.execute(
        select(BugReportModel).where(BugReportModel.report_id == report_id)
    )
    report = result.scalar_one_or_none()
    if not report:
        raise HTTPException(status_code=404, detail="Log not found")

    await db.delete(report)

    return {"deleted": True}
