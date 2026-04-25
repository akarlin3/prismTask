"""Medication audit log query endpoint.

Medication entities themselves sync via /sync/push (registered in
``ENTITY_MAP`` in routers/sync.py). This router only exposes the audit
log so support / debugging can answer questions like "when did this
device push this medication mark, and what intended_time did it claim?"
"""

from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import MedicationLogEvent, User

router = APIRouter(prefix="/medications", tags=["medications"])


class MedicationLogEventResponse(BaseModel):
    id: int
    entity_type: str
    entity_cloud_id: Optional[str]
    intended_time: Optional[datetime]
    logged_at: datetime
    sync_received_at: datetime
    operation: str

    model_config = {"from_attributes": True}


@router.get("/log-events", response_model=list[MedicationLogEventResponse])
async def list_log_events(
    since: datetime | None = Query(
        default=None,
        description="Only return events with logged_at strictly greater than this.",
    ),
    limit: int = Query(default=100, ge=1, le=500),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[MedicationLogEventResponse]:
    """Return this user's medication audit events, newest first.

    Auth-scoped by ``user_id`` — a user can only query their own events.
    """
    query = (
        select(MedicationLogEvent)
        .where(MedicationLogEvent.user_id == current_user.id)
        .order_by(MedicationLogEvent.logged_at.desc())
        .limit(limit)
    )
    if since is not None:
        query = query.where(MedicationLogEvent.logged_at > since)
    result = await db.execute(query)
    return list(result.scalars().all())
