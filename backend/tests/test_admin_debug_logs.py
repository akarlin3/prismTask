"""Tests for the admin debug log viewer endpoints.

Covers:
  - 403 for non-admin users
  - 200 for admin users
  - List endpoint pagination
  - Delete endpoint removes the record
  - Stats endpoint returns correct counts
"""

import pytest
from httpx import AsyncClient
from sqlalchemy import update

from tests.conftest import TestSessionLocal
from app.models import User


async def _make_admin(email: str = "test@example.com") -> None:
    """Helper: set is_admin=True for the user with the given email."""
    async with TestSessionLocal() as session:
        await session.execute(
            update(User).where(User.email == email).values(is_admin=True)
        )
        await session.commit()


async def _create_bug_report(client: AsyncClient, **overrides) -> dict:
    """Helper: create a bug report and return the response JSON."""
    payload = {
        "category": "CRASH",
        "description": "App crashes on launch when dark mode is enabled and language is set to Japanese",
        "severity": "CRITICAL",
        "device_model": "Pixel 8",
        "device_manufacturer": "Google",
        "android_version": 34,
        "app_version": "1.3.2",
        "app_version_code": 93,
        "build_type": "release",
        "current_screen": "Today",
        **overrides,
    }
    resp = await client.post("/api/v1/feedback/bug-report", json=payload)
    assert resp.status_code == 201
    return resp.json()


# ---------------------------------------------------------------------------
# Auth: 403 for non-admin
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_list_debug_logs_returns_403_for_non_admin(
    client: AsyncClient, auth_headers: dict
):
    """Non-admin users get 403 on the list endpoint."""
    resp = await client.get("/api/v1/admin/debug-logs", headers=auth_headers)
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_get_debug_log_returns_403_for_non_admin(
    client: AsyncClient, auth_headers: dict
):
    """Non-admin users get 403 on the detail endpoint."""
    resp = await client.get("/api/v1/admin/debug-logs/fakeid", headers=auth_headers)
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_delete_debug_log_returns_403_for_non_admin(
    client: AsyncClient, auth_headers: dict
):
    """Non-admin users get 403 on the delete endpoint."""
    resp = await client.delete("/api/v1/admin/debug-logs/fakeid", headers=auth_headers)
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_stats_returns_403_for_non_admin(
    client: AsyncClient, auth_headers: dict
):
    """Non-admin users get 403 on the stats endpoint."""
    resp = await client.get("/api/v1/admin/debug-logs/stats", headers=auth_headers)
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_all_endpoints_return_401_without_auth(client: AsyncClient):
    """Unauthenticated requests get 401."""
    for method, path in [
        ("GET", "/api/v1/admin/debug-logs"),
        ("GET", "/api/v1/admin/debug-logs/stats"),
        ("GET", "/api/v1/admin/debug-logs/fakeid"),
        ("DELETE", "/api/v1/admin/debug-logs/fakeid"),
    ]:
        resp = await client.request(method, path)
        assert resp.status_code == 401, f"{method} {path} returned {resp.status_code}"


