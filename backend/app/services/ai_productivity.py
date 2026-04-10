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

MODEL = "claude-haiku-4-5-20251001"


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


def categorize_eisenhower(tasks: list[dict], today: date) -> list[dict]:
    """Call Claude Haiku to categorize tasks into Eisenhower quadrants."""
    client = _get_client()
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

Respond ONLY with valid JSON — no markdown, no preamble:
[
  {{"task_id": 1, "quadrant": "Q1", "reason": "Due tomorrow, high priority"}},
  {{"task_id": 2, "quadrant": "Q2", "reason": "No deadline but important for career growth"}}
]"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model=MODEL,
                max_tokens=2048,
                messages=[{"role": "user", "content": prompt}],
            )
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


def plan_pomodoro(
    tasks: list[dict],
    available_minutes: int,
    session_length: int,
    break_length: int,
    long_break_length: int,
    focus_preference: str,
    today: date,
) -> dict:
    """Call Claude Haiku to generate a Pomodoro focus session plan."""
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
Tasks longer than one session can span multiple sessions.
Tasks shorter than one session can be batched together.

Respond ONLY with valid JSON — no markdown, no preamble:
{{
  "sessions": [
    {{
      "session_number": 1,
      "tasks": [
        {{"task_id": 1, "title": "Write report draft", "allocated_minutes": 25}}
      ],
      "rationale": "Starting with the most urgent deadline"
    }}
  ],
  "total_sessions": 4,
  "total_work_minutes": 100,
  "total_break_minutes": 20,
  "skipped_tasks": [
    {{"task_id": 7, "reason": "No estimated duration and low priority"}}
  ]
}}"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model=MODEL,
                max_tokens=4096,
                messages=[{"role": "user", "content": prompt}],
            )
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


def generate_daily_briefing(
    today: date,
    overdue_tasks: list[dict],
    today_tasks: list[dict],
    planned_tasks: list[dict],
    habits: list[dict],
    completed_tasks: list[dict],
) -> dict:
    """Call Claude Haiku to generate a daily morning briefing."""
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
2. "Top 3 Priorities" — the 3 most important tasks to focus on today, with brief reasoning
3. "Heads Up" — any overdue items or potential scheduling conflicts
4. "Suggested Task Order" — all today's tasks ranked in recommended execution order,
   considering: deadlines, energy levels (harder tasks early), dependencies
5. "Habit Reminders" — which habits to complete today

Respond ONLY with valid JSON — no markdown, no preamble:
{{
  "greeting": "Good morning! You have a productive day ahead with 8 tasks.",
  "top_priorities": [
    {{"task_id": 1, "title": "...", "reason": "Due today, high priority"}},
    {{"task_id": 5, "title": "...", "reason": "Blocks other work"}},
    {{"task_id": 3, "title": "...", "reason": "Quick win to build momentum"}}
  ],
  "heads_up": [
    "You have 2 overdue tasks from yesterday",
    "Meeting at 2 PM may conflict with deep work block"
  ],
  "suggested_order": [
    {{"task_id": 1, "title": "...", "suggested_time": "9:00 AM", "reason": "Hardest task first"}},
    {{"task_id": 5, "title": "...", "suggested_time": "10:30 AM", "reason": "After morning focus"}}
  ],
  "habit_reminders": ["Exercise", "Read 20 pages"],
  "day_type": "moderate"
}}"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model=MODEL,
                max_tokens=4096,
                messages=[{"role": "user", "content": prompt}],
            )
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


