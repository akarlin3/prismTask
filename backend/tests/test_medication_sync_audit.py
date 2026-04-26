"""Tests for medication entity sync + audit log integration (PR1 of 4).

Covers:
- Medication entities can be created/updated/deleted via /sync/push
- Audit rows are written for medication_tier_state ops
- Audit rows are NOT written for non-medication entities (tag, habit, etc.)
- GET /api/v1/medications/log-events returns only the caller's events
- Disallowed fields (e.g. user_id) are stripped on /sync/push

medication_mark coverage was removed in chore/drop-orphan-medication-marks
because the table was never populated by any production write path —
see docs/audits/PHASE_D_BUNDLE_AUDIT.md Item 3.
"""

import asyncio

import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.models import Medication, MedicationLogEvent, MedicationSlot
from tests.conftest import TestSessionLocal


async def _read_audit_rows() -> list[MedicationLogEvent]:
    """Audit writes are inline within the request session, so they're
    visible immediately after the /sync/push response returns."""
    async with TestSessionLocal() as session:
        result = await session.execute(
            select(MedicationLogEvent).order_by(MedicationLogEvent.id)
        )
        return list(result.scalars().all())


async def _seed_medication_and_slot(client: AsyncClient, auth_headers: dict) -> tuple[int, int]:
    """Push a Medication + MedicationSlot via /sync/push and return their backend IDs."""
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication",
                    "operation": "create",
                    "data": {
                        "cloud_id": "med-cloud-1",
                        "name": "Adderall",
                        "dosage": "20mg",
                    },
                    "client_timestamp": "2026-04-23T08:00:00Z",
                },
                {
                    "entity_type": "medication_slot",
                    "operation": "create",
                    "data": {
                        "cloud_id": "slot-cloud-1",
                        "slot_key": "morning",
                        "ideal_time": "08:00",
                        "drift_minutes": 30,
                    },
                    "client_timestamp": "2026-04-23T08:00:00Z",
                },
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200, resp.text
    assert resp.json()["processed"] == 2

    async with TestSessionLocal() as session:
        med = (await session.execute(select(Medication).where(Medication.cloud_id == "med-cloud-1"))).scalar_one()
        slot = (await session.execute(select(MedicationSlot).where(MedicationSlot.cloud_id == "slot-cloud-1"))).scalar_one()
        return med.id, slot.id


