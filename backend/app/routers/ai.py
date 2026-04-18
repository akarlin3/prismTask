import logging
from datetime import date, datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.middleware.rate_limit import RateLimiter, daily_ai_rate_limiter
from app.models import Habit, User
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
from app.services.firestore_tasks import (
    TaskDTO,
    fetch_incomplete_tasks,
    fetch_recently_completed_tasks,
    fetch_tasks_by_ids,
    filter_due_on,
    filter_for_time_block,
    filter_overdue_before,
    filter_planned_on,
    filter_recurring,
)

logger = logging.getLogger(__name__)

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


def _require_firebase_uid(user: User) -> str:
    """Return the user's Firebase UID or raise 401.

    AI endpoints read tasks from Firestore under ``users/{uid}/tasks``. If the
    JWT-linked User row has no ``firebase_uid``, the identity chain is broken —
    reject rather than silently query a nonexistent collection.
    """
    uid = getattr(user, "firebase_uid", None)
    if not uid:
        logger.warning(
            "AI endpoint rejected: user id=%s has no firebase_uid", user.id
        )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User is not linked to a Firebase account",
        )
    return uid


async def _get_incomplete_tasks(
    user: User, task_ids: list[str] | None = None
) -> list[TaskDTO]:
    uid = _require_firebase_uid(user)
    if task_ids:
        tasks = await fetch_tasks_by_ids(uid, task_ids)
        # Match the old Postgres filter: only incomplete tasks. fetch_tasks_by_ids
        # returns any doc that exists; drop completed ones here.
        return [t for t in tasks if t.completed_at is None]
    return await fetch_incomplete_tasks(uid)


def _log_empty_short_circuit(user: User, endpoint: str) -> None:
    logger.info(
        "AI short-circuit: no_incomplete_tasks user_id=%s endpoint=%s",
        user.id,
        endpoint,
    )


@router.post("/eisenhower", response_model=EisenhowerResponse)
async def categorize_eisenhower(
    data: EisenhowerRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
):
    ai_rate_limiter.check(request)
    tier = current_user.effective_tier
    daily_ai_rate_limiter.check(current_user.id, tier)

    tasks = await _get_incomplete_tasks(current_user, data.task_ids)
    if not tasks:
        _log_empty_short_circuit(current_user, "eisenhower")
        return EisenhowerResponse(
            categorizations=[],
            summary=EisenhowerSummary(),
        )

    task_dicts = [t.to_ai_dict() for t in tasks]

    try:
        from app.services.ai_productivity import categorize_eisenhower as ai_categorize

        categorizations = ai_categorize(task_dicts, date.today(), tier=tier)
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    valid_task_ids = {t.task_id for t in tasks}
    valid_quadrants = {"Q1", "Q2", "Q3", "Q4"}
    cleaned = []
    for cat in categorizations:
        tid = cat.get("task_id")
        if tid is not None:
            tid = str(tid)
        quadrant = cat.get("quadrant", "")
        reason = cat.get("reason", "")
        if tid in valid_task_ids and quadrant in valid_quadrants:
            cleaned.append({"task_id": tid, "quadrant": quadrant, "reason": reason})

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
):
    ai_rate_limiter.check(request)
    tier = current_user.effective_tier
    daily_ai_rate_limiter.check(current_user.id, tier)

    tasks = await _get_incomplete_tasks(current_user)
    if not tasks:
        _log_empty_short_circuit(current_user, "pomodoro")
        return PomodoroResponse(
            sessions=[],
            total_sessions=0,
            total_work_minutes=0,
            total_break_minutes=0,
            skipped_tasks=[],
        )

    task_dicts = [t.to_ai_dict() for t in tasks]

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


