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
    sort_order: int
    depth: int
    created_at: datetime
    updated_at: datetime
    subtasks: list["TaskResponse"] = []

    model_config = {"from_attributes": True}
