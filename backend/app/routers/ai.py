from datetime import date, datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.middleware.rate_limit import RateLimiter, daily_ai_rate_limiter
from app.models import Habit, Task, TaskStatus, User
from app.schemas.ai import (
    DailyBriefingRequest,
    DailyBriefingResponse,
    EisenhowerRequest,
    EisenhowerResponse,
    EisenhowerSummary,
    ExtractFromTextRequest,
    ExtractFromTextResponse,
    ExtractedTaskCandidate,
    PomodoroRequest,
    PomodoroResponse,
    TimeBlockRequest,
    TimeBlockResponse,
    WeeklyPlanRequest,
    WeeklyPlanResponse,
    WeeklyReviewRequest,
    WeeklyReviewResponse,
)

router = APIRouter(prefix="/ai", tags=["ai"])

# Rate limiter: max 1 call per 5 minutes (300 seconds) per IP
ai_rate_limiter = RateLimiter(max_requests=1, window_seconds=300)

# Rate limiters for new AI endpoints
briefing_rate_limiter = RateLimiter(max_requests=1, window_seconds=3600)  # 1 per hour
weekly_plan_rate_limiter = RateLimiter(max_requests=1, window_seconds=1800)  # 1 per 30 min
time_block_rate_limiter = RateLimiter(max_requests=1, window_seconds=900)  # 1 per 15 min
# v1.4.0 V6: weekly review — 1 per hour is plenty, the client caches history.
weekly_review_rate_limiter = RateLimiter(max_requests=1, window_seconds=3600)
# v1.4.0 V9: paste-to-extract — 10 per minute to cover rapid iteration
# (user fixing titles and re-extracting).
extract_rate_limiter = RateLimiter(max_requests=10, window_seconds=60)


async def _fetch_incomplete_tasks(
    user: User, db: AsyncSession, task_ids: list[int] | None = None
) -> list[Task]:
    query = select(Task).where(
        Task.user_id == user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
    )
    if task_ids:
        query = query.where(Task.id.in_(task_ids))
    result = await db.execute(query.order_by(Task.sort_order, Task.created_at))
    return list(result.scalars().all())


def _task_to_ai_dict(task: Task) -> dict:
    return {
        "task_id": task.id,
        "title": task.title,
        "description": task.description,
        "due_date": task.due_date.isoformat() if task.due_date else None,
        "priority": task.priority,
        "project_id": task.project_id,
        "eisenhower_quadrant": task.eisenhower_quadrant,
    }