@pytest.mark.asyncio
async def test_create_medication_via_sync_push(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication",
                    "operation": "create",
                    "data": {
                        "cloud_id": "med-abc",
                        "name": "Vitamin D",
                        "dosage": "2000IU",
                        "is_active": True,
                    },
                    "client_timestamp": "2026-04-23T08:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200, resp.text
    assert resp.json()["processed"] == 1
    assert resp.json()["errors"] == []

    async with TestSessionLocal() as session:
        med = (await session.execute(select(Medication).where(Medication.cloud_id == "med-abc"))).scalar_one()
        assert med.name == "Vitamin D"
        assert med.dosage == "2000IU"
        assert med.user_id is not None  # forced server-side


@pytest.mark.asyncio
async def test_audit_row_written_on_tier_state_create(client: AsyncClient, auth_headers: dict):
    await _seed_medication_and_slot(client, auth_headers)

    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication_tier_state",
                    "operation": "create",
                    "data": {
                        "cloud_id": "tier-state-cloud-1",
                        "medication_cloud_id": "med-cloud-1",
                        "slot_cloud_id": "slot-cloud-1",
                        "log_date": "2026-04-23",
                        "tier": "complete",
                        "tier_source": "user_set",
                        "intended_time": "2026-04-23T08:05:00+00:00",
                        "logged_at": "2026-04-23T10:30:00+00:00",
                    },
                    "client_timestamp": "2026-04-23T10:30:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200, resp.text
    assert resp.json()["processed"] == 1

    audit_rows = await _read_audit_rows()
    assert len(audit_rows) == 1
    row = audit_rows[0]
    assert row.entity_type == "tier_state"
    assert row.entity_cloud_id == "tier-state-cloud-1"
    # SQLite strips tzinfo from DateTime(timezone=True) columns; Postgres
    # TIMESTAMPTZ in production preserves it. Compare wall-clock components.
    assert (row.intended_time.year, row.intended_time.month, row.intended_time.day,
            row.intended_time.hour, row.intended_time.minute) == (2026, 4, 23, 8, 5)
    assert (row.logged_at.year, row.logged_at.month, row.logged_at.day,
            row.logged_at.hour, row.logged_at.minute) == (2026, 4, 23, 10, 30)
    assert row.operation == "create"
    assert row.sync_received_at is not None


# test_audit_row_written_on_mark_create — removed in
# chore/drop-orphan-medication-marks; the medication_mark entity no
# longer exists in the sync protocol because the table was never
# populated by any production write path. See
# docs/audits/PHASE_D_BUNDLE_AUDIT.md Item 3.


@pytest.mark.asyncio
async def test_audit_NOT_written_for_non_medication_entity(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "tag",
                    "operation": "create",
                    "data": {"name": "no-audit"},
                    "client_timestamp": "2026-04-23T08:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    # Wait briefly to make sure no background task sneaks a row in.
    await asyncio.sleep(0.2)
    async with TestSessionLocal() as session:
        rows = (await session.execute(select(MedicationLogEvent))).scalars().all()
        assert list(rows) == []


@pytest.mark.asyncio
async def test_disallowed_user_id_stripped_on_create(client: AsyncClient, auth_headers: dict):
    """Client cannot smuggle a user_id — the server forces it from auth."""
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication",
                    "operation": "create",
                    "data": {
                        "cloud_id": "med-evil",
                        "name": "Evil",
                        "user_id": 99999,  # should be stripped
                    },
                    "client_timestamp": "2026-04-23T08:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["processed"] == 1

    async with TestSessionLocal() as session:
        med = (await session.execute(select(Medication).where(Medication.cloud_id == "med-evil"))).scalar_one()
        assert med.user_id != 99999  # forced to the authenticated user


@pytest.mark.asyncio
async def test_fk_validation_rejects_other_users_medication(
    client: AsyncClient, auth_headers: dict
):
    """User B cannot reference user A's medication via cloud_id."""
    # Seed a medication owned by user A (cloud_id "med-cloud-1").
    await _seed_medication_and_slot(client, auth_headers)

    # Register a second user.
    reg = await client.post(
        "/api/v1/auth/register",
        json={"email": "other@example.com", "name": "Other", "password": "otherpass123"},
    )
    assert reg.status_code == 201
    login = await client.post(
        "/api/v1/auth/login",
        json={"email": "other@example.com", "password": "otherpass123"},
    )
    other_headers = {"Authorization": f"Bearer {login.json()['access_token']}"}

    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication_tier_state",
                    "operation": "create",
                    "data": {
                        "cloud_id": "ts-evil",
                        # Cloud_id belongs to user A — resolver scopes by user_id.
                        "medication_cloud_id": "med-cloud-1",
                        "slot_cloud_id": "slot-cloud-1",
                        "log_date": "2026-04-23",
                        "tier": "complete",
                        "logged_at": "2026-04-23T10:00:00+00:00",
                    },
                    "client_timestamp": "2026-04-23T10:00:00Z",
                }
            ]
        },
        headers=other_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["processed"] == 0
    assert len(body["errors"]) == 1
    assert "medication_cloud_id" in body["errors"][0]
    assert "did not resolve" in body["errors"][0]


@pytest.mark.asyncio
async def test_get_log_events_returns_only_callers_events(
    client: AsyncClient, auth_headers: dict
):
    await _seed_medication_and_slot(client, auth_headers)

    # Push a tier state (creates one audit row).
    await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication_tier_state",
                    "operation": "create",
                    "data": {
                        "cloud_id": "ts-for-get",
                        "medication_cloud_id": "med-cloud-1",
                        "slot_cloud_id": "slot-cloud-1",
                        "log_date": "2026-04-23",
                        "tier": "complete",
                        "logged_at": "2026-04-23T10:00:00+00:00",
                    },
                    "client_timestamp": "2026-04-23T10:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    await _read_audit_rows()

    # Caller can read their own events.
    resp = await client.get("/api/v1/medications/log-events", headers=auth_headers)
    assert resp.status_code == 200
    events = resp.json()
    assert len(events) >= 1
    assert events[0]["entity_type"] == "tier_state"
    assert events[0]["entity_cloud_id"] == "ts-for-get"

    # Other user sees nothing.
    reg = await client.post(
        "/api/v1/auth/register",
        json={"email": "stranger@example.com", "name": "Stranger", "password": "strangerpass"},
    )
    assert reg.status_code == 201
    login = await client.post(
        "/api/v1/auth/login",
        json={"email": "stranger@example.com", "password": "strangerpass"},
    )
    stranger_headers = {"Authorization": f"Bearer {login.json()['access_token']}"}
    resp = await client.get("/api/v1/medications/log-events", headers=stranger_headers)
    assert resp.status_code == 200
    assert resp.json() == []


