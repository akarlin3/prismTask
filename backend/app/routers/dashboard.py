from datetime import date, timedelta

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import Task, TaskStatus, User
from app.schemas.dashboard import DashboardSummary
from app.schemas.task import TaskResponse

router = APIRouter(tags=["dashboard"])


def _task_to_response(task: Task) -> TaskResponse:
    return TaskResponse(
        id=task.id,
        project_id=task.project_id,
        user_id=task.user_id,
        parent_id=task.parent_id,
        title=task.title,
        description=task.description,
        status=task.status.value if hasattr(task.status, "value") else task.status,
        priority=task.priority,
        due_date=task.due_date,
        completed_at=task.completed_at,
        sort_order=task.sort_order,
        depth=task.depth,
        created_at=task.created_at,
        updated_at=task.updated_at,
        subtasks=[],
    )


@router.get("/tasks/today", response_model=list[TaskResponse])
async def tasks_today(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    today = date.today()
    result = await db.execute(
        select(Task).where(
            Task.user_id == current_user.id,
            Task.due_date == today,
            Task.status != TaskStatus.DONE,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    return [_task_to_response(t) for t in result.scalars().all()]


@router.get("/tasks/overdue", response_model=list[TaskResponse])
async def tasks_overdue(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    today = date.today()
    result = await db.execute(
        select(Task).where(
            Task.user_id == current_user.id,
            Task.due_date < today,
            Task.status != TaskStatus.DONE,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    return [_task_to_response(t) for t in result.scalars().all()]


@router.get("/tasks/upcoming", response_model=list[TaskResponse])
async def tasks_upcoming(
    days: int = Query(default=7, ge=1, le=90),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    today = date.today()
    end_date = today + timedelta(days=days)
    result = await db.execute(
        select(Task).where(
            Task.user_id == current_user.id,
            Task.due_date > today,
            Task.due_date <= end_date,
            Task.status != TaskStatus.DONE,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    return [_task_to_response(t) for t in result.scalars().all()]


@router.get("/dashboard/summary", response_model=DashboardSummary)
async def dashboard_summary(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    today = date.today()
    upcoming_end = today + timedelta(days=7)

    base = select(func.count()).select_from(Task).where(Task.user_id == current_user.id)

    total = (await db.execute(base)).scalar() or 0
    completed = (await db.execute(base.where(Task.status == TaskStatus.DONE))).scalar() or 0
    overdue = (
        await db.execute(
            base.where(
                Task.due_date < today,
                Task.status != TaskStatus.DONE,
                Task.status != TaskStatus.CANCELLED,
            )
        )
    ).scalar() or 0
    today_count = (
        await db.execute(
            base.where(
                Task.due_date == today,
                Task.status != TaskStatus.DONE,
                Task.status != TaskStatus.CANCELLED,
            )
        )
    ).scalar() or 0
    upcoming = (
        await db.execute(
            base.where(
                Task.due_date > today,
                Task.due_date <= upcoming_end,
                Task.status != TaskStatus.DONE,
                Task.status != TaskStatus.CANCELLED,
            )
        )
    ).scalar() or 0

    rate = (completed / total * 100) if total > 0 else 0.0

    return DashboardSummary(
        total_tasks=total,
        completed_tasks=completed,
        overdue_tasks=overdue,
        today_tasks=today_count,
        upcoming_tasks=upcoming,
        completion_rate=round(rate, 1),
    )
