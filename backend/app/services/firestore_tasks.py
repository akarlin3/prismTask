"""Firestore-backed task queries for the AI productivity endpoints.

Tasks live under ``users/{uid}/tasks`` in Firestore. See
``web/src/api/firestore/tasks.ts`` for the canonical schema. The
Android client writes the same shape.

The google-cloud-firestore client is synchronous, so each call is wrapped
in ``asyncio.to_thread`` to avoid blocking the FastAPI event loop.

Scope note: these helpers currently filter only on ``isCompleted == False``
(matching the original Postgres query which filtered on
``status NOT IN (DONE, CANCELLED)``). Archived tasks are NOT excluded.
If archived tasks polluting AI output becomes an issue, revisit.
"""

from __future__ import annotations

import asyncio
import logging
from datetime import date, datetime, timezone
from functools import lru_cache
from typing import Optional

from firebase_admin import firestore
from google.cloud.firestore_v1.base_query import FieldFilter
from pydantic import BaseModel, Field

from app.services.firebase_storage import _get_firebase_app

logger = logging.getLogger(__name__)

_FETCH_BY_ID_CONCURRENCY = 10


class TaskDTO(BaseModel):
    """Plain DTO carrying the task fields the Haiku prompts consume.

    Field names mirror what ``_task_to_ai_dict`` / ``_task_to_briefing_dict``
    previously produced from the SQLAlchemy Task model, so the Haiku prompts
    do not need to change.
    """

    task_id: str
    title: str
    description: Optional[str] = None
    due_date: Optional[str] = None  # ISO date (YYYY-MM-DD)
    due_time: Optional[str] = None  # ISO time (HH:MM:SS)
    planned_date: Optional[str] = None  # ISO date
    priority: int = 0
    project_id: Optional[str] = None
    eisenhower_quadrant: Optional[str] = None
    urgency_score: float = 0.0
    sort_order: int = 0
    is_recurring: bool = False
    completed_at: Optional[str] = None  # ISO datetime, only for completed fetches
    planned_date_obj: Optional[date] = Field(default=None, exclude=True)
    due_date_obj: Optional[date] = Field(default=None, exclude=True)

    model_config = {"arbitrary_types_allowed": True}

    def to_ai_dict(self) -> dict:
        return {
            "task_id": self.task_id,
            "title": self.title,
            "description": self.description,
            "due_date": self.due_date,
            "priority": self.priority,
            "project_id": self.project_id,
            "eisenhower_quadrant": self.eisenhower_quadrant,
        }

    def to_briefing_dict(self) -> dict:
        return {
            "task_id": self.task_id,
            "title": self.title,
            "description": self.description,
            "due_date": self.due_date,
            "due_time": self.due_time,
            "planned_date": self.planned_date,
            "priority": self.priority,
            "project_id": self.project_id,
            "eisenhower_quadrant": self.eisenhower_quadrant,
            "urgency_score": self.urgency_score,
            "sort_order": self.sort_order,
        }


@lru_cache(maxsize=1)
def _get_firestore_client():
    """Return a cached Firestore client.

    Ensures the Firebase Admin app is initialized first (reusing the
    existing credential loading in ``firebase_storage``), then hands out a
    single Firestore client instance for the life of the process.
    """
    _get_firebase_app()
    return firestore.client()


def _user_tasks_collection(user_id: str):
    return (
        _get_firestore_client()
        .collection("users")
        .document(user_id)
        .collection("tasks")
    )


def _millis_to_iso_date(value) -> Optional[str]:
    if value is None:
        return None
    try:
        millis = int(value)
    except (TypeError, ValueError):
        return None
    dt = datetime.fromtimestamp(millis / 1000.0, tz=timezone.utc)
    return dt.date().isoformat()


def _millis_to_iso_time(value) -> Optional[str]:
    if value is None:
        return None
    try:
        millis = int(value)
    except (TypeError, ValueError):
        return None
    dt = datetime.fromtimestamp(millis / 1000.0, tz=timezone.utc)
    return dt.time().isoformat()


def _millis_to_iso_datetime(value) -> Optional[str]:
    if value is None:
        return None
    try:
        millis = int(value)
    except (TypeError, ValueError):
        return None
    return datetime.fromtimestamp(millis / 1000.0, tz=timezone.utc).isoformat()


def _millis_to_date_obj(value) -> Optional[date]:
    if value is None:
        return None
    try:
        millis = int(value)
    except (TypeError, ValueError):
        return None
    return datetime.fromtimestamp(millis / 1000.0, tz=timezone.utc).date()


