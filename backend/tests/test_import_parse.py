"""Tests for POST /api/v1/tasks/parse-import.

The _call_haiku helper is mocked at the router level so tests run without a
real Anthropic API key or network connection.
"""
from unittest.mock import patch

import pytest
from httpx import AsyncClient

# ---------------------------------------------------------------------------
# Fixtures / shared data
# ---------------------------------------------------------------------------

_SIMPLE_TODO_TEXT = """
const TODOS = [
  { text: "Buy groceries", done: false },
  { text: "Call dentist", done: true },
];
"""

_HAIKU_IMPORT_RESPONSE = (
    '{"name": "Todos", "items": ['
    '{"title": "Buy groceries", "description": null, "dueDate": null, "priority": 0, "completed": false, "subtasks": []},'
    '{"title": "Call dentist", "description": null, "dueDate": null, "priority": 0, "completed": true, "subtasks": []}'
    "]}"
)

_HAIKU_EMPTY_RESPONSE = '{"name": null, "items": []}'


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_parse_import_requires_auth(client: AsyncClient):
    """Endpoint rejects unauthenticated requests."""
    resp = await client.post(
        "/api/v1/tasks/parse-import",
        json={"content": _SIMPLE_TODO_TEXT},
    )
    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_parse_import_success(client: AsyncClient, auth_headers: dict):
    """Happy path: authenticated request returns parsed items."""
    with (
        patch.dict("os.environ", {"ANTHROPIC_API_KEY": "test-key"}),
        patch("app.routers.tasks._call_haiku", return_value=_HAIKU_IMPORT_RESPONSE),
    ):
        resp = await client.post(
            "/api/v1/tasks/parse-import",
            json={"content": _SIMPLE_TODO_TEXT},
            headers=auth_headers,
        )

    assert resp.status_code == 200
    data = resp.json()
    assert data["name"] == "Todos"
    assert len(data["items"]) == 2
    assert data["items"][0]["title"] == "Buy groceries"
    assert data["items"][0]["completed"] is False
    assert data["items"][1]["title"] == "Call dentist"
    assert data["items"][1]["completed"] is True


@pytest.mark.asyncio
async def test_parse_import_subtasks(client: AsyncClient, auth_headers: dict):
    """Items with nested subtasks are returned correctly."""
    haiku_response = (
        '{"name": "Project", "items": [{"title": "Parent task", "description": null, '
        '"dueDate": null, "priority": 0, "completed": false, "subtasks": ['
        '{"title": "Subtask A", "description": null, "dueDate": null, "priority": 0, '
        '"completed": false, "subtasks": []}]}]}'
    )
    with (
        patch.dict("os.environ", {"ANTHROPIC_API_KEY": "test-key"}),
        patch("app.routers.tasks._call_haiku", return_value=haiku_response),
    ):
        resp = await client.post(
            "/api/v1/tasks/parse-import",
            json={"content": "some content"},
            headers=auth_headers,
        )

    assert resp.status_code == 200
    data = resp.json()
    assert len(data["items"]) == 1
    assert len(data["items"][0]["subtasks"]) == 1
    assert data["items"][0]["subtasks"][0]["title"] == "Subtask A"


@pytest.mark.asyncio
async def test_parse_import_no_api_key(client: AsyncClient, auth_headers: dict):
    """503 when the server has no ANTHROPIC_API_KEY configured."""
    with (
        patch.dict("os.environ", {}, clear=True),
        patch("app.routers.tasks.settings") as mock_settings,
    ):
        mock_settings.ANTHROPIC_API_KEY = ""
        resp = await client.post(
            "/api/v1/tasks/parse-import",
            json={"content": _SIMPLE_TODO_TEXT},
            headers=auth_headers,
        )

    assert resp.status_code == 503
    assert "ANTHROPIC_API_KEY" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_parse_import_claude_failure(client: AsyncClient, auth_headers: dict):
    """502 when _call_haiku raises an exception."""
    with (
        patch.dict("os.environ", {"ANTHROPIC_API_KEY": "test-key"}),
        patch("app.routers.tasks._call_haiku", side_effect=RuntimeError("upstream error")),
    ):
        resp = await client.post(
            "/api/v1/tasks/parse-import",
            json={"content": _SIMPLE_TODO_TEXT},
            headers=auth_headers,
        )

    assert resp.status_code == 502
    assert "AI parsing failed" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_parse_import_rate_limit(client: AsyncClient, auth_headers: dict):
    """After 10 calls in the rate-limit window the endpoint returns 429."""
    from app.middleware.rate_limit import import_parse_rate_limiter

    # Reset the limiter so prior tests don't affect this one
    import_parse_rate_limiter._requests.clear()

    with (
        patch.dict("os.environ", {"ANTHROPIC_API_KEY": "test-key"}),
        patch("app.routers.tasks._call_haiku", return_value=_HAIKU_EMPTY_RESPONSE),
    ):
        for _ in range(10):
            r = await client.post(
                "/api/v1/tasks/parse-import",
                json={"content": "task"},
                headers=auth_headers,
            )
            assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text}"

        # 11th call must be rejected
        r = await client.post(
            "/api/v1/tasks/parse-import",
            json={"content": "task"},
            headers=auth_headers,
        )

    assert r.status_code == 429
    assert "Rate limit" in r.json()["detail"]
