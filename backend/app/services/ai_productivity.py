import json
import logging
import os
from datetime import date

from app.config import settings

try:
    import anthropic
except ImportError:
    anthropic = None  # type: ignore

logger = logging.getLogger(__name__)

MODEL_HAIKU = "claude-haiku-4-5-20251001"
MODEL_SONNET = "claude-sonnet-4-20250514"

# AI features that use Sonnet for higher-quality output. Everything else runs
# on Haiku. These are the premium AI features baked into the single Pro tier.
SONNET_FEATURES = {"weekly_planner", "monthly_review"}


def get_model(feature: str | None = None) -> str:
    """Return the appropriate Claude model ID for the given AI feature.

    Weekly planner and monthly review use Sonnet; everything else uses
    Haiku. ``feature`` is a short lowercase identifier (e.g. ``"eisenhower"``,
    ``"weekly_planner"``). Passing ``None`` defaults to Haiku.
    """
    if feature in SONNET_FEATURES:
        return MODEL_SONNET
    return MODEL_HAIKU

# Sonnet pricing (~$3/M input, ~$15/M output) vs Haiku (~$0.25/M, ~$1.25/M).
# Weekly planner / monthly review are Sonnet-backed; all other AI features
# use Haiku to keep the margin healthy at the single $7.99/mo (or $4.99/mo
# annual) Pro tier.


def _get_client():
    api_key = os.environ.get("ANTHROPIC_API_KEY") or settings.ANTHROPIC_API_KEY
    if not api_key:
        raise RuntimeError("ANTHROPIC_API_KEY environment variable is not set")
    if anthropic is None:
        raise RuntimeError("anthropic package is not installed")
    return anthropic.Anthropic(api_key=api_key)


def _parse_ai_json(content: str) -> dict | list:
    """Strip markdown fences and parse JSON from AI response."""
    content = content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else content[3:]
    if content.endswith("```"):
        content = content[:-3]
    content = content.strip()
    return json.loads(content)


def categorize_eisenhower(tasks: list[dict], today: date, tier: str = "FREE") -> list[dict]:
    """Call Claude to categorize tasks into Eisenhower quadrants."""
    client = _get_client()
    model = get_model("eisenhower")
    tasks_json = json.dumps(tasks, default=str, indent=2)
    prompt = f"""You are a productivity assistant. Categorize each task into an Eisenhower Matrix quadrant.

Quadrants:
- Q1 (Urgent + Important): Deadlines within 48 hours, high-priority blockers, critical issues
- Q2 (Not Urgent + Important): Long-term goals, planning, skill building, health, relationships
- Q3 (Urgent + Not Important): Most emails, some meetings, minor deadlines, others' priorities
- Q4 (Not Urgent + Not Important): Time-wasters, excessive social media, busywork

Consider: due date proximity, priority level, project context, task description.
A task with no due date but high priority is likely Q2.
A task due today with low priority is likely Q3.

Tasks:
{tasks_json}

Today's date: {today.isoformat()}

Respond ONLY with valid JSON:
[{{"task_id": 1, "quadrant": "Q1", "reason": "Due tomorrow, high priority"}}]"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=2048, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, list):
                raise ValueError("Expected a JSON array")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse Eisenhower response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Eisenhower AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error}")


def plan_pomodoro(tasks: list[dict], available_minutes: int, session_length: int, break_length: int, long_break_length: int, focus_preference: str, today: date, tier: str = "FREE") -> dict:
    """Call Claude to generate a Pomodoro focus session plan."""
    client = _get_client()
    tasks_json = json.dumps(tasks, default=str, indent=2)
    prompt = f"""You are a productivity coach planning focus sessions (Pomodoro technique).

Available time: {available_minutes} minutes
Session length: {session_length} min work + {break_length} min break
Long break: {long_break_length} min after every 4 sessions
Focus preference: {focus_preference}

Focus preference meanings:
- "deep_work": prioritize complex, high-concentration tasks; batch similar work
- "quick_wins": start with short, easy tasks to build momentum
- "balanced": mix of quick wins and deep work
- "deadline_driven": prioritize by due date urgency

Today's date: {today.isoformat()}

User's tasks:
{tasks_json}

Create an ordered plan of which tasks to work on in each session.

