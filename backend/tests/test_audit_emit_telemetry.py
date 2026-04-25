"""Unit tests for the medication audit-emit failure telemetry — the
backend half of the migration safety net (C5).

The audit-emit path is best-effort: a savepoint failure on
`MedicationLogEvent` insert must not roll back the parent sync
operation, but it MUST surface as an observable signal so the
closed-beta dashboard can plot silent loss the same way the
client-side `db_migration_failed` event surfaces silent migration
failures.
"""

import logging

import pytest
from sqlalchemy.ext.asyncio import AsyncSession

from app.routers import sync as sync_router
from tests.conftest import TestSessionLocal


@pytest.mark.asyncio
async def test_audit_emit_failure_increments_counter_and_logs_structured(caplog):
    """Forcing the savepoint insert to fail must:
    - increment ``audit_emit_failures_total`` keyed by ``entity_type``
    - emit a WARN log with structured ``extra`` fields the closed-beta
      dashboard reads.
    """
    sync_router.audit_emit_failures_total.clear()

    async with TestSessionLocal() as db:
        await _force_audit_emit_failure(db)

    assert sync_router.audit_emit_failures_total.get("tier_state") == 1, (
        "tier_state failure must increment the per-entity counter"
    )

    failure_records = [
        rec for rec in caplog.records
        if rec.levelno == logging.WARNING
        and getattr(rec, "audit_emit_failed", False) is True
    ]
    assert len(failure_records) == 1, "exactly one structured audit-fail log line"
    record = failure_records[0]
    assert record.entity_type == "tier_state"
    assert record.entity_cloud_id == "tier-state-cloud-1"
    assert record.exception_class != ""
    assert record.audit_emit_failures_total == 1


@pytest.mark.asyncio
async def test_audit_emit_counter_separates_entity_types():
    """tier_state and mark failures keep separate counters so the
    dashboard can tell which audit path is silently dropping events.
    """
    sync_router.audit_emit_failures_total.clear()

    async with TestSessionLocal() as db:
        await _force_audit_emit_failure(db, entity_type="tier_state", cloud_id="ts-1")
        await _force_audit_emit_failure(db, entity_type="tier_state", cloud_id="ts-2")
        await _force_audit_emit_failure(db, entity_type="mark", cloud_id="m-1")

    assert sync_router.audit_emit_failures_total["tier_state"] == 2
    assert sync_router.audit_emit_failures_total["mark"] == 1


async def _force_audit_emit_failure(
    db: AsyncSession,
    entity_type: str = "tier_state",
    cloud_id: str = "tier-state-cloud-1"
) -> None:
    """Hand `_emit_audit_events` a record whose `user_id` violates the
    FK constraint on `MedicationLogEvent.user_id` so the savepoint
    insert raises and the except block runs.
    """
    bad_record = {
        "user_id": -1,  # No user with id=-1 → FK violation on commit
        "entity_type": entity_type,
        "entity_cloud_id": cloud_id,
        "intended_time": None,
        "logged_at": None,  # NOT NULL → triggers the integrity error
        "operation": "create",
    }
    await sync_router._emit_audit_events(db, [bad_record])
