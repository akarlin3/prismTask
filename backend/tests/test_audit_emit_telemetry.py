"""Unit tests for the medication audit-emit failure telemetry — the
backend half of the migration safety net (C5).

The audit-emit path is best-effort: a savepoint failure on
`MedicationLogEvent` insert must not roll back the parent sync
operation, but it MUST surface as an observable signal so the
closed-beta dashboard can plot silent loss the same way the
client-side `db_migration_failed` event surfaces silent migration
failures.

Tests assert the prometheus_client Counter shape + structured-log
shape directly. The full-stack path (HTTP push that triggers an audit
savepoint failure) is covered by the existing test_medication_sync_audit.py
suite — we don't duplicate it here.
"""

import logging

import pytest
from prometheus_client import REGISTRY

from app.routers import sync as sync_router


def _counter_value(entity_type: str) -> float:
    """Read the audit_emit_failures_total Counter sample for an entity_type.

    Uses ``REGISTRY.get_sample_value`` (public API) so we don't depend on
    ``Counter._value.get()`` (private). The Counter's metric name is
    ``audit_emit_failures`` (prometheus_client strips the ``_total`` suffix
    internally) but the sample series we want is the count, named
    ``audit_emit_failures_total`` (the suffix is reattached on the sample).
    Returns 0 when no sample exists yet.
    """
    sample = REGISTRY.get_sample_value(
        "audit_emit_failures_total", {"entity_type": entity_type}
    )
    return sample if sample is not None else 0.0


def _reset_counter() -> None:
    """Clear all label children on the audit_emit_failures_total Counter."""
    sync_router.audit_emit_failures_total.clear()


@pytest.mark.asyncio
async def test_audit_emit_failure_increments_counter_and_logs_structured(caplog):
    """A failing savepoint must increment the per-entity Counter and
    emit a WARN log carrying the four structured ``extra`` fields the
    closed-beta dashboard indexes (``audit_emit_failed``, ``entity_type``,
    ``entity_cloud_id``, ``exception_class``).
    """
    _reset_counter()
    caplog.set_level(logging.WARNING, logger="app.routers.sync")

    # `_emit_audit_events` swallows every exception inside its for-loop,
    # so we can drive the failure path with a `None`-ish db that throws on
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

    assert _counter_value("tier_state") == 1, (
        "tier_state failure must increment the per-entity Counter sample"
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
    # The log field is read from the Counter (cast to int for grep
    # friendliness), so it must mirror the sample value.
    assert record.audit_emit_failures_total == 1


@pytest.mark.asyncio
async def test_audit_emit_counter_keys_by_entity_type():
    """Counter labels by entity_type so the dashboard can tell which audit
    path is silently dropping events. Even though tier_state is currently
    the only audited entity (medication_mark was dropped), the per-
    entity-type accounting must still survive future additions.
    """
    _reset_counter()

    db = _RaisingDb()
    await sync_router._emit_audit_events(
        db,
        [
            _bad_record("tier_state", "ts-1"),
            _bad_record("tier_state", "ts-2"),
        ],
    )

    assert _counter_value("tier_state") == 2


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