@pytest.mark.asyncio
async def test_get_log_events_requires_auth(client: AsyncClient):
    resp = await client.get("/api/v1/medications/log-events")
    assert resp.status_code in (401, 403)


@pytest.mark.asyncio
async def test_tier_state_create_rejects_missing_cloud_id_fk(
    client: AsyncClient, auth_headers: dict
):
    """A tier_state push without `medication_cloud_id` is rejected with
    an explicit error — defensive against client bugs that accidentally
    drop the FK reference."""
    await _seed_medication_and_slot(client, auth_headers)
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication_tier_state",
                    "operation": "create",
                    "data": {
                        "cloud_id": "ts-no-fk",
                        "slot_cloud_id": "slot-cloud-1",
                        # medication_cloud_id intentionally omitted
                        "log_date": "2026-04-23",
                        "tier": "complete",
                        "logged_at": "2026-04-23T10:00:00+00:00",
                    },
                    "client_timestamp": "2026-04-23T10:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    body = resp.json()
    assert body["processed"] == 0
    assert "medication_cloud_id is required" in body["errors"][0]


@pytest.mark.asyncio
async def test_tier_state_create_rejects_unknown_cloud_id(
    client: AsyncClient, auth_headers: dict
):
    await _seed_medication_and_slot(client, auth_headers)
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication_tier_state",
                    "operation": "create",
                    "data": {
                        "cloud_id": "ts-bad-fk",
                        "medication_cloud_id": "doesnt-exist",
                        "slot_cloud_id": "slot-cloud-1",
                        "log_date": "2026-04-23",
                        "tier": "complete",
                        "logged_at": "2026-04-23T10:00:00+00:00",
                    },
                    "client_timestamp": "2026-04-23T10:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    body = resp.json()
    assert body["processed"] == 0
    assert "did not resolve" in body["errors"][0]


@pytest.mark.asyncio
async def test_get_log_events_since_filter(client: AsyncClient, auth_headers: dict):
    await _seed_medication_and_slot(client, auth_headers)

    # Push two tier-state ops with different logged_at values.
    await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "medication_tier_state",
                    "operation": "create",
                    "data": {
                        "cloud_id": "ts-old",
                        "medication_cloud_id": "med-cloud-1",
                        "slot_cloud_id": "slot-cloud-1",
                        "log_date": "2026-04-22",
                        "tier": "complete",
                        "logged_at": "2026-04-22T08:00:00+00:00",
                    },
                    "client_timestamp": "2026-04-22T08:00:00Z",
                },
                {
                    "entity_type": "medication_tier_state",
                    "operation": "create",
                    "data": {
                        "cloud_id": "ts-new",
                        "medication_cloud_id": "med-cloud-1",
                        "slot_cloud_id": "slot-cloud-1",
                        "log_date": "2026-04-23",
                        "tier": "complete",
                        "logged_at": "2026-04-23T08:00:00+00:00",
                    },
                    "client_timestamp": "2026-04-23T08:00:00Z",
                },
            ]
        },
        headers=auth_headers,
    )
    await _read_audit_rows()

    # since=2026-04-22T12:00 should return only the newer event.
    resp = await client.get(
        "/api/v1/medications/log-events",
        params={"since": "2026-04-22T12:00:00+00:00"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    events = resp.json()
    assert len(events) == 1
    assert events[0]["entity_cloud_id"] == "ts-new"
