from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel


class GoalCreate(BaseModel):
    title: str
    description: Optional[str] = None
    status: Optional[str] = "active"
    target_date: Optional[date] = None
    color: Optional[str] = None
    sort_order: Optional[int] = 0


class GoalUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    status: Optional[str] = None
    target_date: Optional[date] = None
    color: Optional[str] = None
    sort_order: Optional[int] = None


class GoalResponse(BaseModel):
    id: int
    user_id: int
    title: str
    description: Optional[str] = None
    status: str
    target_date: Optional[date] = None
    color: Optional[str] = None
    sort_order: int
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class ProjectInGoal(BaseModel):
    id: int
    title: str
    description: Optional[str] = None
    status: str
    due_date: Optional[date] = None
    sort_order: int
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class GoalDetailResponse(GoalResponse):
    projects: list[ProjectInGoal] = []
