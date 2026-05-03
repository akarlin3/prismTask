from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel


class TaskCreate(BaseModel):
    title: str
    description: Optional[str] = None
    status: Optional[str] = "todo"
    priority: Optional[int] = 3  # medium
    due_date: Optional[date] = None
    sort_order: Optional[int] = 0


class TaskUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    status: Optional[str] = None
    priority: Optional[int] = None
    due_date: Optional[date] = None
    sort_order: Optional[int] = None
    eisenhower_quadrant: Optional[str] = None
    # PrismTask-timeline-class scope, PR-4 (audit P9 option a). Fractional
    # progress in 0..100; null restores binary semantics (status as
    # source of truth for burndown).
    progress_percent: Optional[int] = None


class SubtaskCreate(BaseModel):
    title: str
    description: Optional[str] = None
    status: Optional[str] = "todo"
    priority: Optional[int] = 3
    due_date: Optional[date] = None
    sort_order: Optional[int] = 0


class TaskResponse(BaseModel):
    id: int
    project_id: int
    user_id: int
    parent_id: Optional[int] = None
    title: str
    description: Optional[str] = None
    status: str
    priority: int
    due_date: Optional[date] = None
    completed_at: Optional[datetime] = None
    eisenhower_quadrant: Optional[str] = None
    eisenhower_updated_at: Optional[datetime] = None
    sort_order: int
    depth: int
    # PrismTask-timeline-class scope, PR-4. Fractional progress 0..100;
    # null preserves legacy binary (status-as-source-of-truth) semantics.
    progress_percent: Optional[int] = None
    created_at: datetime
    updated_at: datetime
    subtasks: list["TaskResponse"] = []

    model_config = {"from_attributes": True}