def generate_weekly_plan(
    week_start: date,
    week_end: date,
    work_days: list[str],
    focus_hours_per_day: int,
    prefer_front_loading: bool,
    tasks: list[dict],
    recurring_tasks: list[dict],
) -> dict:
    """Call Claude Haiku to generate a weekly task plan."""
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
- Hard deadlines (tasks due on specific days MUST be on that day or before)
- Eisenhower quadrants (Q1 tasks first, Q4 tasks can be deferred or dropped)
- Estimated durations (don't exceed focus_hours_per_day)
- Energy management (harder tasks earlier in day AND earlier in week if front-loading)
- Project batching (group tasks from the same project when possible)
- Leave buffer time (don't fill every minute)

Respond ONLY with valid JSON — no markdown, no preamble:
{{
  "plan": {{
    "Monday": {{
      "date": "{week_start.isoformat()}",
      "tasks": [
        {{"task_id": 1, "title": "...", "suggested_time": "9:00 AM",
         "duration_minutes": 60, "reason": "Due Tuesday, needs focus time"}}
      ],
      "total_hours": 5.5,
      "calendar_events": [],
      "habits": ["Exercise", "Read"]
    }}
  }},
  "unscheduled": [
    {{"task_id": 12, "title": "...", "reason": "No deadline, low priority — defer to next week"}}
  ],
  "week_summary": "Moderate week with 23 tasks. Front-loaded: heaviest days are Mon-Wed.",
  "tips": [
    "Consider delegating the 3 Q3 tasks",
    "Block 2 hours Wednesday for the report draft"
  ]
}}"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model=MODEL,
                max_tokens=8192,
                messages=[{"role": "user", "content": prompt}],
            )
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


def generate_time_blocks(
    target_date: date,
    day_start: str,
    day_end: str,
    block_size_minutes: int,
    include_breaks: bool,
    break_frequency_minutes: int,
    break_duration_minutes: int,
    tasks: list[dict],
    calendar_events: list[dict],
) -> dict:
    """Call Claude Haiku to generate a time-blocked schedule for a day."""
    client = _get_client()

    day_of_week = target_date.strftime("%A")
    tasks_json = json.dumps(tasks, default=str, indent=2)
    events_json = json.dumps(calendar_events, default=str, indent=2)

    if include_breaks:
        break_instructions = f"- Insert breaks: every {break_frequency_minutes} min of work, take a {break_duration_minutes} min break"
    else:
        break_instructions = "- No automatic breaks requested"

    prompt = f"""You are a time management coach creating a time-blocked schedule.

Date: {target_date.isoformat()} ({day_of_week})
Available: {day_start} to {day_end}
Block size: {block_size_minutes} min minimum

Tasks to schedule:
{tasks_json}

Fixed calendar events (cannot be moved):
{events_json}

Create a time-blocked schedule. Rules:
- Calendar events are fixed — schedule tasks around them
- Tasks with specific due times should be near those times
- High-energy tasks (complex, creative) in the morning
- Low-energy tasks (email, admin) in the afternoon
- Respect estimated durations; tasks without durations get {block_size_minutes} min default
{break_instructions}
- Leave 15 min buffer between context-switching blocks
- Don't overschedule — leave at least 30 min unscheduled for unexpected work

Respond ONLY with valid JSON — no markdown, no preamble:
{{
  "schedule": [
    {{"start": "09:00", "end": "09:30", "type": "task", "task_id": 1,
     "title": "Write report", "reason": "Deep work while fresh"}},
    {{"start": "09:30", "end": "10:00", "type": "event", "task_id": null,
     "title": "Team standup", "reason": "Fixed calendar event"}},
    {{"start": "10:00", "end": "10:15", "type": "break", "task_id": null,
     "title": "Break", "reason": "Recovery after 90 min work"}}
  ],
  "unscheduled_tasks": [
    {{"task_id": 7, "title": "...", "reason": "Not enough time today — defer to tomorrow"}}
  ],
  "stats": {{
    "total_work_minutes": 300,
    "total_break_minutes": 60,
    "total_free_minutes": 60,
    "tasks_scheduled": 8,
    "tasks_deferred": 2
  }}
}}"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model=MODEL,
                max_tokens=8192,
                messages=[{"role": "user", "content": prompt}],
            )
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
