from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel


class TemplateBase(BaseModel):
    name: str
    description: Optional[str] = None
    icon: Optional[str] = None
    category: Optional[str] = None
    template_title: Optional[str] = None
    template_description: Optional[str] = None
    template_priority: Optional[int] = None
    template_project_id: Optional[int] = None
    template_tags_json: Optional[str] = None
    template_recurrence_json: Optional[str] = None
    template_duration: Optional[int] = None
    template_subtasks_json: Optional[str] = None


class TemplateCreate(TemplateBase):
    pass


class TemplateUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    icon: Optional[str] = None
    category: Optional[str] = None
    template_title: Optional[str] = None
    template_description: Optional[str] = None
    template_priority: Optional[int] = None
    template_project_id: Optional[int] = None
    template_tags_json: Optional[str] = None
    template_recurrence_json: Optional[str] = None
    template_duration: Optional[int] = None
    template_subtasks_json: Optional[str] = None


class TemplateResponse(TemplateBase):
    id: int
    user_id: int
    is_built_in: bool
    usage_count: int
    last_used_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class TemplateUseRequest(BaseModel):
    due_date: Optional[date] = None
    project_id: Optional[int] = None


class TemplateUseResponse(BaseModel):
    task_id: int
    message: str


class TemplateFromTaskRequest(BaseModel):
    name: str
    icon: Optional[str] = None
    category: Optional[str] = None
    description: Optional[str] = None