@router.post("/daily-briefing", response_model=DailyBriefingResponse)
async def daily_briefing(
    data: DailyBriefingRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    briefing_rate_limiter.check(request)
    tier = current_user.effective_tier
    daily_ai_rate_limiter.check(current_user.id, tier)

    try:
        target_date = date.fromisoformat(data.date) if data.date else date.today()
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid date format; expected YYYY-MM-DD")

    uid = _require_firebase_uid(current_user)

    # One Firestore read, then partition in Python. Keeps query count bounded
    # even for users with many incomplete tasks.
    incomplete_tasks = await fetch_incomplete_tasks(uid)
    overdue_tasks = [t.to_briefing_dict() for t in filter_overdue_before(incomplete_tasks, target_date)]
    today_tasks = [t.to_briefing_dict() for t in filter_due_on(incomplete_tasks, target_date)]
    planned_tasks = [t.to_briefing_dict() for t in filter_planned_on(incomplete_tasks, target_date)]

    # Habits still live in Postgres — unchanged by this migration.
    habits_query = select(Habit).where(
        Habit.user_id == current_user.id,
        Habit.is_active.is_(True),
    )
    habits_result = await db.execute(habits_query)
    habits = [{"name": h.name, "frequency": h.frequency.value} for h in habits_result.scalars().all()]

    # Recently completed (last 24h) comes from Firestore too now.
    yesterday = datetime.now(timezone.utc) - timedelta(hours=24)
    completed_dtos = await fetch_recently_completed_tasks(uid, yesterday)
    completed_tasks = [{"task_id": t.task_id, "title": t.title} for t in completed_dtos]

    all_tasks = overdue_tasks + today_tasks + planned_tasks
    if not all_tasks and not habits:
        _log_empty_short_circuit(current_user, "daily-briefing")
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
):
    weekly_plan_rate_limiter.check(request)
    tier = current_user.effective_tier
    daily_ai_rate_limiter.check(current_user.id, tier)

    if data.week_start:
        try:
            week_start = date.fromisoformat(data.week_start)
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid week_start format; expected YYYY-MM-DD")
    else:
        # Default to next Monday
        today = date.today()
        days_until_monday = (7 - today.weekday()) % 7
        if days_until_monday == 0:
            days_until_monday = 7
        week_start = today + timedelta(days=days_until_monday)

    week_end = week_start + timedelta(days=6)

    incomplete = await _get_incomplete_tasks(current_user)
    all_tasks = [t.to_briefing_dict() for t in incomplete]
    recurring_tasks = [t.to_briefing_dict() for t in filter_recurring(incomplete)]

    if not all_tasks:
        _log_empty_short_circuit(current_user, "weekly-plan")
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
    tier = current_user.effective_tier
    daily_ai_rate_limiter.check(current_user.id, tier)

    try:
        target_date = date.fromisoformat(data.date) if data.date else date.today()
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid date format; expected YYYY-MM-DD")

    incomplete = await _get_incomplete_tasks(current_user)
    tasks = [t.to_briefing_dict() for t in filter_for_time_block(incomplete, target_date)]

    if not tasks:
        _log_empty_short_circuit(current_user, "time-block")
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

    # Fetch real Google Calendar events for the target date so the AI
    # planner can schedule around them. The call is best-effort: if the
    # user hasn't connected Calendar, their settings disable sync, or
    # the backend can't reach Google right now, fall back to an empty
    # list and let the planner schedule as if the day were clear.
    import json as _json

    from app.models import CalendarSyncSettings as _CalSettings
    from app.services import calendar_service as _calendar_service

    calendar_events: list[dict] = []
    try:
        cal_settings_result = await db.execute(
            select(_CalSettings).where(_CalSettings.user_id == current_user.id)
        )
        cal_settings = cal_settings_result.scalar_one_or_none()
        if cal_settings is not None and cal_settings.enabled and cal_settings.show_events:
            try:
                display_ids = _json.loads(cal_settings.display_calendar_ids_json or "[]")
            except ValueError:
                display_ids = []
            calendar_ids = display_ids or [cal_settings.target_calendar_id]
            day_start_dt = datetime.combine(
                target_date, datetime.min.time(), tzinfo=timezone.utc
            )
            raw_events = await _calendar_service.list_events_in_window(
                db,
                current_user.id,
                calendar_ids,
                time_min=day_start_dt,
                time_max=day_start_dt + timedelta(days=1),
                limit=50,
            )
            calendar_events = [
                {
                    "title": e["title"],
                    "start_millis": e["start_millis"],
                    "end_millis": e["end_millis"],
                    "all_day": e["all_day"],
                }
                for e in raw_events
            ]
    except Exception:  # noqa: BLE001
        calendar_events = []

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
    tier = current_user.effective_tier
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
    tier = current_user.effective_tier
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
