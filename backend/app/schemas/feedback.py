from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class BugReportCreate(BaseModel):
    category: str = Field(..., description="Bug category: CRASH, UI_GLITCH, FEATURE_NOT_WORKING, DATA_LOSS, PERFORMANCE, SYNC_ISSUE, WIDGET_ISSUE, FEATURE_REQUEST, OTHER")
    description: str = Field(..., min_length=10, description="Description of the issue")
    severity: str = Field(default="MINOR", description="MINOR, MAJOR, or CRITICAL")
    steps: list[str] = Field(default_factory=list, description="Steps to reproduce")
    screenshot_uris: list[str] = Field(default_factory=list, description="Firebase Storage screenshot URIs")
    device_model: str = ""
    device_manufacturer: str = ""
    android_version: int = 0
    app_version: str = ""
    app_version_code: int = 0
    build_type: str = ""
    user_tier: str = ""
    current_screen: str = ""
    task_count: int = 0
    habit_count: int = 0
    available_ram_mb: int = 0
    free_storage_mb: int = 0
    network_type: str = ""
    battery_percent: int = 0
    is_charging: bool = False
    diagnostic_log: Optional[str] = None
    submitted_via: str = "backend"


class BugReportStatusUpdate(BaseModel):
    status: str = Field(..., description="SUBMITTED, ACKNOWLEDGED, FIXED, WONT_FIX")
    admin_notes: Optional[str] = None


class BugReportResponse(BaseModel):
    id: int
    report_id: str
    user_id: Optional[int] = None
    category: str
    description: str
    severity: str
    steps: str  # JSON string
    screenshot_uris: str  # JSON string
    device_model: str
    device_manufacturer: str
    android_version: int
    app_version: str
    app_version_code: int
    build_type: str
    user_tier: str
    current_screen: str
    task_count: int
    habit_count: int
    available_ram_mb: int
    free_storage_mb: int
    network_type: str
    battery_percent: int
    is_charging: bool
    status: str
    admin_notes: Optional[str] = None
    diagnostic_log: Optional[str] = None
    submitted_via: str
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    model_config = {"from_attributes": True}
