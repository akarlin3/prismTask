from fastapi import APIRouter, Depends, Query
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import Task, User

router = APIRouter(prefix="/search", tags=["search"])


@router.get("")
async def search_tasks(
    q: str = Query(..., min_length=1, description="Search query"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    pattern = f"%{q}%"
    result = await db.execute(
        select(Task)
        .where(
            Task.user_id == current_user.id,
            or_(
                Task.title.ilike(pattern),
                Task.description.ilike(pattern),
            ),
        )
        .order_by(Task.created_at.desc())
    )
    tasks = result.scalars().all()
    results = [
        {
            "id": t.id,
            "project_id": t.project_id,
            "user_id": t.user_id,
            "parent_id": t.parent_id,
            "title": t.title,
            "description": t.description,
            "status": t.status.value if hasattr(t.status, "value") else t.status,
            "priority": t.priority,
            "due_date": str(t.due_date) if t.due_date else None,
            "completed_at": str(t.completed_at) if t.completed_at else None,
            "sort_order": t.sort_order,
            "depth": t.depth,
            "created_at": str(t.created_at),
            "updated_at": str(t.updated_at),
            "subtasks": [],
        }
        for t in tasks
    ]
    return {"results": results, "count": len(results)}