Respond ONLY with valid JSON:
{{"sessions": [{{"session_number": 1, "tasks": [{{"task_id": 1, "title": "Write report draft", "allocated_minutes": 25}}], "rationale": "Starting with the most urgent deadline"}}], "total_sessions": 4, "total_work_minutes": 100, "total_break_minutes": 20, "skipped_tasks": [{{"task_id": 7, "reason": "No estimated duration and low priority"}}]}}"""

    model = get_model("pomodoro")
    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=4096, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse Pomodoro response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Pomodoro AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error}")


def generate_daily_briefing(today: date, overdue_tasks: list[dict], today_tasks: list[dict], planned_tasks: list[dict], habits: list[dict], completed_tasks: list[dict], tier: str = "FREE") -> dict:
    """Call Claude to generate a daily morning briefing."""
    client = _get_client()
    day_of_week = today.strftime("%A")
    overdue_json = json.dumps(overdue_tasks, default=str, indent=2)
    today_json = json.dumps(today_tasks, default=str, indent=2)
    planned_json = json.dumps(planned_tasks, default=str, indent=2)
    habits_json = json.dumps(habits, default=str, indent=2)
    completed_json = json.dumps(completed_tasks, default=str, indent=2)
    prompt = f"""You are a productivity coach delivering a concise morning briefing.

Today: {today.isoformat()} ({day_of_week})

User's data:
- Overdue tasks: {overdue_json}
- Today's tasks (due today): {today_json}
- Planned for today: {planned_json}
- Today's habits: {habits_json}
- Completed yesterday: {completed_json}

Generate a morning briefing with:
1. A one-line motivational greeting based on their workload (light/moderate/heavy day)
2. "Top 3 Priorities" with brief reasoning
3. "Heads Up" any overdue items or potential scheduling conflicts
4. "Suggested Task Order" all today's tasks ranked in recommended execution order
5. "Habit Reminders" which habits to complete today

Respond ONLY with valid JSON:
{{"greeting": "Good morning!", "top_priorities": [{{"task_id": 1, "title": "...", "reason": "Due today"}}], "heads_up": ["You have 2 overdue tasks"], "suggested_order": [{{"task_id": 1, "title": "...", "suggested_time": "9:00 AM", "reason": "Hardest task first"}}], "habit_reminders": ["Exercise"], "day_type": "moderate"}}"""

    model = get_model("daily_briefing")
    max_tokens = 4096
    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=max_tokens, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse daily briefing response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Daily briefing AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error}")


def generate_weekly_plan(week_start: date, week_end: date, work_days: list[str], focus_hours_per_day: int, prefer_front_loading: bool, tasks: list[dict], recurring_tasks: list[dict], tier: str = "FREE") -> dict:
    """Call Claude to generate a weekly task plan."""
    client = _get_client()
    tasks_json = json.dumps(tasks, default=str, indent=2)
    recurring_json = json.dumps(recurring_tasks, default=str, indent=2)
    prompt = f"""You are a productivity coach creating a weekly plan.

Week: {week_start.isoformat()} to {week_end.isoformat()}
Work days: {', '.join(work_days)}
Max focus hours per day: {focus_hours_per_day}
Front-loading preference: {prefer_front_loading}

User's tasks:
{tasks_json}

Recurring tasks generating this week:
{recurring_json}

Create a day-by-day plan distributing tasks across the week. Consider:
- Hard deadlines, Eisenhower quadrants, estimated durations
- Energy management, project batching, buffer time

Respond ONLY with valid JSON:
{{"plan": {{"Monday": {{"date": "{week_start.isoformat()}", "tasks": [{{"task_id": 1, "title": "...", "suggested_time": "9:00 AM", "duration_minutes": 60, "reason": "Due Tuesday"}}], "total_hours": 5.5, "calendar_events": [], "habits": ["Exercise"]}}}}, "unscheduled": [{{"task_id": 12, "title": "...", "reason": "No deadline"}}], "week_summary": "Moderate week.", "tips": ["Consider delegating Q3 tasks"]}}"""

    model = get_model("weekly_planner")
    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=8192, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse weekly plan response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Weekly plan AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error}")


def generate_time_blocks(target_date: date, day_start: str, day_end: str, block_size_minutes: int, include_breaks: bool, break_frequency_minutes: int, break_duration_minutes: int, tasks: list[dict], calendar_events: list[dict], tier: str = "FREE") -> dict:
    """Call Claude to generate a time-blocked schedule for a day."""
    client = _get_client()
    day_of_week = target_date.strftime("%A")
    tasks_json = json.dumps(tasks, default=str, indent=2)
    events_json = json.dumps(calendar_events, default=str, indent=2)
    break_instructions = f"- Insert breaks: every {break_frequency_minutes} min of work, take a {break_duration_minutes} min break" if include_breaks else "- No automatic breaks requested"
    prompt = f"""You are a time management coach creating a time-blocked schedule.

