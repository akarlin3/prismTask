from datetime import date
from typing import Optional

from pydantic import BaseModel, Field


class ParsedTask(BaseModel):
    title: str
    project_suggestion: Optional[str] = None
    due_date: Optional[date] = None
    # HH:MM (24-hour) time-of-day component when the user specified a time
    # (e.g. "Friday at 3pm" -> due_date=Friday, due_time="15:00"). Kept as a
    # raw string so the client can apply its own timezone; null when the
    # input had no time component.
    due_time: Optional[str] = None
    priority: Optional[int] = None
    parent_task_suggestion: Optional[str] = None
    confidence: float
    suggestions: Optional[list[str]] = None


class ParseRequest(BaseModel):
    # A task title is at most a few hundred characters; cap generously at 2k
    # to prevent the unauthenticated endpoint being used as a token sink.
    text: str = Field(..., min_length=1, max_length=2_000)
    # Optional Start-of-Day hour/minute forwarded from the Android client so
    # "today"/"tomorrow" in the prompt resolves to the user's *logical* day
    # rather than calendar midnight UTC. Older clients omit these fields and
    # the server falls back to ``date.today()``.
    start_of_day_hour: Optional[int] = Field(default=None, ge=0, le=23)
    start_of_day_minute: Optional[int] = Field(default=None, ge=0, le=59)


class ParseResponse(ParsedTask):
    needs_confirmation: bool = True
