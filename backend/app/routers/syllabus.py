import io
import json
import logging
import os
from datetime import time

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import Goal, GoalStatus, Project, Task, TaskStatus, User
from app.schemas.syllabus import (
    SyllabusConfirmRequest,
    SyllabusConfirmResponse,
    SyllabusEvent,
    SyllabusParseResponse,
    SyllabusRecurringItem,
    SyllabusTask,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/syllabus", tags=["syllabus"])

MAX_FILE_SIZE = 10 * 1024 * 1024  # 10 MB
MIN_TEXT_LENGTH = 100

SYLLABUS_PARSE_PROMPT = """
You are a syllabus parser for a student productivity app. Extract all actionable items
from the following syllabus text and return ONLY a JSON object with no explanation,
no markdown, and no code fences.

The JSON must have exactly this structure:
{{
  "course_name": "string (course name if found, else 'My Course')",
  "tasks": [
    {{
      "title": "string (assignment/exam name)",
      "due_date": "YYYY-MM-DD or null if not specified",
      "due_time": "HH:MM or null",
      "type": "assignment|exam|quiz|project|reading|other",
      "notes": "string or null (any relevant details)"
    }}
  ],
  "events": [
    {{
      "title": "string (class session, office hours, exam, etc.)",
      "date": "YYYY-MM-DD or null",
      "start_time": "HH:MM or null",
      "end_time": "HH:MM or null",
      "location": "string or null"
    }}
  ],
  "recurring_schedule": [
    {{
      "title": "string (e.g. 'Lecture', 'Lab', 'Office Hours')",
      "day_of_week": "monday|tuesday|wednesday|thursday|friday|saturday|sunday",
      "start_time": "HH:MM or null",
      "end_time": "HH:MM or null",
      "location": "string or null",
      "recurrence_end_date": "YYYY-MM-DD or null (semester end if found)"
    }}
  ]
}}

Rules:
- Only include items that have enough information to be useful
- If a date is ambiguous or missing, set due_date/date to null rather than guessing
- Use 24-hour time for all times
- Do not include the professor's personal contact info as a task
- Exams and quizzes should appear in BOTH tasks (so they get a reminder) and events
  (so they appear on the calendar)
- If the syllabus mentions a recurring class time (e.g. "MWF 10:00-10:50am"), add one
  entry per day of week to recurring_schedule

Syllabus text:
{syllabus_text}
"""


def _parse_ai_json(content: str) -> dict:
    """Strip markdown fences and parse JSON from AI response."""
    content = content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else content[3:]
    if content.endswith("```"):
        content = content[:-3]
    content = content.strip()
    return json.loads(content)


def _parse_time(t: str | None) -> time | None:
    """Parse HH:MM string to a time object."""
    if not t:
        return None
    try:
        parts = t.split(":")
        return time(int(parts[0]), int(parts[1]))
    except (ValueError, IndexError):
        return None


DAY_MAP = {
    "monday": 0,
    "tuesday": 1,
    "wednesday": 2,
    "thursday": 3,
    "friday": 4,
    "saturday": 5,
    "sunday": 6,
}


@router.post("/parse", response_model=SyllabusParseResponse)
async def parse_syllabus(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Pro gate
    if current_user.effective_tier != "PRO":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Syllabus parsing requires Pro",
        )

    # Validate content type
    if file.content_type != "application/pdf":
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Only PDF files are supported",
        )

    # Read and validate size
    contents = await file.read()
    if len(contents) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="PDF must be under 10MB",
        )

    # Extract text
    try:
        import pypdf

        reader = pypdf.PdfReader(io.BytesIO(contents))
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
    except Exception as e:
        logger.error(f"PDF extraction error: {e}")
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Could not read this PDF file",
        )

    if len(text.strip()) < MIN_TEXT_LENGTH:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Could not extract text from PDF. Is it a scanned image?",
        )

    # Call Claude Haiku
    try:
        import anthropic

        api_key = os.environ.get("ANTHROPIC_API_KEY") or settings.ANTHROPIC_API_KEY
        if not api_key:
            raise RuntimeError("ANTHROPIC_API_KEY is not set")

        client = anthropic.AsyncAnthropic(api_key=api_key)
        message = await client.messages.create(
            model="claude-haiku-4-5-20251001",
            max_tokens=4096,
            messages=[
                {
                    "role": "user",
                    "content": SYLLABUS_PARSE_PROMPT.format(
                        syllabus_text=text[:12000]
                    ),
                }
            ],
        )
        raw = message.content[0].text
        parsed = _parse_ai_json(raw)
    except json.JSONDecodeError as e:
        logger.error(f"Failed to parse AI JSON response: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="AI returned an invalid response",
        )
    except RuntimeError as e:
        logger.error(f"AI service config error: {e}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI service temporarily unavailable",
        )
    except Exception as e:
        logger.error(f"AI service error: {type(e).__name__}: {e}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI service temporarily unavailable",
        )

    # Build response
    course_name = parsed.get("course_name", "My Course")

    tasks = []
    for t in parsed.get("tasks", []):
        tasks.append(
            SyllabusTask(
                title=t.get("title", "Untitled"),
                due_date=t.get("due_date"),
                due_time=t.get("due_time"),
                type=t.get("type", "other"),
                notes=t.get("notes"),
            )
        )

    events = []
    for e in parsed.get("events", []):
        events.append(
            SyllabusEvent(
                title=e.get("title", "Untitled"),
                date=e.get("date"),
                start_time=e.get("start_time"),
                end_time=e.get("end_time"),
                location=e.get("location"),
            )
        )

    recurring = []
    for r in parsed.get("recurring_schedule", []):
        recurring.append(
            SyllabusRecurringItem(
                title=r.get("title", "Untitled"),
                day_of_week=r.get("day_of_week", "monday"),
                start_time=r.get("start_time"),
                end_time=r.get("end_time"),
                location=r.get("location"),
                recurrence_end_date=r.get("recurrence_end_date"),
            )
        )

    return SyllabusParseResponse(
        course_name=course_name,
        tasks=tasks,
        events=events,
        recurring_schedule=recurring,
    )