Date: {target_date.isoformat()} ({day_of_week})
Available: {day_start} to {day_end}
Block size: {block_size_minutes} min minimum

Tasks to schedule:
{tasks_json}

Fixed calendar events (cannot be moved):
{events_json}

Rules:
- Calendar events are fixed; schedule tasks around them
- High-energy tasks in morning, low-energy in afternoon
- Respect estimated durations; default {block_size_minutes} min
{break_instructions}
- Leave 15 min buffer between context-switching blocks
- Leave at least 30 min unscheduled for unexpected work

Respond ONLY with valid JSON:
{{"schedule": [{{"start": "09:00", "end": "09:30", "type": "task", "task_id": 1, "title": "Write report", "reason": "Deep work while fresh"}}], "unscheduled_tasks": [{{"task_id": 7, "title": "...", "reason": "Not enough time today"}}], "stats": {{"total_work_minutes": 300, "total_break_minutes": 60, "total_free_minutes": 60, "tasks_scheduled": 8, "tasks_deferred": 2}}}}"""

    model = get_model("time_blocking")
    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=8192, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse time block response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Time block AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error}")


def analyze_habit_correlations(daily_data: list[dict], tier: str = "FREE") -> dict:
    """Call Claude to analyze correlations between habit completion and task productivity."""
    client = _get_client()
    daily_json = json.dumps(daily_data, default=str, indent=2)
    prompt = f"""Analyze the correlation between habit completion and task productivity.

Data (last 90 days):
{daily_json}

For each habit, calculate: task completion rate on days done vs not done, correlation direction, and interpretation.

Respond ONLY with valid JSON:
{{"correlations": [{{"habit": "Exercise", "done_productivity": 82, "not_done_productivity": 65, "correlation": "positive", "interpretation": "You complete 26% more tasks on days you exercise"}}], "top_insight": "Exercise has the strongest positive impact", "recommendation": "Try to exercise before starting work"}}"""

    model = get_model("habit_correlation")
    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=4096, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse habit correlation response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Habit correlation AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error}")


# ---------------------------------------------------------------------------
# v1.4.0 V6 -- AI weekly review
# ---------------------------------------------------------------------------

def _format_task_bullets(tasks: list[dict]) -> str:
    """Render a task list as terse bullets for inclusion in the weekly-review prompt."""
    if not tasks:
        return "(none)"
    lines = []
    for t in tasks:
        parts = [f"- {t.get('title', '').strip() or '(untitled)'}"]
        meta: list[str] = []
        if t.get("priority") is not None:
            meta.append(f"p{t['priority']}")
        if t.get("eisenhower_quadrant"):
            meta.append(str(t["eisenhower_quadrant"]))
        if t.get("life_category"):
            meta.append(str(t["life_category"]))
        if t.get("due_date"):
            meta.append(f"due {t['due_date']}")
        if t.get("completed_at"):
            meta.append(f"done {t['completed_at']}")
        if meta:
            parts.append(f"  [{', '.join(meta)}]")
        lines.append(" ".join(parts))
    return "\n".join(lines)


def generate_weekly_review(
    week_start: str,
    week_end: str,
    completed_tasks: list[dict],
    slipped_tasks: list[dict],
    open_tasks: list[dict],
    habit_summary: dict | None = None,
    pomodoro_summary: dict | None = None,
    notes: str | None = None,
    tier: str = "FREE",
) -> dict:
    """Generate a forgiveness-first weekly review narrative via Claude.

    Inputs are four structured lists plus optional opaque summaries:
      * completed_tasks — what shipped this week (from client body)
      * slipped_tasks   — due/planned but not completed (from client body)
      * open_tasks      — what's currently on the user's plate (Firestore,
                          ranked by priority DESC, due_date ASC (nulls last),
                          sort_order ASC, capped to 20 upstream)
      * habit_summary / pomodoro_summary — opaque dicts forwarded verbatim
      * notes — free-form text the user wrote about their week

    Uses Sonnet via the ``monthly_review`` feature flag for higher-quality
    narrative output — do not change without coordinating pricing.
    """
    client = _get_client()

    completed_block = _format_task_bullets(completed_tasks)
    slipped_block = _format_task_bullets(slipped_tasks)
    open_block = _format_task_bullets(open_tasks)
    habit_block = json.dumps(habit_summary, default=str) if habit_summary else "(not provided)"
    pomodoro_block = json.dumps(pomodoro_summary, default=str) if pomodoro_summary else "(not provided)"
    notes_block = notes.strip() if notes and notes.strip() else "(none)"

    prompt = f"""You are a compassionate productivity coach writing a weekly review for someone with ADHD. Use a forgiveness-first, supportive tone. Ground every observation in the specifics below — don't invent tasks or metrics.

