from datetime import datetime
from typing import Optional

from pydantic import BaseModel, EmailStr


# --- Project Members ---


class ProjectMemberResponse(BaseModel):
    id: int
    user_id: int
    display_name: Optional[str] = None
    email: str
    avatar_url: Optional[str] = None
    role: str
    joined_at: datetime

    model_config = {"from_attributes": True}


# --- Project Invites ---


class ProjectInviteCreate(BaseModel):
    invitee_email: EmailStr
    role: str = "editor"


class ProjectInviteResponse(BaseModel):
    id: int
    project_id: int
    project_name: Optional[str] = None
    inviter_name: Optional[str] = None
    invitee_email: str
    role: str
    status: str
    created_at: datetime
    expires_at: datetime

    model_config = {"from_attributes": True}


# --- Activity Logs ---


class ActivityLogResponse(BaseModel):
    id: int
    user_display_name: Optional[str] = None
    action: str
    entity_type: Optional[str] = None
    entity_id: Optional[int] = None
    entity_title: Optional[str] = None
    created_at: datetime

    model_config = {"from_attributes": True}


# --- Task Comments ---


class TaskCommentCreate(BaseModel):
    content: str


class TaskCommentResponse(BaseModel):
    id: int
    task_id: int
    user_id: int
    user_display_name: Optional[str] = None
    user_avatar_url: Optional[str] = None
    content: str
    created_at: datetime

    model_config = {"from_attributes": True}
