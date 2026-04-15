import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import (
    Goal,
    GoalStatus,
    Habit,
    HabitCompletion,
    HabitFrequency,
    Project,
    ProjectStatus,
    Tag,
    Task,
    TaskStatus,
    TaskTemplate,
    User,
)
from app.schemas.sync import (
    SyncChange,
    SyncOperation,
    SyncPullResponse,
    SyncPushRequest,
    SyncPushResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/sync", tags=["sync"])

ENTITY_MAP = {
    "goal": Goal,
    "project": Project,
    "task": Task,
    "tag": Tag,
    "habit": Habit,
    "habit_completion": HabitCompletion,
    "template": TaskTemplate,
}

STATUS_ENUM_MAP = {
    "goal": GoalStatus,
    "project": ProjectStatus,
    "task": TaskStatus,
    "habit": HabitFrequency,
}

# Per-entity allowlists of fields a client may write via /sync. Anything
# outside these lists is stripped before the ORM instance is constructed
# or updated — this protects columns like `user_id`, `id`, `is_admin`,
# `tier`, and `created_at` from client-controlled assignment.
#
# Relationship FKs that reference other user-owned entities (e.g.
# `project_id` on a task) are additionally validated for ownership in
# ``_validate_foreign_keys`` below.
WRITABLE_FIELDS: dict[str, frozenset[str]] = {
    "goal": frozenset({
        "title", "description", "status", "target_date", "color", "sort_order",
    }),
    "project": frozenset({
        "goal_id", "title", "description", "status", "due_date", "sort_order",
    }),
    "task": frozenset({
        "project_id", "parent_id", "title", "description", "notes", "status",
        "priority", "due_date", "due_time", "planned_date", "completed_at",
        "urgency_score", "recurrence_json", "eisenhower_quadrant",
        "eisenhower_updated_at", "estimated_duration", "actual_duration",
        "sort_order", "depth",
    }),
    "tag": frozenset({"name", "color"}),
    "habit": frozenset({
        "name", "description", "icon", "color", "category", "frequency",
        "target_count", "active_days_json", "is_active",
    }),
    "habit_completion": frozenset({"habit_id", "date", "count"}),
    "template": frozenset({
        "name", "description", "icon", "category",
        "template_title", "template_description", "template_priority",
        "template_project_id", "template_tags_json",
        "template_recurrence_json", "template_duration", "template_subtasks_json",
    }),
}

# Foreign keys that reference user-scoped entities. Before assigning one of
# these keys, the server must confirm the referenced row belongs to the
# authenticated user.
USER_SCOPED_FKS: dict[str, dict[str, type]] = {
    "project": {"goal_id": Goal},
    "task": {"project_id": Project, "parent_id": Task},
    "habit_completion": {"habit_id": Habit},
    "template": {"template_project_id": Project},
}


def _filter_writable(entity_type: str, data: dict) -> dict:
    """Strip any key not in the per-entity allowlist."""
    allowed = WRITABLE_FIELDS.get(entity_type, frozenset())
    filtered: dict = {}
    for key, value in data.items():
        if key in allowed:
            filtered[key] = value
        else:
            logger.info(
                "sync: dropping disallowed field %s on %s", key, entity_type
            )
    return filtered


async def _validate_foreign_keys(
    entity_type: str, data: dict, user: User, db: AsyncSession
) -> str | None:
    """Ensure any user-scoped FK in ``data`` points to a row owned by ``user``.

    Returns an error string on failure, None on success.
    """
    fks = USER_SCOPED_FKS.get(entity_type)
    if not fks:
        return None
    for column, model in fks.items():
        value = data.get(column)
        if value is None:
            continue
        query = select(model.id).where(model.id == value)
        if hasattr(model, "user_id"):
            query = query.where(model.user_id == user.id)
        result = await db.execute(query)
        if result.scalar_one_or_none() is None:
            return f"Invalid {column} on {entity_type}: {value} not found"
    return None


async def _process_operation(
    op: SyncOperation, user: User, db: AsyncSession
) -> str | None:
    model = ENTITY_MAP.get(op.entity_type)
    if not model:
        return f"Unknown entity type: {op.entity_type}"

    if op.operation == "create":
        if not op.data:
            return "Create operation requires data"
        data = _filter_writable(op.entity_type, dict(op.data))
        fk_error = await _validate_foreign_keys(op.entity_type, data, user, db)
        if fk_error:
            return fk_error
        # Force user_id server-side — never trust the client for ownership.
        if hasattr(model, "user_id"):
            data["user_id"] = user.id
        # Default status for new tasks (PostgreSQL enum values are lowercase)
        if op.entity_type == "task" and "status" not in data:
            data["status"] = "todo"
        # Convert status enums to their lowercase values
        if "status" in data and op.entity_type in STATUS_ENUM_MAP:
            data["status"] = STATUS_ENUM_MAP[op.entity_type](data["status"]).value
        if "frequency" in data and op.entity_type == "habit":
            data["frequency"] = HabitFrequency(data["frequency"]).value
        entity = model(**data)
        db.add(entity)

    elif op.operation == "update":
        if not op.entity_id or not op.data:
            return "Update requires entity_id and data"
        query = select(model).where(model.id == op.entity_id)
        if hasattr(model, "user_id"):
            query = query.where(model.user_id == user.id)
        result = await db.execute(query)
        entity = result.scalar_one_or_none()
        if not entity:
            return f"{op.entity_type} {op.entity_id} not found"
        data = _filter_writable(op.entity_type, dict(op.data))
        fk_error = await _validate_foreign_keys(op.entity_type, data, user, db)
        if fk_error:
            return fk_error
        for key, value in data.items():
            if key == "status" and op.entity_type in STATUS_ENUM_MAP:
                value = STATUS_ENUM_MAP[op.entity_type](value).value
            if key == "frequency" and op.entity_type == "habit":
                value = HabitFrequency(value).value
            setattr(entity, key, value)

    elif op.operation == "delete":
        if not op.entity_id:
            return "Delete requires entity_id"
        query = select(model).where(model.id == op.entity_id)
        if hasattr(model, "user_id"):
            query = query.where(model.user_id == user.id)
        result = await db.execute(query)
        entity = result.scalar_one_or_none()
        if not entity:
            return f"{op.entity_type} {op.entity_id} not found"
        await db.delete(entity)

    else:
        return f"Unknown operation: {op.operation}"

    return None


@router.post("/push", response_model=SyncPushResponse)
async def sync_push(
    data: SyncPushRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    errors = []
    processed = 0

    for op in data.operations:
        error = await _process_operation(op, current_user, db)
        if error:
            errors.append(error)
        else:
            processed += 1

    await db.flush()

    return SyncPushResponse(
        processed=processed,
        errors=errors,
        server_timestamp=datetime.now(timezone.utc),
    )


@router.get("/pull", response_model=SyncPullResponse)
async def sync_pull(
    since: datetime | None = Query(default=None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    changes: list[SyncChange] = []

    for entity_type, model in ENTITY_MAP.items():
        if entity_type == "habit_completion":
            # HabitCompletion doesn't have user_id directly
            continue

        query = select(model)
        if hasattr(model, "user_id"):
            query = query.where(model.user_id == current_user.id)

        if since and hasattr(model, "updated_at"):
            query = query.where(model.updated_at > since)
        elif since and hasattr(model, "created_at"):
            query = query.where(model.created_at > since)

        result = await db.execute(query)
        for entity in result.scalars().all():
            data = {}
            for col in entity.__table__.columns:
                val = getattr(entity, col.name)
                if hasattr(val, "value"):
                    val = val.value
                if hasattr(val, "isoformat"):
                    val = val.isoformat()
                data[col.name] = val

            timestamp = getattr(entity, "updated_at", None) or getattr(entity, "created_at", None)
            changes.append(
                SyncChange(
                    entity_type=entity_type,
                    operation="upsert",
                    entity_id=entity.id,
                    data=data,
                    timestamp=timestamp or datetime.now(timezone.utc),
                )
            )

    # Also pull habit completions via user's habits
    if since:
        habit_result = await db.execute(
            select(Habit.id).where(Habit.user_id == current_user.id)
        )
        habit_ids = [r[0] for r in habit_result.all()]
        if habit_ids:
            comp_result = await db.execute(
                select(HabitCompletion)
                .where(
                    HabitCompletion.habit_id.in_(habit_ids),
                    HabitCompletion.created_at > since,
                )
            )
            for c in comp_result.scalars().all():
                changes.append(
                    SyncChange(
                        entity_type="habit_completion",
                        operation="upsert",
                        entity_id=c.id,
                        data={
                            "id": c.id,
                            "habit_id": c.habit_id,
                            "date": c.date.isoformat(),
                            "count": c.count,
                        },
                        timestamp=c.created_at,
                    )
                )

    return SyncPullResponse(
        changes=changes,
        server_timestamp=datetime.now(timezone.utc),
    )
