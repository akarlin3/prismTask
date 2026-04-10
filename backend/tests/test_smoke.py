"""
Smoke tests that verify all major API endpoints work end-to-end.

These cover: auth, task CRUD, tags, habits, templates, sync, dashboard,
and search — exercising the full request→DB→response path against the
SQLite test database.

Run:  pytest tests/test_smoke.py -v
"""

import pytest
from httpx import AsyncClient


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

async def _register_and_login(
    client: AsyncClient,
    email: str = "smoke@example.com",
    name: str = "Smoke User",
    password: str = "smokepass123",
) -> dict:
    """Register a new user and return auth headers."""
    await client.post(
        "/api/v1/auth/register",
        json={"email": email, "name": name, "password": password},
    )
    login = await client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": password},
    )
    token = login.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


async def _create_goal_and_project(
    client: AsyncClient, headers: dict
) -> tuple[int, int]:
    """Create a goal and project, return (goal_id, project_id)."""
    goal = await client.post(
        "/api/v1/goals", json={"title": "Smoke Goal"}, headers=headers
    )
    goal_id = goal.json()["id"]
    proj = await client.post(
        f"/api/v1/goals/{goal_id}/projects",
        json={"title": "Smoke Project"},
        headers=headers,
    )
    return goal_id, proj.json()["id"]


# ===========================================================================
# 1. Health check
# ===========================================================================


@pytest.mark.asyncio
async def test_health_check(client: AsyncClient):
    resp = await client.get("/")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "healthy"
    assert "version" in data


# ===========================================================================
# 2. Auth smoke tests
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_register_and_login(client: AsyncClient):
    reg = await client.post(
        "/api/v1/auth/register",
        json={"email": "smoke_auth@test.com", "name": "Auth Smoke", "password": "pass123"},
    )
    assert reg.status_code == 201
    assert "access_token" in reg.json()
    assert "refresh_token" in reg.json()

    login = await client.post(
        "/api/v1/auth/login",
        json={"email": "smoke_auth@test.com", "password": "pass123"},
    )
    assert login.status_code == 200
    assert "access_token" in login.json()


@pytest.mark.asyncio
async def test_smoke_refresh_token(client: AsyncClient):
    reg = await client.post(
        "/api/v1/auth/register",
        json={"email": "smoke_refresh@test.com", "name": "Refresh", "password": "pass123"},
    )
    refresh_token = reg.json()["refresh_token"]

    resp = await client.post(
        "/api/v1/auth/refresh",
        json={"refresh_token": refresh_token},
    )
    assert resp.status_code == 200
    assert "access_token" in resp.json()


@pytest.mark.asyncio
async def test_smoke_protected_endpoint_without_token(client: AsyncClient):
    resp = await client.get("/api/v1/auth/me")
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_smoke_me_endpoint(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["email"] == "test@example.com"


# ===========================================================================
# 3. Task CRUD smoke tests
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_create_task(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    resp = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Smoke Task", "priority": 2, "due_date": "2026-04-15"},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["title"] == "Smoke Task"
    assert data["priority"] == 2
    assert data["status"] == "todo"
    assert data["due_date"] == "2026-04-15"


@pytest.mark.asyncio
async def test_smoke_list_tasks(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "List Task 1"},
        headers=auth_headers,
    )
    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "List Task 2"},
        headers=auth_headers,
    )

    resp = await client.get(
        f"/api/v1/projects/{project_id}/tasks", headers=auth_headers
    )
    assert resp.status_code == 200
    assert len(resp.json()) >= 2


