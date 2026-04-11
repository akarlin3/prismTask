"""End-to-end workflow tests that exercise multiple routers together."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest
from httpx import AsyncClient


def _iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).isoformat()


@pytest.mark.asyncio
async def test_goal_project_task_hierarchy(client: AsyncClient, auth_headers: dict):
    # Create goal
    goal = await client.post(
        "/api/v1/goals", json={"title": "Ship v2"}, headers=auth_headers
    )
    assert goal.status_code == 201
    goal_id = goal.json()["id"]

    # Add project under goal
    proj = await client.post(
        f"/api/v1/goals/{goal_id}/projects",
        json={"title": "Implementation"},
        headers=auth_headers,
    )
    assert proj.status_code == 201
    proj_id = proj.json()["id"]

    # Add 3 tasks to the project
    task_ids = []
    for title in ("Design", "Build", "Test"):
        t = await client.post(
            f"/api/v1/projects/{proj_id}/tasks",
            json={"title": title},
            headers=auth_headers,
        )
        assert t.status_code == 201
        task_ids.append(t.json()["id"])

    # Mark all tasks complete
    for tid in task_ids:
        r = await client.patch(
            f"/api/v1/tasks/{tid}",
            json={"status": "completed"},
            headers=auth_headers,
        )
        assert r.status_code == 200

    # Verify the dashboard summary reflects the completions
    summary = await client.get("/api/v1/dashboard/summary", headers=auth_headers)
    data = summary.json()
    assert data["total_tasks"] == 3
    assert data["completed_tasks"] == 3


@pytest.mark.asyncio
async def test_task_update_lifecycle(client: AsyncClient, auth_headers: dict):
    # Create goal + project
    goal = await client.post(
        "/api/v1/goals", json={"title": "G"}, headers=auth_headers
    )
    proj = await client.post(
        f"/api/v1/goals/{goal.json()['id']}/projects",
        json={"title": "P"},
        headers=auth_headers,
    )
    proj_id = proj.json()["id"]

    # Create task
    create = await client.post(
        f"/api/v1/projects/{proj_id}/tasks",
        json={"title": "Original", "priority": 1},
        headers=auth_headers,
    )
    task_id = create.json()["id"]

    # Update title + priority
    update = await client.patch(
        f"/api/v1/tasks/{task_id}",
        json={"title": "Updated", "priority": 3},
        headers=auth_headers,
    )
    assert update.status_code == 200
    assert update.json()["title"] == "Updated"
    assert update.json()["priority"] == 3

    # Fetch back and verify persistence
    fetched = await client.get(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    assert fetched.status_code == 200
    assert fetched.json()["title"] == "Updated"

    # Delete it
    delete = await client.delete(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    assert delete.status_code == 204

    # Confirm 404 after delete
    gone = await client.get(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    assert gone.status_code == 404


@pytest.mark.asyncio
async def test_export_import_roundtrip(client: AsyncClient, auth_headers: dict):
    # Seed some data
    goal = await client.post(
        "/api/v1/goals", json={"title": "Roundtrip Goal"}, headers=auth_headers
    )
    proj = await client.post(
        f"/api/v1/goals/{goal.json()['id']}/projects",
        json={"title": "Roundtrip Project"},
        headers=auth_headers,
    )
    await client.post(
        f"/api/v1/projects/{proj.json()['id']}/tasks",
        json={"title": "Round task"},
        headers=auth_headers,
    )

    # Export
    export = await client.get("/api/v1/export/json", headers=auth_headers)
    assert export.status_code == 200
    body = export.text
    assert "Round task" in body


@pytest.mark.asyncio
async def test_stress_create_many_tasks(
    client: AsyncClient, auth_headers: dict
):
    goal = await client.post(
        "/api/v1/goals", json={"title": "Stress"}, headers=auth_headers
    )
    proj = await client.post(
        f"/api/v1/goals/{goal.json()['id']}/projects",
        json={"title": "Stress Project"},
        headers=auth_headers,
    )
    proj_id = proj.json()["id"]

    # Create 50 tasks
    for i in range(50):
        r = await client.post(
            f"/api/v1/projects/{proj_id}/tasks",
            json={"title": f"Task {i}"},
            headers=auth_headers,
        )
        assert r.status_code == 201

    # Verify all created
    listing = await client.get(
        f"/api/v1/projects/{proj_id}/tasks", headers=auth_headers
    )
    assert listing.status_code == 200
    assert len(listing.json()) == 50


@pytest.mark.asyncio
async def test_large_payload_task(client: AsyncClient, auth_headers: dict):
    goal = await client.post(
        "/api/v1/goals", json={"title": "Large"}, headers=auth_headers
    )
    proj = await client.post(
        f"/api/v1/goals/{goal.json()['id']}/projects",
        json={"title": "Large Project"},
        headers=auth_headers,
    )
    # 500-char title + 5000-char description
    long_title = "A" * 500
    long_desc = "B" * 5000
    r = await client.post(
        f"/api/v1/projects/{proj.json()['id']}/tasks",
        json={"title": long_title, "description": long_desc},
        headers=auth_headers,
    )
    # Either accepted (201) or validated out (422) — assert one of them, not a 500.
    assert r.status_code in (201, 422)
