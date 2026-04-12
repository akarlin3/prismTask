from datetime import date
from typing import Optional

from pydantic import BaseModel


class ParsedTask(BaseModel):
    title: str
    project_suggestion: Optional[str] = None
    due_date: Optional[date] = None
    priority: Optional[int] = None
    parent_task_suggestion: Optional[str] = None
    confidence: float
    suggestions: Optional[list[str]] = None


class ParseRequest(BaseModel):
    text: str


class ParseResponse(ParsedTask):
    needs_confirmation: bool = True