@router.post("/confirm", response_model=SyllabusConfirmResponse)
async def confirm_syllabus(
    body: SyllabusConfirmRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Pro gate
    if current_user.effective_tier != "PRO":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Syllabus import requires Pro",
        )

    tasks_created = 0
    events_created = 0
    recurring_created = 0

    try:
        # Find or create a "Schoolwork" goal for this user
        result = await db.execute(
            select(Goal).where(
                Goal.user_id == current_user.id,
                Goal.title == "Schoolwork",
            )
        )
        goal = result.scalar_one_or_none()
        if goal is None:
            goal = Goal(
                user_id=current_user.id,
                title="Schoolwork",
                status=GoalStatus.ACTIVE,
            )
            db.add(goal)
            await db.flush()
            await db.refresh(goal)

        # Create a project for this course under the Schoolwork goal
        project = Project(
            goal_id=goal.id,
            user_id=current_user.id,
            title=body.course_name,
        )
        db.add(project)
        await db.flush()
        await db.refresh(project)

        # Create tasks
        for t in body.tasks:
            task = Task(
                project_id=project.id,
                user_id=current_user.id,
                title=t.title,
                description=t.notes,
                due_date=t.due_date,
                due_time=_parse_time(t.due_time),
                status=TaskStatus.TODO,
                priority=_priority_for_type(t.type),
                depth=0,
            )
            db.add(task)
            tasks_created += 1

        # Create event tasks (calendar events stored as tasks with notes)
        for e in body.events:
            description_parts = []
            if e.location:
                description_parts.append(f"Location: {e.location}")
            if e.start_time and e.end_time:
                description_parts.append(f"Time: {e.start_time} - {e.end_time}")
            elif e.start_time:
                description_parts.append(f"Time: {e.start_time}")

            task = Task(
                project_id=project.id,
                user_id=current_user.id,
                title=e.title,
                description="\n".join(description_parts) if description_parts else None,
                due_date=e.date,
                due_time=_parse_time(e.start_time),
                status=TaskStatus.TODO,
                priority=2,  # HIGH for calendar events
                depth=0,
            )
            db.add(task)
            events_created += 1

        # Create recurring schedule items as tasks with recurrence JSON
        for r in body.recurring_schedule:
            day_num = DAY_MAP.get(r.day_of_week.lower(), 0)
            day_codes = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]
            day_code = day_codes[day_num]

            recurrence = {
                "frequency": "weekly",
                "interval": 1,
                "daysOfWeek": [day_code],
            }
            if r.recurrence_end_date:
                recurrence["endDate"] = r.recurrence_end_date.isoformat()

            description_parts = []
            if r.location:
                description_parts.append(f"Location: {r.location}")
            if r.start_time and r.end_time:
                description_parts.append(f"Time: {r.start_time} - {r.end_time}")
            elif r.start_time:
                description_parts.append(f"Time: {r.start_time}")

            task = Task(
                project_id=project.id,
                user_id=current_user.id,
                title=r.title,
                description="\n".join(description_parts) if description_parts else None,
                due_time=_parse_time(r.start_time),
                status=TaskStatus.TODO,
                priority=3,  # MEDIUM for recurring
                depth=0,
                recurrence_json=json.dumps(recurrence),
            )
            db.add(task)
            recurring_created += 1

        await db.flush()

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Syllabus confirm error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create syllabus items",
        )

    return SyllabusConfirmResponse(
        tasks_created=tasks_created,
        events_created=events_created,
        recurring_created=recurring_created,
    )


def _priority_for_type(task_type: str) -> int:
    """Map syllabus task types to priority levels."""
    return {
        "exam": 1,      # URGENT
        "quiz": 2,      # HIGH
        "project": 2,   # HIGH
        "assignment": 3, # MEDIUM
        "reading": 4,   # LOW
        "other": 3,     # MEDIUM
    }.get(task_type, 3)
