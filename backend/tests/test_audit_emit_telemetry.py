"""Unit tests for the medication audit-emit failure telemetry — the
backend half of the migration safety net (C5).

The audit-emit path is best-effort: a savepoint failure on
`MedicationLogEvent` insert must not roll back the parent sync
operation, but it MUST surface as an observable signal so the
closed-beta dashboard can plot silent loss the same way the
client-side `db_migration_failed` event surfaces silent migration
failures.

Tests assert the in-process counter + structured-log shape directly.
The full-stack path (HTTP push that triggers an audit savepoint
failure) is covered by the existing test_medication_sync_audit.py
suite — we don't duplicate it here.
"""

import logging

import pytest

from app.routers import sync as sync_router


@pytest.mark.asyncio
async def test_audit_emit_failure_increments_counter_and_logs_structured(caplog):
    """A failing savepoint must increment the per-entity counter and
    emit a WARN log carrying the four structured ``extra`` fields the
    closed-beta dashboard indexes (``audit_emit_failed``, ``entity_type``,
    ``entity_cloud_id``, ``exception_class``).
    """
    sync_router.audit_emit_failures_total.clear()
    caplog.set_level(logging.WARNING, logger="app.routers.sync")

    # `_emit_audit_events` swallows every exception inside its for-loop,
    # so we can drive the failure path with a `None` db that throws on
    # the first attribute access (`db.begin_nested()`). The except block
    # bumps the counter and emits the structured log line — the bare
    # exception captured is irrelevant because the contract is "any
    # failure is logged + counted".
    bad_record = {
        "user_id": -1,
        "entity_type": "tier_state",
        "entity_cloud_id": "tier-state-cloud-1",
        "intended_time": None,
        "logged_at": None,
        "operation": "create",
    }
    await sync_router._emit_audit_events(_RaisingDb(), [bad_record])

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

    db = _RaisingDb()
    await sync_router._emit_audit_events(
        db,
        [
            _bad_record("tier_state", "ts-1"),
            _bad_record("tier_state", "ts-2"),
            _bad_record("mark", "m-1"),
        ],
    )

    assert sync_router.audit_emit_failures_total["tier_state"] == 2
    assert sync_router.audit_emit_failures_total["mark"] == 1


def _bad_record(entity_type: str, cloud_id: str) -> dict:
    return {
        "user_id": -1,
        "entity_type": entity_type,
        "entity_cloud_id": cloud_id,
        "intended_time": None,
        "logged_at": None,
        "operation": "create",
    }


class _RaisingDb:
    """Minimal `AsyncSession`-shaped stub whose every operation raises.

    Drives the savepoint failure path without touching SQLAlchemy or the
    shared test database, so this test never leaks connection state into
    sibling test files.
    """

    def begin_nested(self):
        raise RuntimeError("simulated savepoint failure")

    def add(self, _entity):
        raise RuntimeError("simulated add failure")
