"""Admin activity log viewer — list and inspect user activity logs.

Activity logs record user actions across the platform (task creation,
project updates, member changes, etc.). This router exposes them to
admins with pagination, filtering, and aggregate statistics.
"""

from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.admin import require_admin
from app.models import ActivityLog, User

router = APIRouter(prefix="/admin/activity-logs", tags=["admin"])


# ---------------------------------------------------------------------------
# Response schemas
# ---------------------------------------------------------------------------

class ActivityLogSummary(BaseModel):
    id: int
    user_id: int
    user_email: str | None = None
    project_id: int
    action: str
    entity_type: str | None = None
    entity_id: int | None = None
    entity_title: str | None = None
    metadata_json: str | None = None
    created_at: str


class ActivityLogStats(BaseModel):
    total_logs: int
    logs_today: int
    logs_this_week: int
    unique_users: int
    top_actions: list[dict]


class PaginatedActivityLogs(BaseModel):
    items: list[ActivityLogSummary]
    total: int
    page: int
    per_page: int
    total_pages: int


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@router.get("/stats", response_model=ActivityLogStats)
async def activity_log_stats(
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Return aggregate statistics about activity logs."""
    total_q = select(func.count(ActivityLog.id))
    total_result = await db.execute(total_q)
    total_logs = total_result.scalar() or 0

    now = datetime.utcnow()

    today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    today_q = select(func.count(ActivityLog.id)).where(
        ActivityLog.created_at >= today_start
    )
    today_result = await db.execute(today_q)
    logs_today = today_result.scalar() or 0

    one_week_ago = now - timedelta(days=7)
    week_q = select(func.count(ActivityLog.id)).where(
        ActivityLog.created_at >= one_week_ago
    )
    week_result = await db.execute(week_q)
    logs_this_week = week_result.scalar() or 0

    users_q = select(func.count(func.distinct(ActivityLog.user_id)))
    users_result = await db.execute(users_q)
    unique_users = users_result.scalar() or 0

    # Top 5 actions by count
    top_actions_q = (
        select(ActivityLog.action, func.count(ActivityLog.id).label("count"))
        .group_by(ActivityLog.action)
        .order_by(func.count(ActivityLog.id).desc())
        .limit(5)
    )
    top_actions_result = await db.execute(top_actions_q)
    top_actions = [
        {"action": row[0], "count": row[1]}
        for row in top_actions_result.all()
    ]

    return ActivityLogStats(
        total_logs=total_logs,
        logs_today=logs_today,
        logs_this_week=logs_this_week,
        unique_users=unique_users,
        top_actions=top_actions,
    )


@router.get("", response_model=PaginatedActivityLogs)
async def list_activity_logs(
    user_id: int | None = None,
    action: str | None = None,
    entity_type: str | None = None,
    sort: str = "newest",
    page: int = Query(default=1, ge=1),
    per_page: int = Query(default=20, ge=1, le=100),
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """List activity logs with pagination, filtering, and sorting."""
    base_q = select(ActivityLog)

    if user_id is not None:
        base_q = base_q.where(ActivityLog.user_id == user_id)
    if action:
        base_q = base_q.where(ActivityLog.action == action)
    if entity_type:
        base_q = base_q.where(ActivityLog.entity_type == entity_type)

    # Count total matching rows
    count_q = select(func.count()).select_from(base_q.subquery())
    total = (await db.execute(count_q)).scalar() or 0

    # Sort
    if sort == "oldest":
        base_q = base_q.order_by(ActivityLog.created_at.asc())
    else:
        base_q = base_q.order_by(ActivityLog.created_at.desc())

    # Paginate
    offset = (page - 1) * per_page
    base_q = base_q.offset(offset).limit(per_page)

    result = await db.execute(base_q)
    logs = result.scalars().all()

    # Batch-fetch user emails
    user_ids = {log.user_id for log in logs if log.user_id}
    user_map: dict[int, str] = {}
    if user_ids:
        users_result = await db.execute(
            select(User.id, User.email).where(User.id.in_(user_ids))
        )
        user_map = {uid: email for uid, email in users_result.all()}

    items = [
        ActivityLogSummary(
            id=log.id,
            user_id=log.user_id,
            user_email=user_map.get(log.user_id),
            project_id=log.project_id,
            action=log.action,
            entity_type=log.entity_type,
            entity_id=log.entity_id,
            entity_title=log.entity_title,
            metadata_json=log.metadata_json,
            created_at=log.created_at.isoformat() + "Z" if log.created_at else "",
        )
        for log in logs
    ]

    total_pages = max(1, (total + per_page - 1) // per_page)
    return PaginatedActivityLogs(
        items=items,
        total=total,
        page=page,
        per_page=per_page,
        total_pages=total_pages,
    )


@router.get("/{log_id}")
async def get_activity_log(
    log_id: int,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Get a single activity log entry by ID."""
    result = await db.execute(
        select(ActivityLog).where(ActivityLog.id == log_id)
    )
    log = result.scalar_one_or_none()
    if not log:
        raise HTTPException(status_code=404, detail="Activity log not found")

    # Fetch user email
    user_email = None
    if log.user_id:
        user_result = await db.execute(
            select(User.email).where(User.id == log.user_id)
        )
        row = user_result.first()
        if row:
            user_email = row[0]

    return ActivityLogSummary(
        id=log.id,
        user_id=log.user_id,
        user_email=user_email,
        project_id=log.project_id,
        action=log.action,
        entity_type=log.entity_type,
        entity_id=log.entity_id,
        entity_title=log.entity_title,
        metadata_json=log.metadata_json,
        created_at=log.created_at.isoformat() + "Z" if log.created_at else "",
    )