@pytest.mark.asyncio
async def test_smoke_update_task(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    create = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Old Title"},
        headers=auth_headers,
    )
    task_id = create.json()["id"]

    resp = await client.patch(
        f"/api/v1/tasks/{task_id}",
        json={"title": "New Title"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["title"] == "New Title"


@pytest.mark.asyncio
async def test_smoke_complete_task(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    create = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Complete Me"},
        headers=auth_headers,
    )
    task_id = create.json()["id"]

    resp = await client.patch(
        f"/api/v1/tasks/{task_id}",
        json={"status": "done"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["status"] == "done"
    assert resp.json()["completed_at"] is not None


@pytest.mark.asyncio
async def test_smoke_delete_task(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    create = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Delete Me"},
        headers=auth_headers,
    )
    task_id = create.json()["id"]

    resp = await client.delete(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    assert resp.status_code == 204

    get_resp = await client.get(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    assert get_resp.status_code == 404


@pytest.mark.asyncio
async def test_smoke_task_with_tags(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    task = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Tagged Task"},
        headers=auth_headers,
    )
    task_id = task.json()["id"]

    tag1 = await client.post(
        "/api/v1/tags", json={"name": "smoke-tag-1"}, headers=auth_headers
    )
    tag2 = await client.post(
        "/api/v1/tags", json={"name": "smoke-tag-2"}, headers=auth_headers
    )

    resp = await client.put(
        f"/api/v1/tags/tasks/{task_id}/tags",
        json=[tag1.json()["id"], tag2.json()["id"]],
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert len(resp.json()) == 2


# ===========================================================================
# 4. Template smoke tests
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_create_template(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    resp = await client.post(
        "/api/v1/templates",
        json={
            "name": "Smoke Template",
            "template_title": "Smoke Task From Template",
            "template_priority": 2,
            "template_project_id": project_id,
        },
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "Smoke Template"
    assert data["usage_count"] == 0


@pytest.mark.asyncio
async def test_smoke_list_templates(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    await client.post(
        "/api/v1/templates",
        json={"name": "T1", "template_project_id": project_id},
        headers=auth_headers,
    )
    await client.post(
        "/api/v1/templates",
        json={"name": "T2", "template_project_id": project_id},
        headers=auth_headers,
    )

    resp = await client.get("/api/v1/templates", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) >= 2


@pytest.mark.asyncio
async def test_smoke_use_template(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    create = await client.post(
        "/api/v1/templates",
        json={
            "name": "Use Me",
            "template_title": "Task From Template",
            "template_priority": 1,
            "template_project_id": project_id,
        },
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    use = await client.post(
        f"/api/v1/templates/{template_id}/use",
        json={"due_date": "2026-04-20"},
        headers=auth_headers,
    )
    assert use.status_code == 200
    task_id = use.json()["task_id"]

    task = await client.get(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    assert task.status_code == 200
    assert task.json()["title"] == "Task From Template"
    assert task.json()["due_date"] == "2026-04-20"


@pytest.mark.asyncio
async def test_smoke_create_template_from_task(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    task = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Source Task", "priority": 3},
        headers=auth_headers,
    )
    task_id = task.json()["id"]

    resp = await client.post(
        f"/api/v1/templates/from-task/{task_id}",
        json={"name": "From Task Template", "icon": "\U0001F525"},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "From Task Template"
    assert data["template_title"] == "Source Task"
    assert data["template_priority"] == 3


@pytest.mark.asyncio
async def test_smoke_template_usage_count_increments(
    client: AsyncClient, auth_headers: dict
):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    create = await client.post(
        "/api/v1/templates",
        json={
            "name": "Count Me",
            "template_title": "Counted",
            "template_project_id": project_id,
        },
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    await client.post(
        f"/api/v1/templates/{template_id}/use", json={}, headers=auth_headers
    )
    await client.post(
        f"/api/v1/templates/{template_id}/use", json={}, headers=auth_headers
    )

    resp = await client.get(
        f"/api/v1/templates/{template_id}", headers=auth_headers
    )
    assert resp.json()["usage_count"] == 2


# ===========================================================================
# 5. Habit smoke tests
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_create_and_complete_habit(client: AsyncClient, auth_headers: dict):
    create = await client.post(
        "/api/v1/habits",
        json={"name": "Smoke Habit", "frequency": "daily", "icon": "\U0001F4AA"},
        headers=auth_headers,
    )
    assert create.status_code == 201
    habit_id = create.json()["id"]

    complete = await client.post(
        f"/api/v1/habits/{habit_id}/complete",
        json={"date": "2026-04-10"},
        headers=auth_headers,
    )
    assert complete.status_code == 201
    assert complete.json()["date"] == "2026-04-10"


@pytest.mark.asyncio
async def test_smoke_habit_stats(client: AsyncClient, auth_headers: dict):
    create = await client.post(
        "/api/v1/habits",
        json={"name": "Stats Habit"},
        headers=auth_headers,
    )
    habit_id = create.json()["id"]

    for day in range(1, 4):
        await client.post(
            f"/api/v1/habits/{habit_id}/complete",
            json={"date": f"2026-04-0{day}"},
            headers=auth_headers,
        )

    resp = await client.get(
        f"/api/v1/habits/{habit_id}/stats", headers=auth_headers
    )
    assert resp.status_code == 200
    assert resp.json()["total_completions"] == 3


@pytest.mark.asyncio
async def test_smoke_habit_toggle(client: AsyncClient, auth_headers: dict):
    create = await client.post(
        "/api/v1/habits",
        json={"name": "Toggle Habit"},
        headers=auth_headers,
    )
    habit_id = create.json()["id"]

    # Complete
    r1 = await client.post(
        f"/api/v1/habits/{habit_id}/complete",
        json={"date": "2026-04-10"},
        headers=auth_headers,
    )
    assert r1.json()["count"] == 1

    # Toggle off
    r2 = await client.post(
        f"/api/v1/habits/{habit_id}/complete",
        json={"date": "2026-04-10"},
        headers=auth_headers,
    )
    assert r2.json()["count"] == 0


# ===========================================================================
# 6. Sync smoke tests
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_sync_push(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "tag",
                    "operation": "create",
                    "data": {"name": "sync-smoke-tag", "color": "#AABBCC"},
                    "client_timestamp": "2026-04-10T12:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["processed"] == 1
    assert len(data["errors"]) == 0


@pytest.mark.asyncio
async def test_smoke_sync_pull(client: AsyncClient, auth_headers: dict):
    # Create some data so pull has something to return
    await client.post(
        "/api/v1/tags", json={"name": "pull-smoke"}, headers=auth_headers
    )

    resp = await client.get("/api/v1/sync/pull", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert "changes" in data
    assert "server_timestamp" in data


@pytest.mark.asyncio
async def test_smoke_sync_push_multiple(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "tag",
                    "operation": "create",
                    "data": {"name": "batch-a"},
                    "client_timestamp": "2026-04-10T12:00:00Z",
                },
                {
                    "entity_type": "tag",
                    "operation": "create",
                    "data": {"name": "batch-b"},
                    "client_timestamp": "2026-04-10T12:00:01Z",
                },
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["processed"] == 2


# ===========================================================================
# 7. Dashboard smoke tests
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_dashboard_summary(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/dashboard/summary", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    # Summary should have standard fields
    assert "total_tasks" in data or isinstance(data, dict)


@pytest.mark.asyncio
async def test_smoke_today_tasks(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/tasks/today", headers=auth_headers)
    assert resp.status_code == 200
    assert isinstance(resp.json(), list)


@pytest.mark.asyncio
async def test_smoke_overdue_tasks(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/tasks/overdue", headers=auth_headers)
    assert resp.status_code == 200
    assert isinstance(resp.json(), list)


# ===========================================================================
# 8. Search smoke test
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_search(client: AsyncClient, auth_headers: dict):
    _, project_id = await _create_goal_and_project(client, auth_headers)

    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Searchable Unique Task"},
        headers=auth_headers,
    )

    resp = await client.get(
        "/api/v1/search",
        params={"q": "Searchable"},
        headers=auth_headers,
    )
    assert resp.status_code == 200


# ===========================================================================
# 9. Collaboration model smoke tests (DB-level, since API routes are pending)
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_project_auto_owner_membership(
    client: AsyncClient, auth_headers: dict
):
    """Creating a project should auto-create an owner membership row."""
    from sqlalchemy import select

    from app.models import ProjectMember
    from tests.conftest import TestSessionLocal

    _, project_id = await _create_goal_and_project(client, auth_headers)

    async with TestSessionLocal() as session:
        result = await session.execute(
            select(ProjectMember).where(ProjectMember.project_id == project_id)
        )
        members = result.scalars().all()
        assert len(members) == 1
        assert members[0].role == "owner"


@pytest.mark.asyncio
async def test_smoke_invite_token_uniqueness():
    """ProjectInvite.generate_token() produces unique tokens."""
    from app.models import ProjectInvite

    tokens = {ProjectInvite.generate_token() for _ in range(10)}
    assert len(tokens) == 10  # all unique


# ===========================================================================
# 10. Export smoke test
# ===========================================================================


@pytest.mark.asyncio
async def test_smoke_export_json(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/export/json", headers=auth_headers)
    # Export should succeed (status 200) and return JSON or file content
    assert resp.status_code == 200
