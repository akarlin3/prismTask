"""Daily Essentials medication slot completion endpoints.

Slots are derived client-side from the user's medication schedule; this router
only persists *materialized* rows — one per ``(user, date, slot_key)`` tuple —
once the user explicitly interacts with a slot. ``slot_key`` is either a
``"HH:mm"`` time or the literal string ``"anytime"`` for interval-based doses.
"""

import json
from datetime import date as date_cls, datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import DailyEssentialSlotCompletion, User
from app.schemas.daily_essentials import (
    SlotBatchRequest,
    SlotBatchResponse,
    SlotCompletionResponse,
    SlotToggleRequest,
)

router = APIRouter(prefix="/daily-essentials", tags=["daily-essentials"])


def _to_response(row: DailyEssentialSlotCompletion) -> SlotCompletionResponse:
    try:
        med_ids = json.loads(row.med_ids_json) if row.med_ids_json else []
    except (ValueError, TypeError):
        med_ids = []
    return SlotCompletionResponse(
        id=row.id,
        date=row.date,
        slot_key=row.slot_key,
        med_ids=med_ids,
        taken_at=row.taken_at,
        created_at=row.created_at,
        updated_at=row.updated_at,
    )


async def _upsert_slot(
    db: AsyncSession,
    user: User,
    on_date: date_cls,
    slot_key: str,
    med_ids: list[str],
    taken: bool,
) -> DailyEssentialSlotCompletion:
    result = await db.execute(
        select(DailyEssentialSlotCompletion).where(
            DailyEssentialSlotCompletion.user_id == user.id,
            DailyEssentialSlotCompletion.date == on_date,
            DailyEssentialSlotCompletion.slot_key == slot_key,
        )
    )
    row = result.scalar_one_or_none()
    now = datetime.now(timezone.utc)
    med_ids_json = json.dumps(med_ids) if med_ids else None
    if row is None:
        row = DailyEssentialSlotCompletion(
            user_id=user.id,
            date=on_date,
            slot_key=slot_key,
            med_ids_json=med_ids_json,
            taken_at=now if taken else None,
            # Set created_at / updated_at explicitly so they're known to the
            # ORM without a post-flush SELECT refresh. Without this, the
            # ``server_default=func.now()`` columns come back expired and
            # accessing them from the sync response serializer blows up
            # with ``MissingGreenlet``.
            created_at=now,
            updated_at=now,
        )
        db.add(row)
        await db.flush()
    else:
        # Overwrite the med_ids snapshot so the slot reflects the current
        # derivation; callers can send an empty list to leave the snapshot
        # untouched (None sentinel is not modeled — client always sends the
        # current set).
        if med_ids:
            row.med_ids_json = med_ids_json
        row.taken_at = now if taken else None
        # Mirror the ``onupdate=func.now()`` hook in Python so we don't
        # trigger an async refresh on the sync ``_to_response`` read path.
        row.updated_at = now
        await db.flush()
    return row


@router.get("/slots", response_model=list[SlotCompletionResponse])
async def list_slots(
    date: date_cls = Query(..., description="Local date to fetch slots for"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[SlotCompletionResponse]:
    """Return all materialized slot completions for the given date."""
    result = await db.execute(
        select(DailyEssentialSlotCompletion)
        .where(
            DailyEssentialSlotCompletion.user_id == current_user.id,
            DailyEssentialSlotCompletion.date == date,
        )
        .order_by(DailyEssentialSlotCompletion.slot_key)
    )
    return [_to_response(r) for r in result.scalars().all()]


@router.post("/slots/toggle", response_model=SlotCompletionResponse)
async def toggle_slot(
    body: SlotToggleRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> SlotCompletionResponse:
    """Materialize a slot and set ``taken_at`` in one call.

    When ``taken`` is true the slot is stamped with the current server time;
    when false the timestamp is cleared but the row is kept so the
    ``med_ids`` snapshot remains available for the client.
    """
    row = await _upsert_slot(
        db,
        current_user,
        body.date,
        body.slot_key,
        body.med_ids,
        body.taken,
    )
    return _to_response(row)


@router.patch("/slots/batch", response_model=SlotBatchResponse)
async def batch_mark_slots(
    body: SlotBatchRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> SlotBatchResponse:
    """Upsert multiple slots for a single date in one transaction."""
    rows: list[DailyEssentialSlotCompletion] = []
    for entry in body.entries:
        rows.append(
            await _upsert_slot(
                db,
                current_user,
                body.date,
                entry.slot_key,
                entry.med_ids,
                entry.taken,
            )
        )
    return SlotBatchResponse(
        updated=len(rows),
        slots=[_to_response(r) for r in rows],
    )