@router.post("/eisenhower", response_model=EisenhowerResponse)
async def categorize_eisenhower(
    data: EisenhowerRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    ai_rate_limiter.check(request)
    tier = current_user.tier or "FREE"
    daily_ai_rate_limiter.check(current_user.id, tier)

    tasks = await _fetch_incomplete_tasks(current_user, db, data.task_ids)
    if not tasks:
        return EisenhowerResponse(
            categorizations=[],
            summary=EisenhowerSummary(),
        )

    task_dicts = [_task_to_ai_dict(t) for t in tasks]

    try:
        from app.services.ai_productivity import categorize_eisenhower as ai_categorize

        categorizations = ai_categorize(task_dicts, date.today(), tier=tier)
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    # Build task lookup for updating
    task_map = {t.id: t for t in tasks}
    now = datetime.now(timezone.utc)

    valid_quadrants = {"Q1", "Q2", "Q3", "Q4"}
    cleaned = []
    for cat in categorizations:
        tid = cat.get("task_id")
        quadrant = cat.get("quadrant", "")
        reason = cat.get("reason", "")
        if tid in task_map and quadrant in valid_quadrants:
            task_map[tid].eisenhower_quadrant = quadrant
            task_map[tid].eisenhower_updated_at = now
            cleaned.append({"task_id": tid, "quadrant": quadrant, "reason": reason})

    await db.flush()

    summary = EisenhowerSummary()
    for cat in cleaned:
        current = getattr(summary, cat["quadrant"])
        setattr(summary, cat["quadrant"], current + 1)

    return EisenhowerResponse(
        categorizations=[
            {"task_id": c["task_id"], "quadrant": c["quadrant"], "reason": c["reason"]}
            for c in cleaned
        ],
        summary=summary,
    )


@router.post("/pomodoro-plan", response_model=PomodoroResponse)
async def plan_pomodoro(
    data: PomodoroRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    ai_rate_limiter.check(request)
    tier = current_user.tier or "FREE"
    daily_ai_rate_limiter.check(current_user.id, tier)

    tasks = await _fetch_incomplete_tasks(current_user, db)
    if not tasks:
        return PomodoroResponse(
            sessions=[],
            total_sessions=0,
            total_work_minutes=0,
            total_break_minutes=0,
            skipped_tasks=[],
        )

    task_dicts = [_task_to_ai_dict(t) for t in tasks]

    try:
        from app.services.ai_productivity import plan_pomodoro as ai_plan

        plan = ai_plan(
            tasks=task_dicts,
            available_minutes=data.available_minutes,
            session_length=data.session_length,
            break_length=data.break_length,
            long_break_length=data.long_break_length,
            focus_preference=data.focus_preference,
            today=date.today(),
            tier=tier,
        )
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    return PomodoroResponse(**plan)


def _task_to_briefing_dict(task: Task) -> dict:
    return {
        "task_id": task.id,
        "title": task.title,
        "description": task.description,
        "due_date": task.due_date.isoformat() if task.due_date else None,
        "due_time": task.due_time.isoformat() if task.due_time else None,
        "planned_date": task.planned_date.isoformat() if task.planned_date else None,
        "priority": task.priority,
        "project_id": task.project_id,
        "eisenhower_quadrant": task.eisenhower_quadrant,
        "urgency_score": task.urgency_score,
        "sort_order": task.sort_order,
    }


@router.post("/daily-briefing", response_model=DailyBriefingResponse)
async def daily_briefing(
    data: DailyBriefingRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    briefing_rate_limiter.check(request)
    tier = current_user.tier or "FREE"
    daily_ai_rate_limiter.check(current_user.id, tier)

    target_date = date.fromisoformat(data.date) if data.date else date.today()

    # Fetch overdue tasks (past due, not completed)
    overdue_query = select(Task).where(
        Task.user_id == current_user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
        Task.due_date < target_date,
    )
    overdue_result = await db.execute(overdue_query)
    overdue_tasks = [_task_to_briefing_dict(t) for t in overdue_result.scalars().all()]

    # Fetch tasks due today
    today_query = select(Task).where(
        Task.user_id == current_user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
        Task.due_date == target_date,
    )
    today_result = await db.execute(today_query)
    today_tasks = [_task_to_briefing_dict(t) for t in today_result.scalars().all()]

    # Fetch tasks planned for today
    planned_query = select(Task).where(
        Task.user_id == current_user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
        Task.planned_date == target_date,
    )
    planned_result = await db.execute(planned_query)
    planned_tasks = [_task_to_briefing_dict(t) for t in planned_result.scalars().all()]

    # Fetch active habits
    habits_query = select(Habit).where(
        Habit.user_id == current_user.id,
        Habit.is_active == True,  # noqa: E712
    )
    habits_result = await db.execute(habits_query)
    habits = [{"name": h.name, "frequency": h.frequency.value} for h in habits_result.scalars().all()]

    # Fetch recently completed tasks (last 24 hours)
    yesterday = datetime.now(timezone.utc) - timedelta(hours=24)
    completed_query = select(Task).where(
        Task.user_id == current_user.id,
        Task.status == TaskStatus.DONE,
        Task.completed_at >= yesterday,
    )
    completed_result = await db.execute(completed_query)
    completed_tasks = [{"task_id": t.id, "title": t.title} for t in completed_result.scalars().all()]

    # Return empty briefing if no data
    all_tasks = overdue_tasks + today_tasks + planned_tasks
    if not all_tasks and not habits:
        return DailyBriefingResponse(
            greeting="Good morning! You have a clear day ahead.",
            top_priorities=[],
            heads_up=[],
            suggested_order=[],
            habit_reminders=[],
            day_type="light",
        )

    try:
        from app.services.ai_productivity import generate_daily_briefing as ai_briefing

        result = ai_briefing(
            today=target_date,
            overdue_tasks=overdue_tasks,
            today_tasks=today_tasks,
            planned_tasks=planned_tasks,
            habits=habits,
            completed_tasks=completed_tasks,
            tier=tier,
        )
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    return DailyBriefingResponse(**result)


@router.post("/weekly-plan", response_model=WeeklyPlanResponse)
async def weekly_plan(
    data: WeeklyPlanRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    weekly_plan_rate_limiter.check(request)
    tier = current_user.tier or "FREE"
    daily_ai_rate_limiter.check(current_user.id, tier)

    if data.week_start:
        week_start = date.fromisoformat(data.week_start)
    else:
        # Default to next Monday
        today = date.today()
        days_until_monday = (7 - today.weekday()) % 7
        if days_until_monday == 0:
            days_until_monday = 7
        week_start = today + timedelta(days=days_until_monday)

    week_end = week_start + timedelta(days=6)

    # Fetch all incomplete tasks
    tasks_query = select(Task).where(
        Task.user_id == current_user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
    ).order_by(Task.sort_order, Task.created_at)
    tasks_result = await db.execute(tasks_query)
    all_tasks = [_task_to_briefing_dict(t) for t in tasks_result.scalars().all()]

    # Fetch recurring tasks
    recurring_query = select(Task).where(
        Task.user_id == current_user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
        Task.recurrence_json.isnot(None),
    )
    recurring_result = await db.execute(recurring_query)
    recurring_tasks = [_task_to_briefing_dict(t) for t in recurring_result.scalars().all()]

    if not all_tasks:
        return WeeklyPlanResponse(
            plan={},
            unscheduled=[],
            week_summary="No tasks to plan for this week.",
            tips=[],
        )

    try:
        from app.services.ai_productivity import generate_weekly_plan as ai_plan

        result = ai_plan(
            week_start=week_start,
            week_end=week_end,
            work_days=data.preferences.work_days,
            focus_hours_per_day=data.preferences.focus_hours_per_day,
            prefer_front_loading=data.preferences.prefer_front_loading,
            tasks=all_tasks,
            recurring_tasks=recurring_tasks,
            tier=tier,
        )
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    return WeeklyPlanResponse(**result)


@router.post("/time-block", response_model=TimeBlockResponse)
async def time_block(
    data: TimeBlockRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    time_block_rate_limiter.check(request)
    tier = current_user.tier or "FREE"
    daily_ai_rate_limiter.check(current_user.id, tier)

    target_date = date.fromisoformat(data.date) if data.date else date.today()

    # Fetch tasks for the date: due today, planned today, or overdue
    tasks_query = select(Task).where(
        Task.user_id == current_user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
    ).where(
        (Task.due_date == target_date)
        | (Task.planned_date == target_date)
        | (Task.due_date < target_date)
    ).order_by(Task.sort_order, Task.created_at)
    tasks_result = await db.execute(tasks_query)
    tasks = [_task_to_briefing_dict(t) for t in tasks_result.scalars().all()]

    if not tasks:
        from app.schemas.ai import TimeBlockStats

        return TimeBlockResponse(
            schedule=[],
            unscheduled_tasks=[],
            stats=TimeBlockStats(
                total_work_minutes=0,
                total_break_minutes=0,
                total_free_minutes=0,
                tasks_scheduled=0,
                tasks_deferred=0,
            ),
        )

    # Calendar events are not stored in the backend DB currently,
    # so pass an empty list (can be extended when calendar sync is added)
    calendar_events: list[dict] = []

    try:
        from app.services.ai_productivity import generate_time_blocks as ai_time_block

        result = ai_time_block(
            target_date=target_date,
            day_start=data.day_start,
            day_end=data.day_end,
            block_size_minutes=data.block_size_minutes,
            include_breaks=data.include_breaks,
            break_frequency_minutes=data.break_frequency_minutes,
            break_duration_minutes=data.break_duration_minutes,
            tasks=tasks,
            calendar_events=calendar_events,
            tier=tier,
        )
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    return TimeBlockResponse(**result)


# ---------------------------------------------------------------------------
# v1.4.0 V6 — AI weekly review
# ---------------------------------------------------------------------------


@router.post("/weekly-review", response_model=WeeklyReviewResponse)
async def weekly_review(
    data: WeeklyReviewRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
):
    """
    Generate an ADHD-friendly weekly review narrative from anonymized
    aggregate stats. No individual task titles are sent — the Android
    client computes counts + category ratios + burnout score locally
    and forwards only those numbers.

    The response has three short bullet lists (wins, slips, suggestions)
    plus a tone marker. The Android WeeklyReviewScreen replaces its
    rule-based narrative with this output when the user is on Premium
    and the request succeeds; the rule-based fallback stays for Free
    users and network failures.
    """
    weekly_review_rate_limiter.check(request)
    tier = current_user.tier or "FREE"
    daily_ai_rate_limiter.check(current_user.id, tier)

    try:
        from app.services.ai_productivity import generate_weekly_review as ai_review
        result = ai_review(
            week_start=data.week_start,
            week_end=data.week_end,
            completed=data.completed,
            slipped=data.slipped,
            rescheduled=data.rescheduled,
            category_counts=data.category_counts,
            burnout_score=data.burnout_score,
            medication_adherence=data.medication_adherence,
            tier=tier,
        )
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    return WeeklyReviewResponse(
        wins=result.get("wins", []),
        slips=result.get("slips", []),
        suggestions=result.get("suggestions", []),
        tone=result.get("tone", "gentle"),
    )


# ---------------------------------------------------------------------------
# v1.4.0 V9 — paste-to-tasks extraction
# ---------------------------------------------------------------------------


@router.post("/tasks/extract-from-text", response_model=ExtractFromTextResponse)
async def extract_from_text(
    data: ExtractFromTextRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
):
    """
    Extract structured task candidates from pasted conversation text via
    Claude Haiku. The Android client (ConversationTaskExtractor) falls
    back to regex-based extraction when this endpoint is unavailable.

    Input is capped at 10,000 chars by the schema. The Android paste
    screen enforces the same cap client-side.
    """
    extract_rate_limiter.check(request)
    tier = current_user.tier or "FREE"
    daily_ai_rate_limiter.check(current_user.id, tier)

    try:
        from app.services.ai_productivity import extract_tasks_from_text as ai_extract
        raw_tasks = ai_extract(data.text, data.source, tier=tier)
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    candidates = []
    for t in raw_tasks:
        candidates.append(
            ExtractedTaskCandidate(
                title=str(t.get("title", "")).strip(),
                suggested_due_date=t.get("suggested_due_date"),
                suggested_priority=int(t.get("suggested_priority") or 0),
                suggested_project=t.get("suggested_project"),
                confidence=float(t.get("confidence") or 0.5),
            )
        )
    # Drop anything with an empty title — defensive.
    candidates = [c for c in candidates if c.title]
    return ExtractFromTextResponse(tasks=candidates)
