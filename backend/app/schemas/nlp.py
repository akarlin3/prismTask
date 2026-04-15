from datetime import date
from typing import Optional

from pydantic import BaseModel, Field


class ParsedTask(BaseModel):
    title: str
    project_suggestion: Optional[str] = None
    due_date: Optional[date] = None
    priority: Optional[int] = None
    parent_task_suggestion: Optional[str] = None
    confidence: float
    suggestions: Optional[list[str]] = None


class ParseRequest(BaseModel):
    # A task title is at most a few hundred characters; cap generously at 2k
    # to prevent the unauthenticated endpoint being used as a token sink.
    text: str = Field(..., min_length=1, max_length=2_000)


class ParseResponse(ParsedTask):
    needs_confirmation: bool = True
