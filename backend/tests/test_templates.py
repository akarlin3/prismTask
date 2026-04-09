import json

import pytest
from httpx import AsyncClient


@pytest.fixture
async def goal_and_project(client: AsyncClient, auth_headers: dict):
    goal_resp = await client.post(
        "/api/v1/goals", json={"title": "Template Goal"}, headers=auth_headers
    )
    goal_id = goal_resp.json()["id"]
    project_resp = await client.post(
        f"/api/v1/goals/{goal_id}/projects",
        json={"title": "Template Project"},
        headers=auth_headers,
    )
    return goal_id, project_resp.json()["id"]


async def _other_user_headers(client: AsyncClient) -> dict:
    await client.post(
        "/api/v1/auth/register",
        json={
            "email": "other@example.com",
            "name": "Other User",
            "password": "otherpass123",
        },
    )
    login = await client.post(
        "/api/v1/auth/login",
        json={"email": "other@example.com", "password": "otherpass123"},
    )
    token = login.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.asyncio
async def test_create_and_get_template(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    resp = await client.post(
        "/api/v1/templates",
        json={
            "name": "Weekly Review",
            "icon": "📋",
            "category": "Routines",
            "template_title": "Do weekly review",
            "template_priority": 2,
            "template_project_id": project_id,
        },
        headers=auth_headers,
    )
    assert resp.status_code == 201, resp.text
    data = resp.json()
    assert data["name"] == "Weekly Review"
    assert data["icon"] == "📋"
    assert data["category"] == "Routines"
    assert data["template_title"] == "Do weekly review"
    assert data["template_priority"] == 2
    assert data["is_built_in"] is False
    assert data["usage_count"] == 0
    assert data["last_used_at"] is None

    template_id = data["id"]
    get_resp = await client.get(
        f"/api/v1/templates/{template_id}", headers=auth_headers
    )
    assert get_resp.status_code == 200
    assert get_resp.json()["name"] == "Weekly Review"


@pytest.mark.asyncio
async def test_list_templates_sorted_by_usage(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    # Create a few templates
    a = await client.post(
        "/api/v1/templates",
        json={"name": "Alpha", "template_project_id": project_id, "template_title": "Alpha Task"},
        headers=auth_headers,
    )
    b = await client.post(
        "/api/v1/templates",
        json={"name": "Bravo", "template_project_id": project_id, "template_title": "Bravo Task"},
        headers=auth_headers,
    )
    # Use Bravo twice to bump its usage_count above Alpha
    await client.post(
        f"/api/v1/templates/{b.json()['id']}/use", json={}, headers=auth_headers
    )
    await client.post(
        f"/api/v1/templates/{b.json()['id']}/use", json={}, headers=auth_headers
    )
    await client.post(
        f"/api/v1/templates/{a.json()['id']}/use", json={}, headers=auth_headers
    )

    resp = await client.get("/api/v1/templates", headers=auth_headers)
    assert resp.status_code == 200
    items = resp.json()
    names = [t["name"] for t in items if t["name"] in ("Alpha", "Bravo")]
    assert names[0] == "Bravo"  # most used first
    assert names[1] == "Alpha"


@pytest.mark.asyncio
async def test_update_template(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    create = await client.post(
        "/api/v1/templates",
        json={"name": "Initial", "template_project_id": project_id},
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    resp = await client.patch(
        f"/api/v1/templates/{template_id}",
        json={"name": "Updated", "category": "Work"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["name"] == "Updated"
    assert data["category"] == "Work"


@pytest.mark.asyncio
async def test_delete_template(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    create = await client.post(
        "/api/v1/templates",
        json={"name": "Trash", "template_project_id": project_id},
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    resp = await client.delete(
        f"/api/v1/templates/{template_id}", headers=auth_headers
    )
    assert resp.status_code == 204

    get_resp = await client.get(
        f"/api/v1/templates/{template_id}", headers=auth_headers
    )
    assert get_resp.status_code == 404


@pytest.mark.asyncio
async def test_use_template_creates_task_with_fields(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    create = await client.post(
        "/api/v1/templates",
        json={
            "name": "Daily Standup",
            "template_title": "Attend daily standup",
            "template_description": "15 min sync",
            "template_priority": 2,
            "template_project_id": project_id,
        },
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    use_resp = await client.post(
        f"/api/v1/templates/{template_id}/use",
        json={"due_date": "2026-04-15"},
        headers=auth_headers,
    )
    assert use_resp.status_code == 200, use_resp.text
    task_id = use_resp.json()["task_id"]

    # Verify the task was created with template fields
    task_resp = await client.get(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    assert task_resp.status_code == 200
    task = task_resp.json()
    assert task["title"] == "Attend daily standup"
    assert task["description"] == "15 min sync"
    assert task["priority"] == 2
    assert task["project_id"] == project_id
    assert task["due_date"] == "2026-04-15"

    # Verify usage tracking was updated
    get_tmpl = await client.get(
        f"/api/v1/templates/{template_id}", headers=auth_headers
    )
    data = get_tmpl.json()
    assert data["usage_count"] == 1
    assert data["last_used_at"] is not None


@pytest.mark.asyncio
async def test_use_template_with_subtasks(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    subtasks = ["Gather notes", "Draft agenda", "Send invite"]
    create = await client.post(
        "/api/v1/templates",
        json={
            "name": "Meeting Prep",
            "template_title": "Prepare meeting",
            "template_project_id": project_id,
            "template_subtasks_json": json.dumps(subtasks),
        },
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    use_resp = await client.post(
        f"/api/v1/templates/{template_id}/use", json={}, headers=auth_headers
    )
    assert use_resp.status_code == 200
    task_id = use_resp.json()["task_id"]

    task_resp = await client.get(f"/api/v1/tasks/{task_id}", headers=auth_headers)
    created_subtasks = task_resp.json()["subtasks"]
    assert len(created_subtasks) == 3
    titles = [s["title"] for s in created_subtasks]
    for expected in subtasks:
        assert expected in titles


@pytest.mark.asyncio
async def test_create_template_from_task(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    task_resp = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={
            "title": "Weekly Report",
            "description": "Write and submit weekly report",
            "priority": 2,
        },
        headers=auth_headers,
    )
    task_id = task_resp.json()["id"]

    # Add a subtask so the template captures it
    await client.post(
        f"/api/v1/tasks/{task_id}/subtasks",
        json={"title": "Collect metrics"},
        headers=auth_headers,
    )

    resp = await client.post(
        f"/api/v1/templates/from-task/{task_id}",
        json={"name": "Report Template", "icon": "📊", "category": "Work"},
        headers=auth_headers,
    )
    assert resp.status_code == 201, resp.text
    data = resp.json()
    assert data["name"] == "Report Template"
    assert data["icon"] == "📊"
    assert data["category"] == "Work"
    assert data["template_title"] == "Weekly Report"
    assert data["template_description"] == "Write and submit weekly report"
    assert data["template_priority"] == 2
    assert data["template_project_id"] == project_id
    assert data["template_subtasks_json"] is not None
    subtasks = json.loads(data["template_subtasks_json"])
    assert "Collect metrics" in subtasks


@pytest.mark.asyncio
async def test_template_authorization_isolation(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    _, project_id = goal_and_project
    create = await client.post(
        "/api/v1/templates",
        json={"name": "Private", "template_project_id": project_id},
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    other_headers = await _other_user_headers(client)

    # Other user cannot read it
    resp = await client.get(
        f"/api/v1/templates/{template_id}", headers=other_headers
    )
    assert resp.status_code == 404

    # Other user cannot update it
    resp = await client.patch(
        f"/api/v1/templates/{template_id}",
        json={"name": "Hacked"},
        headers=other_headers,
    )
    assert resp.status_code == 404

    # Other user cannot delete it
    resp = await client.delete(
        f"/api/v1/templates/{template_id}", headers=other_headers
    )
    assert resp.status_code == 404

    # Other user's list does not include the private template
    list_resp = await client.get("/api/v1/templates", headers=other_headers)
    assert list_resp.status_code == 200
    assert all(t["id"] != template_id for t in list_resp.json())


@pytest.mark.asyncio
async def test_modifying_builtin_template_clears_flag(
    client: AsyncClient, auth_headers: dict, goal_and_project
):
    from sqlalchemy import select

    from app.models import TaskTemplate
    from tests.conftest import TestSessionLocal

    _, project_id = goal_and_project
    create = await client.post(
        "/api/v1/templates",
        json={"name": "Built-in Starter", "template_project_id": project_id},
        headers=auth_headers,
    )
    template_id = create.json()["id"]

    # Directly mark the template as built-in in the DB
    async with TestSessionLocal() as session:
        result = await session.execute(
            select(TaskTemplate).where(TaskTemplate.id == template_id)
        )
        tmpl = result.scalar_one()
        tmpl.is_built_in = True
        await session.commit()

    # Sanity check: GET shows is_built_in = True
    get_resp = await client.get(
        f"/api/v1/templates/{template_id}", headers=auth_headers
    )
    assert get_resp.json()["is_built_in"] is True

    # User modifies the template -> flag should clear
    patch_resp = await client.patch(
        f"/api/v1/templates/{template_id}",
        json={"name": "My Custom Version"},
        headers=auth_headers,
    )
    assert patch_resp.status_code == 200
    assert patch_resp.json()["is_built_in"] is False
    assert patch_resp.json()["name"] == "My Custom Version"
