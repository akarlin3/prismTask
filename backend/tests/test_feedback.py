import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_create_bug_report_valid(client: AsyncClient):
    """POST valid bug report returns 201 with ID."""
    resp = await client.post(
        "/api/v1/feedback/bug-report",
        json={
            "category": "CRASH",
            "description": "App crashes when I open the settings screen and tap on appearance",
            "severity": "CRITICAL",
            "steps": ["Open app", "Go to settings", "Tap appearance"],
            "device_model": "Pixel 6",
            "device_manufacturer": "Google",
            "android_version": 33,
            "app_version": "1.3.2",
            "app_version_code": 93,
            "build_type": "debug",
            "current_screen": "Settings",
        },
    )
    assert resp.status_code == 201
    data = resp.json()
    assert "id" in data
    assert data["status"] == "submitted"
    assert data["message"] == "Thanks!"


@pytest.mark.asyncio
async def test_create_bug_report_missing_description(client: AsyncClient):
    """POST bug report without description returns 422."""
    resp = await client.post(
        "/api/v1/feedback/bug-report",
        json={
            "category": "CRASH",
            "severity": "MINOR",
        },
    )
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_create_bug_report_description_too_short(client: AsyncClient):
    """POST bug report with description < 10 chars returns 422."""
    resp = await client.post(
        "/api/v1/feedback/bug-report",
        json={
            "category": "CRASH",
            "description": "short",
            "severity": "MINOR",
        },
    )
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_list_bug_reports_admin(client: AsyncClient, auth_headers: dict):
    """GET bug reports as admin (user_id=1) returns list."""
    # First create a report
    await client.post(
        "/api/v1/feedback/bug-report",
        json={
            "category": "UI_GLITCH",
            "description": "Button misaligned on the task list screen",
            "severity": "MINOR",
        },
    )

    resp = await client.get("/api/v1/feedback/bug-reports", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert isinstance(data, list)
    assert len(data) >= 1


@pytest.mark.asyncio
async def test_list_bug_reports_non_admin(client: AsyncClient):
    """GET bug reports as non-admin returns 403."""
    # Register a second user who is NOT admin (user_id != 1)
    reg = await client.post(
        "/api/v1/auth/register",
        json={"email": "nonadmin@example.com", "name": "Non Admin", "password": "testpass123"},
    )
    token = reg.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    resp = await client.get("/api/v1/feedback/bug-reports", headers=headers)
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_update_bug_report_status(client: AsyncClient, auth_headers: dict):
    """PATCH status as admin updates correctly."""
    # Create a report
    create_resp = await client.post(
        "/api/v1/feedback/bug-report",
        json={
            "category": "PERFORMANCE",
            "description": "App is very slow when loading the task list with 500+ tasks",
            "severity": "MAJOR",
        },
    )
    report_id = create_resp.json()["id"]

    # Update status
    resp = await client.patch(
        f"/api/v1/feedback/bug-reports/{report_id}",
        json={"status": "ACKNOWLEDGED", "admin_notes": "Looking into this"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ACKNOWLEDGED"
    assert data["admin_notes"] == "Looking into this"


@pytest.mark.asyncio
async def test_filter_by_severity(client: AsyncClient, auth_headers: dict):
    """GET reports filtered by severity returns only matching."""
    # Create reports with different severities
    await client.post(
        "/api/v1/feedback/bug-report",
        json={
            "category": "CRASH",
            "description": "Critical crash in the billing module when purchasing",
            "severity": "CRITICAL",
        },
    )
    await client.post(
        "/api/v1/feedback/bug-report",
        json={
            "category": "UI_GLITCH",
            "description": "Minor alignment issue on the month view calendar header",
            "severity": "MINOR",
        },
    )

    resp = await client.get(
        "/api/v1/feedback/bug-reports?severity=CRITICAL", headers=auth_headers
    )
    assert resp.status_code == 200
    data = resp.json()
    assert all(r["severity"] == "CRITICAL" for r in data)