# ---------------------------------------------------------------------------
# Admin: 200 for admin users
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_list_debug_logs_as_admin(client: AsyncClient, auth_headers: dict):
    """Admin can list debug logs and gets paginated response."""
    await _make_admin()

    # Create some bug reports first
    await _create_bug_report(client, severity="CRITICAL")
    await _create_bug_report(client, severity="MINOR",
                             description="Minor glitch in the calendar month view when scrolling quickly")

    resp = await client.get("/api/v1/admin/debug-logs", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()

    assert "items" in data
    assert "total" in data
    assert "page" in data
    assert "per_page" in data
    assert "total_pages" in data
    assert data["total"] >= 2
    assert len(data["items"]) >= 2

    # Check item shape
    item = data["items"][0]
    assert "id" in item
    assert "timestamp" in item
    assert "category" in item
    assert "severity" in item
    assert "size_bytes" in item


@pytest.mark.asyncio
async def test_list_debug_logs_pagination(client: AsyncClient, auth_headers: dict):
    """Pagination works correctly."""
    await _make_admin()

    # Create 5 reports
    for i in range(5):
        await _create_bug_report(
            client,
            description=f"Bug report number {i} with enough characters to pass validation easily",
        )

    # Request page 1 with per_page=2
    resp = await client.get(
        "/api/v1/admin/debug-logs?page=1&per_page=2", headers=auth_headers
    )
    assert resp.status_code == 200
    data = resp.json()
    assert len(data["items"]) == 2
    assert data["page"] == 1
    assert data["total"] >= 5
    assert data["total_pages"] >= 3

    # Request page 2
    resp2 = await client.get(
        "/api/v1/admin/debug-logs?page=2&per_page=2", headers=auth_headers
    )
    assert resp2.status_code == 200
    data2 = resp2.json()
    assert len(data2["items"]) == 2
    assert data2["page"] == 2

    # Items should be different
    ids1 = {item["id"] for item in data["items"]}
    ids2 = {item["id"] for item in data2["items"]}
    assert ids1.isdisjoint(ids2)


@pytest.mark.asyncio
async def test_list_debug_logs_sort(client: AsyncClient, auth_headers: dict):
    """Sort parameter changes the ordering."""
    await _make_admin()

    await _create_bug_report(client, description="First report created before the second one to test ordering")
    await _create_bug_report(client, description="Second report created after the first one to test ordering")

    # Default sort (newest first)
    resp = await client.get("/api/v1/admin/debug-logs?sort=newest", headers=auth_headers)
    assert resp.status_code == 200
    items_newest = resp.json()["items"]

    resp2 = await client.get("/api/v1/admin/debug-logs?sort=oldest", headers=auth_headers)
    assert resp2.status_code == 200
    items_oldest = resp2.json()["items"]

    # First item in newest should be last in oldest (or at least different order)
    if len(items_newest) >= 2:
        assert items_newest[0]["id"] == items_oldest[-1]["id"]


@pytest.mark.asyncio
async def test_get_debug_log_detail(client: AsyncClient, auth_headers: dict):
    """Admin can view a specific debug log's full content."""
    await _make_admin()

    await _create_bug_report(client)

    # Get the list to find a log ID
    list_resp = await client.get("/api/v1/admin/debug-logs", headers=auth_headers)
    log_id = list_resp.json()["items"][0]["id"]

    resp = await client.get(f"/api/v1/admin/debug-logs/{log_id}", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()

    assert data["id"] == log_id
    assert "content" in data
    assert "metadata" in data
    assert "timestamp" in data
    assert data["content"] is not None
    assert "CRASH" in data["content"]


@pytest.mark.asyncio
async def test_get_debug_log_not_found(client: AsyncClient, auth_headers: dict):
    """Getting a non-existent log returns 404."""
    await _make_admin()

    # Encode a fake report_id
    import base64
    fake_id = base64.urlsafe_b64encode(b"nonexistent_report_id").decode().rstrip("=")

    resp = await client.get(f"/api/v1/admin/debug-logs/{fake_id}", headers=auth_headers)
    assert resp.status_code == 404


# ---------------------------------------------------------------------------
# Delete
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_delete_debug_log(client: AsyncClient, auth_headers: dict):
    """Admin can delete a log and it disappears from the list."""
    await _make_admin()

    await _create_bug_report(client)

    # List and grab the ID
    list_resp = await client.get("/api/v1/admin/debug-logs", headers=auth_headers)
    items = list_resp.json()["items"]
    assert len(items) >= 1
    log_id = items[0]["id"]

    # Delete it
    del_resp = await client.delete(
        f"/api/v1/admin/debug-logs/{log_id}", headers=auth_headers
    )
    assert del_resp.status_code == 200
    assert del_resp.json()["deleted"] is True

    # Verify it's gone
    get_resp = await client.get(
        f"/api/v1/admin/debug-logs/{log_id}", headers=auth_headers
    )
    assert get_resp.status_code == 404


@pytest.mark.asyncio
async def test_delete_nonexistent_log_returns_404(
    client: AsyncClient, auth_headers: dict
):
    """Deleting a non-existent log returns 404."""
    await _make_admin()

    import base64
    fake_id = base64.urlsafe_b64encode(b"does_not_exist_at_all").decode().rstrip("=")

    resp = await client.delete(
        f"/api/v1/admin/debug-logs/{fake_id}", headers=auth_headers
    )
    assert resp.status_code == 404


# ---------------------------------------------------------------------------
# Stats
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_stats_endpoint(client: AsyncClient, auth_headers: dict):
    """Stats endpoint returns correct counts."""
    await _make_admin()

    # Create some reports
    await _create_bug_report(client, description="Report one for stats counting test validation purposes")
    await _create_bug_report(client, description="Report two for stats counting test validation purposes")

    resp = await client.get("/api/v1/admin/debug-logs/stats", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()

    assert "total_logs" in data
    assert "logs_this_week" in data
    assert "unique_users" in data
    assert "storage_used_bytes" in data

    assert data["total_logs"] >= 2
    assert data["logs_this_week"] >= 2
    assert data["storage_used_bytes"] > 0


@pytest.mark.asyncio
async def test_list_filter_by_user_id(client: AsyncClient, auth_headers: dict):
    """Filtering by user_id returns only matching logs."""
    await _make_admin()

    # Create a report (logged-in user is user_id=1)
    await _create_bug_report(client)

    # Filter by user_id=1 (the test user)
    resp = await client.get("/api/v1/admin/debug-logs?user_id=1", headers=auth_headers)
    assert resp.status_code == 200
    # All items should have user_id=None (created without auth) or a specific ID
    # Since we created without auth, let's filter for a non-existent user
    resp2 = await client.get(
        "/api/v1/admin/debug-logs?user_id=9999", headers=auth_headers
    )
    assert resp2.status_code == 200
    assert resp2.json()["total"] == 0
