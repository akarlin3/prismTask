import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_toggle_slot_materializes_row(
    client: AsyncClient, auth_headers: dict
):
    resp = await client.post(
        "/api/v1/daily-essentials/slots/toggle",
        json={
            "date": "2026-04-17",
            "slot_key": "08:00",
            "med_ids": ["specific_time:lipitor", "self_care_step:metformin"],
            "taken": True,
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["slot_key"] == "08:00"
    assert data["taken_at"] is not None
    assert set(data["med_ids"]) == {
        "specific_time:lipitor",
        "self_care_step:metformin",
    }


@pytest.mark.asyncio
async def test_toggle_slot_clears_taken_when_unchecked(
    client: AsyncClient, auth_headers: dict
):
    # Check it first.
    await client.post(
        "/api/v1/daily-essentials/slots/toggle",
        json={
            "date": "2026-04-17",
            "slot_key": "08:00",
            "med_ids": ["specific_time:lipitor"],
            "taken": True,
        },
        headers=auth_headers,
    )
    # Then uncheck — the row should persist but taken_at should clear.
    resp = await client.post(
        "/api/v1/daily-essentials/slots/toggle",
        json={
            "date": "2026-04-17",
            "slot_key": "08:00",
            "med_ids": ["specific_time:lipitor"],
            "taken": False,
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["taken_at"] is None
    assert data["med_ids"] == ["specific_time:lipitor"]

    listing = await client.get(
        "/api/v1/daily-essentials/slots",
        params={"date": "2026-04-17"},
        headers=auth_headers,
    )
    assert listing.status_code == 200
    rows = listing.json()
    assert len(rows) == 1
    assert rows[0]["taken_at"] is None


@pytest.mark.asyncio
async def test_batch_mark_slots(client: AsyncClient, auth_headers: dict):
    resp = await client.patch(
        "/api/v1/daily-essentials/slots/batch",
        json={
            "date": "2026-04-17",
            "entries": [
                {
                    "slot_key": "08:00",
                    "med_ids": ["self_care_step:lipitor"],
                    "taken": True,
                },
                {
                    "slot_key": "13:00",
                    "med_ids": ["self_care_step:vitamin_d"],
                    "taken": True,
                },
                {
                    "slot_key": "anytime",
                    "med_ids": ["interval_habit:adderall"],
                    "taken": False,
                },
            ],
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["updated"] == 3
    by_slot = {s["slot_key"]: s for s in body["slots"]}
    assert by_slot["08:00"]["taken_at"] is not None
    assert by_slot["13:00"]["taken_at"] is not None
    assert by_slot["anytime"]["taken_at"] is None

    # GET /slots reflects all three materialized rows.
    listing = await client.get(
        "/api/v1/daily-essentials/slots",
        params={"date": "2026-04-17"},
        headers=auth_headers,
    )
    assert listing.status_code == 200
    keys = sorted(r["slot_key"] for r in listing.json())
    assert keys == ["08:00", "13:00", "anytime"]


@pytest.mark.asyncio
async def test_list_slots_scoped_by_date(
    client: AsyncClient, auth_headers: dict
):
    await client.post(
        "/api/v1/daily-essentials/slots/toggle",
        json={
            "date": "2026-04-17",
            "slot_key": "08:00",
            "med_ids": ["a"],
            "taken": True,
        },
        headers=auth_headers,
    )
    await client.post(
        "/api/v1/daily-essentials/slots/toggle",
        json={
            "date": "2026-04-18",
            "slot_key": "08:00",
            "med_ids": ["a"],
            "taken": True,
        },
        headers=auth_headers,
    )

    resp = await client.get(
        "/api/v1/daily-essentials/slots",
        params={"date": "2026-04-17"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    rows = resp.json()
    assert len(rows) == 1
    assert rows[0]["date"] == "2026-04-17"
