from __future__ import annotations

from datetime import date
from typing import Optional

from pydantic import BaseModel


class SyllabusTask(BaseModel):
    title: str
    due_date: Optional[date] = None
    due_time: Optional[str] = None
    type: str = "other"  # assignment|exam|quiz|project|reading|other
    notes: Optional[str] = None


class SyllabusEvent(BaseModel):
    title: str
    date: Optional[date] = None
    start_time: Optional[str] = None
    end_time: Optional[str] = None
    location: Optional[str] = None


class SyllabusRecurringItem(BaseModel):
    title: str
    day_of_week: str  # monday|tuesday|...|sunday
    start_time: Optional[str] = None
    end_time: Optional[str] = None
    location: Optional[str] = None
    recurrence_end_date: Optional[date] = None


class SyllabusParseResponse(BaseModel):
    course_name: str
    tasks: list[SyllabusTask] = []
    events: list[SyllabusEvent] = []
    recurring_schedule: list[SyllabusRecurringItem] = []


class SyllabusConfirmRequest(BaseModel):
    course_name: str = "My Course"
    tasks: list[SyllabusTask] = []
    events: list[SyllabusEvent] = []
    recurring_schedule: list[SyllabusRecurringItem] = []


class SyllabusConfirmResponse(BaseModel):
    tasks_created: int = 0
    events_created: int = 0
    recurring_created: int = 0
