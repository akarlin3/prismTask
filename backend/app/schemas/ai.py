from typing import Optional

from pydantic import BaseModel, Field


# --- Eisenhower ---


class EisenhowerRequest(BaseModel):
    task_ids: Optional[list[int]] = None


class EisenhowerCategorization(BaseModel):
    task_id: int
    quadrant: str
    reason: str


class EisenhowerSummary(BaseModel):
    Q1: int = 0
    Q2: int = 0
    Q3: int = 0
    Q4: int = 0


class EisenhowerResponse(BaseModel):
    categorizations: list[EisenhowerCategorization]
    summary: EisenhowerSummary


# --- Pomodoro ---


class PomodoroRequest(BaseModel):
    available_minutes: int = Field(default=120, ge=15, le=480)
    session_length: int = Field(default=25, ge=5, le=60)
    break_length: int = Field(default=5, ge=1, le=30)
    long_break_length: int = Field(default=15, ge=5, le=60)
    focus_preference: str = Field(default="balanced")


class SessionTask(BaseModel):
    task_id: int
    title: str
    allocated_minutes: int


class PomodoroSession(BaseModel):
    session_number: int
    tasks: list[SessionTask]
    rationale: str


class SkippedTask(BaseModel):
    task_id: int
    reason: str


class PomodoroResponse(BaseModel):
    sessions: list[PomodoroSession]
    total_sessions: int
    total_work_minutes: int
    total_break_minutes: int
    skipped_tasks: list[SkippedTask] = []


# --- Daily Briefing ---


class DailyBriefingRequest(BaseModel):
    date: Optional[str] = None  # ISO date string, defaults to today


class BriefingPriority(BaseModel):
    task_id: int
    title: str
    reason: str


class SuggestedTask(BaseModel):
    task_id: int
    title: str
    suggested_time: str
    reason: str


class DailyBriefingResponse(BaseModel):
    greeting: str
    top_priorities: list[BriefingPriority]
    heads_up: list[str] = []
    suggested_order: list[SuggestedTask]
    habit_reminders: list[str] = []
    day_type: str  # "light", "moderate", "heavy"


# --- Weekly Plan ---


class WeeklyPlanPreferences(BaseModel):
    work_days: list[str] = Field(default=["MO", "TU", "WE", "TH", "FR"])
    focus_hours_per_day: int = Field(default=6, ge=1, le=12)
    prefer_front_loading: bool = True


class WeeklyPlanRequest(BaseModel):
    week_start: Optional[str] = None  # Monday of target week, defaults to next Monday
    preferences: WeeklyPlanPreferences = WeeklyPlanPreferences()


class PlannedTask(BaseModel):
    task_id: int
    title: str
    suggested_time: str
    duration_minutes: int
    reason: str


class DayPlan(BaseModel):
    date: str
    tasks: list[PlannedTask]
    total_hours: float
    calendar_events: list[str] = []
    habits: list[str] = []


class UnscheduledTask(BaseModel):
    task_id: int
    title: str
    reason: str


class WeeklyPlanResponse(BaseModel):
    plan: dict[str, DayPlan]  # day name -> plan
    unscheduled: list[UnscheduledTask] = []
    week_summary: str
    tips: list[str] = []


# --- Time Block ---


class TimeBlockRequest(BaseModel):
    date: Optional[str] = None  # defaults to today
    day_start: str = Field(default="09:00")
    day_end: str = Field(default="18:00")
    block_size_minutes: int = Field(default=30, ge=15, le=120)
    include_breaks: bool = True
    break_frequency_minutes: int = Field(default=90, ge=30, le=180)
    break_duration_minutes: int = Field(default=15, ge=5, le=30)


class ScheduleBlock(BaseModel):
    start: str
    end: str
    type: str  # "task", "event", "break"
    task_id: Optional[int] = None
    title: str
    reason: str


class TimeBlockStats(BaseModel):
    total_work_minutes: int
    total_break_minutes: int
    total_free_minutes: int
    tasks_scheduled: int
    tasks_deferred: int


class TimeBlockResponse(BaseModel):
    schedule: list[ScheduleBlock]
    unscheduled_tasks: list[UnscheduledTask] = []
    stats: TimeBlockStats
