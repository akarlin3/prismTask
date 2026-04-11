"""Analytics calculation service for productivity scoring and aggregation."""

from datetime import date, timedelta
from typing import Optional

from sqlalchemy import case, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import (
    Habit,
    HabitCompletion,
    Project,
    Task,
    TaskStatus,
)


async def compute_daily_productivity_scores(
    db: AsyncSession,
    user_id: int,
    start_date: date,
    end_date: date,
) -> list[dict]:
    """Compute a productivity score (0-100) for each day in the range.

    Weights:
      - Task completion rate: 40%
      - On-time rate: 25%
      - Habit completion rate: 20%
      - Estimation accuracy: 15%
    """
    scores: list[dict] = []
    current = start_date

    while current <= end_date:
        next_day = current + timedelta(days=1)

        # --- Task completion rate (40%) ---
        # Tasks that were due on this day
        due_count_result = await db.execute(
            select(func.count()).select_from(Task).where(
                Task.user_id == user_id,
                Task.due_date == current,
                Task.status != TaskStatus.CANCELLED,
            )
        )
        tasks_due = due_count_result.scalar() or 0

        # Tasks due on this day that were completed
        completed_due_result = await db.execute(
            select(func.count()).select_from(Task).where(
                Task.user_id == user_id,
                Task.due_date == current,
                Task.status == TaskStatus.DONE,
            )
        )
        tasks_completed_due = completed_due_result.scalar() or 0

        task_completion_rate = (tasks_completed_due / tasks_due * 100) if tasks_due > 0 else 100.0

        # --- On-time rate (25%) ---
        # Tasks completed on this day
        completed_on_day_result = await db.execute(
            select(func.count()).select_from(Task).where(
                Task.user_id == user_id,
                Task.status == TaskStatus.DONE,
                Task.completed_at >= current,
                Task.completed_at < next_day,
            )
        )
        total_completed_on_day = completed_on_day_result.scalar() or 0

        # Tasks completed on this day that were on time (completed_at <= due_date end of day)
        on_time_result = await db.execute(
            select(func.count()).select_from(Task).where(
                Task.user_id == user_id,
                Task.status == TaskStatus.DONE,
                Task.completed_at >= current,
                Task.completed_at < next_day,
                Task.due_date.isnot(None),
                Task.completed_at <= func.datetime(Task.due_date, "+1 day"),
            )
        )
        on_time_count = on_time_result.scalar() or 0

        on_time_rate = (on_time_count / total_completed_on_day * 100) if total_completed_on_day > 0 else 100.0

        # --- Habit completion rate (20%) ---
        # Active habits for this user
        active_habits_result = await db.execute(
            select(func.count()).select_from(Habit).where(
                Habit.user_id == user_id,
                Habit.is_active.is_(True),
            )
        )
        active_habit_count = active_habits_result.scalar() or 0

        # Habit completions on this day
        habit_completions_result = await db.execute(
            select(func.count()).select_from(HabitCompletion).join(Habit).where(
                Habit.user_id == user_id,
                HabitCompletion.date == current,
            )
        )
        habits_completed = habit_completions_result.scalar() or 0

        habit_rate = (habits_completed / active_habit_count * 100) if active_habit_count > 0 else 100.0

        # --- Estimation accuracy (15%) ---
        # Tasks completed on this day that have both estimated and actual duration
        accuracy_result = await db.execute(
            select(
                func.avg(
                    case(
                        (
                            Task.estimated_duration > 0,
                            100.0 - func.min(
                                func.abs(Task.actual_duration - Task.estimated_duration) * 100.0 / Task.estimated_duration,
                                100.0,
                            ),
                        ),
                        else_=None,
                    )
                )
            ).where(
                Task.user_id == user_id,
                Task.status == TaskStatus.DONE,
                Task.completed_at >= current,
                Task.completed_at < next_day,
                Task.estimated_duration.isnot(None),
                Task.actual_duration.isnot(None),
            )
        )
        avg_accuracy = accuracy_result.scalar()
        estimation_accuracy = float(avg_accuracy) if avg_accuracy is not None else 100.0

        # Clamp sub-scores
        task_completion_rate = max(0.0, min(100.0, task_completion_rate))
        on_time_rate = max(0.0, min(100.0, on_time_rate))
        habit_rate = max(0.0, min(100.0, habit_rate))
        estimation_accuracy = max(0.0, min(100.0, estimation_accuracy))

        score = (
            task_completion_rate * 0.40
            + on_time_rate * 0.25
            + habit_rate * 0.20
            + estimation_accuracy * 0.15
        )
        score = max(0.0, min(100.0, score))

        scores.append({
            "date": current,
            "score": round(score, 1),
            "breakdown": {
                "task_completion": round(task_completion_rate, 1),
                "on_time": round(on_time_rate, 1),
                "habit_completion": round(habit_rate, 1),
                "estimation_accuracy": round(estimation_accuracy, 1),
            },
        })

        current += timedelta(days=1)

    return scores


