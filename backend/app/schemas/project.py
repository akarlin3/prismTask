from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel


class ProjectCreate(BaseModel):
    title: str
    description: Optional[str] = None
    status: Optional[str] = "active"
    due_date: Optional[date] = None
    sort_order: Optional[int] = 0


class ProjectUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    status: Optional[str] = None
    due_date: Optional[date] = None
    sort_order: Optional[int] = None


class TaskInProject(BaseModel):
    id: int
    title: str
    status: str
    priority: int
    due_date: Optional[date] = None
    sort_order: int
    depth: int
    created_at: datetime

    model_config = {"from_attributes": True}


class ProjectResponse(BaseModel):
    id: int
    goal_id: int
    user_id: int
    title: str
    description: Optional[str] = None
    status: str
    due_date: Optional[date] = None
    sort_order: int
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class ProjectDetailResponse(ProjectResponse):
    tasks: list[TaskInProject] = []
