from datetime import date
from typing import Optional

from pydantic import BaseModel


# --- Productivity Score ---


class ScoreBreakdown(BaseModel):
    task_completion: float
    on_time: float
    habit_completion: float
    estimation_accuracy: float


class DailyScore(BaseModel):
    date: date
    score: float
    breakdown: ScoreBreakdown


class BestWorstDay(BaseModel):
    date: date
    score: float


class ProductivityScoreResponse(BaseModel):
    scores: list[DailyScore]
    average_score: float
    trend: str  # "improving", "declining", "stable"
    best_day: Optional[BestWorstDay] = None
    worst_day: Optional[BestWorstDay] = None


# --- Time Tracking ---


class TimeTrackingEntry(BaseModel):
    group: str
    total_minutes: int
    task_count: int
    avg_minutes_per_task: float
    estimated_total: int
    accuracy_pct: float


class TimeTrackingResponse(BaseModel):
    entries: list[TimeTrackingEntry]
    total_tracked_minutes: int
    total_estimated_minutes: int
    overall_accuracy_pct: float
    most_time_consuming_project: Optional[str] = None
    most_accurate_estimates: Optional[str] = None


# --- Project Progress / Burndown ---


class BurndownEntry(BaseModel):
    date: date
    # `remaining` and `completed_cumulative` are floats since the
    # PrismTask-timeline-class scope (P9 option a) — a task with
    # progress_percent = 60 contributes 0.6 of a unit. `added` stays
    # int because it counts task rows created on a day, not fractional
    # progress.
    remaining: float
    completed_cumulative: float
    added: int


class ProjectProgressResponse(BaseModel):
    project_name: str
    total_tasks: int
    # `completed_tasks` is the project-wide sum of fractional
    # contributions (`progress_percent / 100` per task, with a fallback
    # of 1.0 for binary-DONE rows). Float because partial-progress rows
    # land non-integer values; legacy int-only consumers should round
    # at the call site.
    completed_tasks: float
    burndown: list[BurndownEntry]
    velocity: float
    projected_completion: Optional[date] = None
    is_on_track: bool


# --- Habit Correlations ---


class HabitCorrelation(BaseModel):
    habit: str
    done_productivity: float
    not_done_productivity: float
    correlation: str  # "positive", "negative", "neutral"
    interpretation: str


class HabitCorrelationResponse(BaseModel):
    correlations: list[HabitCorrelation]
    top_insight: str
    recommendation: str


# --- Summary ---


class TodaySummary(BaseModel):
    completed: int
    remaining: int
    score: float


class WeekSummary(BaseModel):
    completed: int
    remaining: int
    score: float
    trend: str


class MonthSummary(BaseModel):
    completed: int
    remaining: int
    score: float


class StreakSummary(BaseModel):
    current_productive_days: int
    longest_productive_days: int


class HabitSummary(BaseModel):
    completion_rate_7d: float
    completion_rate_30d: float


class AnalyticsSummaryResponse(BaseModel):
    today: TodaySummary
    this_week: WeekSummary
    this_month: MonthSummary
    streaks: StreakSummary
    habits: HabitSummary
