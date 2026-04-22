from datetime import date
from typing import Any, Optional

from pydantic import BaseModel, Field, model_validator


# --- Eisenhower ---


class EisenhowerRequest(BaseModel):
    task_ids: Optional[list[str]] = None


class EisenhowerCategorization(BaseModel):
    task_id: str
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


class EisenhowerClassifyTextRequest(BaseModel):
    """Single-task text-based Eisenhower classification.

    Accepts raw task fields so the client can classify a freshly-created
    task before the local row has been synced to the backend. Mirrors the
    client's `EisenhowerClassifier.classify(task)` call shape.
    """

    title: str = Field(min_length=1, max_length=500)
    description: Optional[str] = Field(default=None, max_length=4000)
    due_date: Optional[str] = None  # ISO date (YYYY-MM-DD); null means no due date
    priority: int = Field(default=0, ge=0, le=4)


class EisenhowerClassifyTextResponse(BaseModel):
    quadrant: str  # "Q1".."Q4"
    reason: str


# --- Pomodoro ---


class PomodoroRequest(BaseModel):
    available_minutes: int = Field(default=120, ge=15, le=480)
    session_length: int = Field(default=25, ge=5, le=60)
    break_length: int = Field(default=5, ge=1, le=30)
    long_break_length: int = Field(default=15, ge=5, le=60)
    focus_preference: str = Field(default="balanced")


class SessionTask(BaseModel):
    task_id: str
    title: str
    allocated_minutes: int


class PomodoroSession(BaseModel):
    session_number: int
    tasks: list[SessionTask]
    rationale: str


class SkippedTask(BaseModel):
    task_id: str
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
    task_id: str
    title: str
    reason: str


class SuggestedTask(BaseModel):
    task_id: str
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
    task_id: str
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
    task_id: str
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
    task_id: Optional[str] = None
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


# --- Weekly Review (v2 hybrid schema) ---
#
# Schema v2. Breaking change from v1 (which sent aggregate counts only): the
# client now sends per-task summaries for completed and slipped items, and
# the backend enriches the prompt with a live Firestore "open tasks" list.
# Old clients sending the v1 shape will get 422 until their prompts land.


class WeeklyTaskSummary(BaseModel):
    task_id: str
    title: str
    completed_at: Optional[str] = None  # ISO datetime; None for slipped tasks
    priority: int = Field(ge=0, le=4)
    eisenhower_quadrant: Optional[str] = None
    life_category: Optional[str] = None
    project_id: Optional[str] = None


class WeeklyReviewRequest(BaseModel):
    week_start: date
    week_end: date
    completed_tasks: list[WeeklyTaskSummary] = Field(default_factory=list)
    slipped_tasks: list[WeeklyTaskSummary] = Field(default_factory=list)
    # Opaque pass-through. The backend forwards these to the prompt verbatim
    # so the client controls the shape (streak counts, session totals, etc.).
    habit_summary: Optional[dict[str, Any]] = None
    pomodoro_summary: Optional[dict[str, Any]] = None
    notes: Optional[str] = Field(default=None, max_length=2000)

    @model_validator(mode="after")
    def _check_week_span(self):
        if self.week_end < self.week_start:
            raise ValueError("week_end must be on or after week_start")
        if (self.week_end - self.week_start).days > 14:
            raise ValueError("week span must be 14 days or fewer")
        return self


class WeeklyReviewResponse(BaseModel):
    week_start: date
    week_end: date
    wins: list[str]
    slips: list[str]
    patterns: list[str]
    next_week_focus: list[str]
    narrative: str


# --- Task Extraction (v1.4.0 V9) ---


class ExtractFromTextRequest(BaseModel):
    text: str = Field(min_length=1, max_length=10_000)
    source: Optional[str] = None


class ExtractedTaskCandidate(BaseModel):
    title: str
    suggested_due_date: Optional[str] = None
    suggested_priority: int = 0
    suggested_project: Optional[str] = None
    confidence: float = Field(ge=0.0, le=1.0)


class ExtractFromTextResponse(BaseModel):
    tasks: list[ExtractedTaskCandidate]