def determine_trend(scores: list[dict]) -> str:
    """Determine whether scores are improving, declining, or stable."""
    if len(scores) < 2:
        return "stable"

    mid = len(scores) // 2
    first_half = [s["score"] for s in scores[:mid]]
    second_half = [s["score"] for s in scores[mid:]]

    first_avg = sum(first_half) / len(first_half) if first_half else 0
    second_avg = sum(second_half) / len(second_half) if second_half else 0

    diff = second_avg - first_avg
    if diff > 3:
        return "improving"
    elif diff < -3:
        return "declining"
    return "stable"


async def compute_time_tracking_stats(
    db: AsyncSession,
    user_id: int,
    start_date: date,
    end_date: date,
    group_by: str,
) -> dict:
    """Aggregate time tracking stats grouped by project, tag, priority, or day."""
    next_end = end_date + timedelta(days=1)

    if group_by == "project":
        group_col = Project.title
        query = (
            select(
                group_col.label("group_name"),
                func.sum(Task.actual_duration).label("total_minutes"),
                func.count(Task.id).label("task_count"),
                func.sum(Task.estimated_duration).label("estimated_total"),
            )
            .join(Project, Task.project_id == Project.id)
            .where(
                Task.user_id == user_id,
                Task.status == TaskStatus.DONE,
                Task.actual_duration.isnot(None),
                Task.completed_at >= start_date,
                Task.completed_at < next_end,
            )
            .group_by(group_col)
            .order_by(func.sum(Task.actual_duration).desc())
        )
    elif group_by == "priority":
        group_col = Task.priority
        query = (
            select(
                group_col.label("group_name"),
                func.sum(Task.actual_duration).label("total_minutes"),
                func.count(Task.id).label("task_count"),
                func.sum(Task.estimated_duration).label("estimated_total"),
            )
            .where(
                Task.user_id == user_id,
                Task.status == TaskStatus.DONE,
                Task.actual_duration.isnot(None),
                Task.completed_at >= start_date,
                Task.completed_at < next_end,
            )
            .group_by(group_col)
            .order_by(func.sum(Task.actual_duration).desc())
        )
    elif group_by == "day":
        group_col = func.date(Task.completed_at)
        query = (
            select(
                group_col.label("group_name"),
                func.sum(Task.actual_duration).label("total_minutes"),
                func.count(Task.id).label("task_count"),
                func.sum(Task.estimated_duration).label("estimated_total"),
            )
            .where(
                Task.user_id == user_id,
                Task.status == TaskStatus.DONE,
                Task.actual_duration.isnot(None),
                Task.completed_at >= start_date,
                Task.completed_at < next_end,
            )
            .group_by(group_col)
            .order_by(group_col)
        )
    else:
        # Default: group by tag — join through task_tags
        from app.models import Tag, TaskTag

        group_col = func.coalesce(Tag.name, "Untagged")
        query = (
            select(
                group_col.label("group_name"),
                func.sum(Task.actual_duration).label("total_minutes"),
                func.count(func.distinct(Task.id)).label("task_count"),
                func.sum(Task.estimated_duration).label("estimated_total"),
            )
            .outerjoin(TaskTag, Task.id == TaskTag.task_id)
            .outerjoin(Tag, TaskTag.tag_id == Tag.id)
            .where(
                Task.user_id == user_id,
                Task.status == TaskStatus.DONE,
                Task.actual_duration.isnot(None),
                Task.completed_at >= start_date,
                Task.completed_at < next_end,
            )
            .group_by(group_col)
            .order_by(func.sum(Task.actual_duration).desc())
        )

    result = await db.execute(query)
    rows = result.all()

    entries = []
    total_tracked = 0
    total_estimated = 0
    best_accuracy_name: Optional[str] = None
    best_accuracy_val: Optional[float] = None
    most_time_name: Optional[str] = None

    for row in rows:
        group_name = str(row.group_name)
        total_min = int(row.total_minutes or 0)
        task_count = int(row.task_count or 0)
        est_total = int(row.estimated_total or 0)

        avg_per_task = round(total_min / task_count, 1) if task_count > 0 else 0.0
        accuracy = round(
            (1 - abs(total_min - est_total) / est_total) * 100, 1
        ) if est_total > 0 else 0.0
        accuracy = max(0.0, accuracy)

        entries.append({
            "group": group_name,
            "total_minutes": total_min,
            "task_count": task_count,
            "avg_minutes_per_task": avg_per_task,
            "estimated_total": est_total,
            "accuracy_pct": accuracy,
        })

        total_tracked += total_min
        total_estimated += est_total

        if most_time_name is None:
            most_time_name = group_name

        if est_total > 0 and (best_accuracy_val is None or accuracy > best_accuracy_val):
            best_accuracy_val = accuracy
            best_accuracy_name = group_name

    overall_accuracy = (
        round((1 - abs(total_tracked - total_estimated) / total_estimated) * 100, 1)
        if total_estimated > 0 else 0.0
    )
    overall_accuracy = max(0.0, overall_accuracy)

    return {
        "entries": entries,
        "total_tracked_minutes": total_tracked,
        "total_estimated_minutes": total_estimated,
        "overall_accuracy_pct": overall_accuracy,
        "most_time_consuming_project": most_time_name,
        "most_accurate_estimates": best_accuracy_name,
    }