def _doc_to_dto(doc) -> TaskDTO:
    data = doc.to_dict() or {}
    project_id = data.get("projectId")
    if project_id is not None:
        project_id = str(project_id)
    due_millis = data.get("dueDate")
    planned_millis = data.get("plannedDate")
    return TaskDTO(
        task_id=str(doc.id),
        title=str(data.get("title") or ""),
        description=data.get("description"),
        due_date=_millis_to_iso_date(due_millis),
        due_time=_millis_to_iso_time(data.get("dueTime")),
        planned_date=_millis_to_iso_date(planned_millis),
        priority=int(data.get("priority") or 0),
        project_id=project_id,
        eisenhower_quadrant=data.get("eisenhowerQuadrant"),
        urgency_score=0.0,  # Not stored in Firestore; default.
        sort_order=int(data.get("sortOrder") or 0),
        is_recurring=bool(data.get("recurrenceRule")),
        completed_at=_millis_to_iso_datetime(data.get("completedAt")),
        due_date_obj=_millis_to_date_obj(due_millis),
        planned_date_obj=_millis_to_date_obj(planned_millis),
    )


async def fetch_incomplete_tasks(user_id: str) -> list[TaskDTO]:
    """Return all tasks under ``users/{user_id}/tasks`` where ``isCompleted == False``.

    Ordering mirrors the old Postgres query: sort_order ASC, then created_at ASC
    as a tiebreaker. Firestore can't enforce both without a composite index, so
    we sort in Python.
    """

    def _sync() -> list[TaskDTO]:
        coll = _user_tasks_collection(user_id)
        query = coll.where(filter=FieldFilter("isCompleted", "==", False))
        dtos = [_doc_to_dto(d) for d in query.stream()]
        dtos.sort(key=lambda t: (t.sort_order, t.task_id))
        return dtos

    return await asyncio.to_thread(_sync)


async def fetch_tasks_by_ids(user_id: str, task_ids: list[str]) -> list[TaskDTO]:
    """Fetch a set of tasks by document ID in parallel.

    Missing documents are skipped (logged at debug level) rather than raising —
    clients sometimes have stale IDs cached. Concurrency is capped to avoid
    hammering Firestore when a user selects a large batch.
    """
    if not task_ids:
        return []

    sem = asyncio.Semaphore(_FETCH_BY_ID_CONCURRENCY)

    async def _fetch_one(tid: str) -> Optional[TaskDTO]:
        async with sem:
            def _sync():
                return _user_tasks_collection(user_id).document(tid).get()

            doc = await asyncio.to_thread(_sync)
            if not doc.exists:
                logger.debug(
                    "fetch_tasks_by_ids: skipping missing doc user=%s id=%s",
                    user_id,
                    tid,
                )
                return None
            return _doc_to_dto(doc)

    results = await asyncio.gather(*[_fetch_one(tid) for tid in task_ids])
    return [r for r in results if r is not None]


async def fetch_recently_completed_tasks(
    user_id: str, since: datetime
) -> list[TaskDTO]:
    """Return completed tasks with ``completedAt >= since`` (used by daily briefing)."""
    since_millis = int(since.timestamp() * 1000)

    def _sync() -> list[TaskDTO]:
        coll = _user_tasks_collection(user_id)
        query = (
            coll.where(filter=FieldFilter("isCompleted", "==", True))
            .where(filter=FieldFilter("completedAt", ">=", since_millis))
        )
        return [_doc_to_dto(d) for d in query.stream()]

    return await asyncio.to_thread(_sync)


# ---------------------------------------------------------------------------
# Convenience filters for the router (keep Firestore query count bounded).
# ---------------------------------------------------------------------------


def filter_due_on(tasks: list[TaskDTO], target: date) -> list[TaskDTO]:
    return [t for t in tasks if t.due_date_obj == target]


def filter_overdue_before(tasks: list[TaskDTO], target: date) -> list[TaskDTO]:
    return [t for t in tasks if t.due_date_obj is not None and t.due_date_obj < target]


def filter_planned_on(tasks: list[TaskDTO], target: date) -> list[TaskDTO]:
    return [t for t in tasks if t.planned_date_obj == target]


def filter_for_time_block(tasks: list[TaskDTO], target: date) -> list[TaskDTO]:
    return [
        t
        for t in tasks
        if t.due_date_obj == target
        or t.planned_date_obj == target
        or (t.due_date_obj is not None and t.due_date_obj < target)
    ]


def filter_recurring(tasks: list[TaskDTO]) -> list[TaskDTO]:
    return [t for t in tasks if t.is_recurring]


__all__ = [
    "TaskDTO",
    "fetch_incomplete_tasks",
    "fetch_tasks_by_ids",
    "fetch_recently_completed_tasks",
    "filter_due_on",
    "filter_overdue_before",
    "filter_planned_on",
    "filter_for_time_block",
    "filter_recurring",
]
