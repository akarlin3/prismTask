from pydantic import BaseModel

from app.schemas.task import TaskResponse


class DashboardSummary(BaseModel):
    total_tasks: int
    completed_tasks: int
    overdue_tasks: int
    today_tasks: int
    upcoming_tasks: int
    completion_rate: float


class TaskListResponse(BaseModel):
    tasks: list[TaskResponse]
    count: int