async def compute_project_burndown(
    db: AsyncSession,
    user_id: int,
    project_id: int,
    start_date: date,
    end_date: date,
) -> Optional[dict]:
    """Generate burndown data for a project."""
    # Verify project belongs to user
    proj_result = await db.execute(
        select(Project).where(Project.id == project_id, Project.user_id == user_id)
    )
    project = proj_result.scalar_one_or_none()
    if not project:
        return None

    # Total tasks in project (not cancelled)
    total_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.project_id == project_id,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    total_tasks = total_result.scalar() or 0

    # Completed tasks count
    completed_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.project_id == project_id,
            Task.status == TaskStatus.DONE,
        )
    )
    completed_tasks = completed_result.scalar() or 0

    # Fetch all tasks with their created_at and completed_at
    tasks_result = await db.execute(
        select(Task.created_at, Task.completed_at, Task.status).where(
            Task.project_id == project_id,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    all_tasks = tasks_result.all()

    # Build day-by-day burndown
    burndown = []
    current = start_date
    while current <= end_date:
        # Count tasks that existed by end of this day (created_at <= end of day)
        tasks_existing = sum(
            1 for t in all_tasks
            if t.created_at is not None and t.created_at.date() <= current
        )

        # Count tasks completed by end of this day
        tasks_done = sum(
            1 for t in all_tasks
            if t.completed_at is not None and t.completed_at.date() <= current
        )

        remaining = tasks_existing - tasks_done

        # Tasks added today
        added_today = sum(
            1 for t in all_tasks
            if t.created_at is not None and t.created_at.date() == current
        )

        burndown.append({
            "date": current,
            "remaining": remaining,
            "completed_cumulative": tasks_done,
            "added": added_today,
        })

        current += timedelta(days=1)

    # Calculate velocity (tasks completed per day over the range)
    days_elapsed = (end_date - start_date).days + 1
    velocity = round(completed_tasks / days_elapsed, 1) if days_elapsed > 0 else 0.0

    # Projected completion
    remaining_now = total_tasks - completed_tasks
    projected_completion = None
    is_on_track = True
    if velocity > 0 and remaining_now > 0:
        days_to_complete = remaining_now / velocity
        projected_completion = end_date + timedelta(days=int(days_to_complete))
        # On track if projected completion is before or on project due_date
        if project.due_date:
            is_on_track = projected_completion <= project.due_date
    elif remaining_now == 0:
        is_on_track = True
        projected_completion = end_date

    return {
        "project_name": project.title,
        "total_tasks": total_tasks,
        "completed_tasks": completed_tasks,
        "burndown": burndown,
        "velocity": velocity,
        "projected_completion": projected_completion,
        "is_on_track": is_on_track,
    }


async def compute_summary(
    db: AsyncSession,
    user_id: int,
    today: date,
) -> dict:
    """Compute quick analytics summary for the dashboard header."""
    # --- Today ---
    today_next = today + timedelta(days=1)

    today_completed_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.user_id == user_id,
            Task.status == TaskStatus.DONE,
            Task.completed_at >= today,
            Task.completed_at < today_next,
        )
    )
    today_completed = today_completed_result.scalar() or 0

    today_remaining_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.user_id == user_id,
            Task.due_date == today,
            Task.status != TaskStatus.DONE,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    today_remaining = today_remaining_result.scalar() or 0

    # Today's score
    today_scores = await compute_daily_productivity_scores(db, user_id, today, today)
    today_score = today_scores[0]["score"] if today_scores else 0.0

    # --- This Week ---
    week_start = today - timedelta(days=today.weekday())
    week_end = week_start + timedelta(days=6)
    week_next = week_end + timedelta(days=1)

    week_completed_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.user_id == user_id,
            Task.status == TaskStatus.DONE,
            Task.completed_at >= week_start,
            Task.completed_at < week_next,
        )
    )
    week_completed = week_completed_result.scalar() or 0

    week_remaining_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.user_id == user_id,
            Task.due_date >= week_start,
            Task.due_date <= week_end,
            Task.status != TaskStatus.DONE,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    week_remaining = week_remaining_result.scalar() or 0

    week_scores = await compute_daily_productivity_scores(db, user_id, week_start, min(today, week_end))
    week_avg = (
        round(sum(s["score"] for s in week_scores) / len(week_scores), 1)
        if week_scores else 0.0
    )
    week_trend = determine_trend(week_scores)

    # --- This Month ---
    month_start = today.replace(day=1)
    if today.month == 12:
        month_end = today.replace(year=today.year + 1, month=1, day=1) - timedelta(days=1)
    else:
        month_end = today.replace(month=today.month + 1, day=1) - timedelta(days=1)
    month_next = month_end + timedelta(days=1)

    month_completed_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.user_id == user_id,
            Task.status == TaskStatus.DONE,
            Task.completed_at >= month_start,
            Task.completed_at < month_next,
        )
    )
    month_completed = month_completed_result.scalar() or 0

    month_remaining_result = await db.execute(
        select(func.count()).select_from(Task).where(
            Task.user_id == user_id,
            Task.due_date >= month_start,
            Task.due_date <= month_end,
            Task.status != TaskStatus.DONE,
            Task.status != TaskStatus.CANCELLED,
        )
    )
    month_remaining = month_remaining_result.scalar() or 0

    month_scores = await compute_daily_productivity_scores(db, user_id, month_start, min(today, month_end))
    month_avg = (
        round(sum(s["score"] for s in month_scores) / len(month_scores), 1)
        if month_scores else 0.0
    )

    # --- Streaks ---
    # A "productive day" = score >= 60
    # Walk backwards from today counting consecutive productive days
    current_streak = 0
    longest_streak = 0
    streak_start = today - timedelta(days=90)  # look back max 90 days

    all_scores = await compute_daily_productivity_scores(db, user_id, streak_start, today)
    score_by_date = {s["date"]: s["score"] for s in all_scores}

    # Current streak: count backwards from today
    d = today
    while d >= streak_start and score_by_date.get(d, 0) >= 60:
        current_streak += 1
        d -= timedelta(days=1)

    # Longest streak in range
    streak = 0
    for s in all_scores:
        if s["score"] >= 60:
            streak += 1
            longest_streak = max(longest_streak, streak)
        else:
            streak = 0

    # --- Habits ---
    seven_days_ago = today - timedelta(days=7)
    thirty_days_ago = today - timedelta(days=30)

    active_habits_result = await db.execute(
        select(func.count()).select_from(Habit).where(
            Habit.user_id == user_id,
            Habit.is_active.is_(True),
        )
    )
    active_habits = active_habits_result.scalar() or 0

    completions_7d_result = await db.execute(
        select(func.count()).select_from(HabitCompletion).join(Habit).where(
            Habit.user_id == user_id,
            HabitCompletion.date >= seven_days_ago,
            HabitCompletion.date <= today,
        )
    )
    completions_7d = completions_7d_result.scalar() or 0

    completions_30d_result = await db.execute(
        select(func.count()).select_from(HabitCompletion).join(Habit).where(
            Habit.user_id == user_id,
            HabitCompletion.date >= thirty_days_ago,
            HabitCompletion.date <= today,
        )
    )
    completions_30d = completions_30d_result.scalar() or 0

    habit_rate_7d = round(completions_7d / (active_habits * 7) * 100, 1) if active_habits > 0 else 0.0
    habit_rate_30d = round(completions_30d / (active_habits * 30) * 100, 1) if active_habits > 0 else 0.0

    return {
        "today": {
            "completed": today_completed,
            "remaining": today_remaining,
            "score": today_score,
        },
        "this_week": {
            "completed": week_completed,
            "remaining": week_remaining,
            "score": week_avg,
            "trend": week_trend,
        },
        "this_month": {
            "completed": month_completed,
            "remaining": month_remaining,
            "score": month_avg,
        },
        "streaks": {
            "current_productive_days": current_streak,
            "longest_productive_days": longest_streak,
        },
        "habits": {
            "completion_rate_7d": min(100.0, habit_rate_7d),
            "completion_rate_30d": min(100.0, habit_rate_30d),
        },
    }