Week under review: {week_start} to {week_end}

This week the user completed {len(completed_tasks)} task(s):
{completed_block}

They had {len(slipped_tasks)} task(s) that slipped (due or planned this week but not completed):
{slipped_block}

Currently open on their plate going forward: {len(open_tasks)} task(s) (top 20 by priority and due date):
{open_block}

Habit summary (opaque client-provided aggregate):
{habit_block}

Pomodoro summary (opaque client-provided aggregate):
{pomodoro_block}

User's notes about the week:
{notes_block}

Write a review with:
- wins: 2-3 specific accomplishments, citing completed tasks by name where possible.
- slips: 2-3 honest, non-judgmental observations on what didn't happen, citing slipped task titles where they make the point land.
- patterns: 2-3 trends you notice across completed/slipped/open (e.g. "Q2 tasks consistently get deferred", "self-care category dominated completions").
- next_week_focus: 2-3 concrete suggestions for next week, anchored in what's currently open.
- narrative: 2-3 sentence prose summary capturing the overall arc and emotional tone of the week.

Respond ONLY with valid JSON in this exact shape:
{{"wins": ["..."], "slips": ["..."], "patterns": ["..."], "next_week_focus": ["..."], "narrative": "..."}}"""

    model = get_model("monthly_review")
    max_tokens = 2048
    last_error_wr = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=max_tokens, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error_wr = e
            logger.error(f"Failed to parse weekly review response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Weekly review AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error_wr}")


# ---------------------------------------------------------------------------
# v1.4.0 V9 -- conversation -> tasks extraction
# ---------------------------------------------------------------------------

def extract_tasks_from_text(text: str, source: str | None = None, tier: str = "FREE") -> list[dict]:
    """Extract structured task candidates from pasted conversation text."""
    if not text or not text.strip():
        return []
    if len(text) > 10_000:
        text = text[:10_000]

    client = _get_client()
    source_label = f" (source: {source})" if source else ""
    prompt = f"""You are an assistant that extracts action items from conversation text{source_label}.

Text:
---
{text}
---

For each action item, return: title (imperative, Title case, under 12 words), suggested_due_date (ISO or null), suggested_priority (0-4), suggested_project (one-word or null), confidence (0-1).

Only extract clear action items. Ignore general discussion.

Respond ONLY with valid JSON:
[{{"title": "Send the design mocks to Alice", "suggested_due_date": null, "suggested_priority": 2, "suggested_project": null, "confidence": 0.9}}]

Return an empty array if no action items are found."""

    model = get_model("task_extraction")
    last_error_ex = None
    for attempt in range(2):
        try:
            message = client.messages.create(model=model, max_tokens=2048, messages=[{"role": "user", "content": prompt}])
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, list):
                raise ValueError("Expected a JSON array")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error_ex = e
            logger.error(f"Failed to parse task extraction response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Task extraction AI error: {type(e).__name__}: {e}")
            raise
    raise ValueError(f"Failed to parse AI response: {last_error_ex}")
