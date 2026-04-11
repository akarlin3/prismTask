"""Tests for the dashboard router endpoints."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient


@pytest.fixture
async def project_id(client: AsyncClient, auth_headers: dict) -> int:
    goal_resp = await client.post(
        "/api/v1/goals", json={"title": "Dashboard Goal"}, headers=auth_headers
    )
    goal_id = goal_resp.json()["id"]
    project_resp = await client.post(
        f"/api/v1/goals/{goal_id}/projects",
        json={"title": "Dashboard Project"},
        headers=auth_headers,
    )
    return project_resp.json()["id"]


def _iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).isoformat()


@pytest.mark.asyncio
async def test_summary_with_no_tasks_returns_zero_counts(
    client: AsyncClient, auth_headers: dict
):
    resp = await client.get("/api/v1/dashboard/summary", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["total_tasks"] == 0
    assert data["completed_tasks"] == 0
    assert data["overdue_tasks"] == 0


@pytest.mark.asyncio
async def test_summary_counts_tasks_after_creation(
    client: AsyncClient, auth_headers: dict, project_id: int
):
    # Create three tasks: one plain, one completed, one overdue.
    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Plain task"},
        headers=auth_headers,
    )
    completed = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Completed task"},
        headers=auth_headers,
    )
    await client.patch(
        f"/api/v1/tasks/{completed.json()['id']}",
        json={"status": "completed"},
        headers=auth_headers,
    )

    overdue_due = _iso(datetime.now(timezone.utc) - timedelta(days=2))
    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Overdue task", "due_date": overdue_due},
        headers=auth_headers,
    )

    resp = await client.get("/api/v1/dashboard/summary", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["total_tasks"] == 3
    assert data["completed_tasks"] == 1
    assert data["overdue_tasks"] == 1


@pytest.mark.asyncio
async def test_tasks_today_returns_only_today_due_tasks(
    client: AsyncClient, auth_headers: dict, project_id: int
):
    today = _iso(datetime.now(timezone.utc).replace(hour=12, minute=0, second=0, microsecond=0))
    tomorrow = _iso(datetime.now(timezone.utc) + timedelta(days=1))

    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Today task", "due_date": today},
        headers=auth_headers,
    )
    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Tomorrow task", "due_date": tomorrow},
        headers=auth_headers,
    )

    resp = await client.get("/api/v1/tasks/today", headers=auth_headers)
    assert resp.status_code == 200
    titles = [t["title"] for t in resp.json()]
    assert "Today task" in titles
    assert "Tomorrow task" not in titles


@pytest.mark.asyncio
async def test_tasks_overdue_excludes_completed_tasks(
    client: AsyncClient, auth_headers: dict, project_id: int
):
    overdue_due = _iso(datetime.now(timezone.utc) - timedelta(days=3))
    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Still overdue", "due_date": overdue_due},
        headers=auth_headers,
    )
    done_resp = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Overdue but done", "due_date": overdue_due},
        headers=auth_headers,
    )
    await client.patch(
        f"/api/v1/tasks/{done_resp.json()['id']}",
        json={"status": "completed"},
        headers=auth_headers,
    )

    resp = await client.get("/api/v1/tasks/overdue", headers=auth_headers)
    assert resp.status_code == 200
    titles = [t["title"] for t in resp.json()]
    assert "Still overdue" in titles
    assert "Overdue but done" not in titles


@pytest.mark.asyncio
async def test_tasks_upcoming_respects_day_window(
    client: AsyncClient, auth_headers: dict, project_id: int
):
    in_3_days = _iso(datetime.now(timezone.utc) + timedelta(days=3))
    in_10_days = _iso(datetime.now(timezone.utc) + timedelta(days=10))

    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Upcoming soon", "due_date": in_3_days},
        headers=auth_headers,
    )
    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Upcoming far", "due_date": in_10_days},
        headers=auth_headers,
    )

    resp = await client.get("/api/v1/tasks/upcoming?days=7", headers=auth_headers)
    assert resp.status_code == 200
    titles = [t["title"] for t in resp.json()]
    assert "Upcoming soon" in titles
    assert "Upcoming far" not in titles


@pytest.mark.asyncio
async def test_dashboard_summary_requires_auth(client: AsyncClient):
    resp = await client.get("/api/v1/dashboard/summary")
    assert resp.status_code in (401, 403)
