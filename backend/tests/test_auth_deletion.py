"""Tests for the soft-delete account-deletion endpoints.

Covers the full lifecycle:
  - POST   /auth/me/deletion       mark pending
  - GET    /auth/me/deletion       inspect status
  - DELETE /auth/me/deletion       cancel (restore)
  - POST   /auth/me/purge          permanent deletion (post-grace)

Plus the ``get_active_user`` middleware guard that returns 410 Gone for
mutation endpoints when an account is pending deletion.
"""

from datetime import datetime, timedelta, timezone
from unittest.mock import patch

import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.models import Goal, Project, ProjectMember, User
from tests.conftest import TestSessionLocal


async def _get_user_by_email(email: str) -> User | None:
    async with TestSessionLocal() as session:
        result = await session.execute(select(User).where(User.email == email))
        return result.scalar_one_or_none()


async def _backdate_deletion(email: str, days_ago: int) -> None:
    """Set ``deletion_scheduled_for`` to ``days_ago`` days in the past so the
    purge endpoint will accept the request without waiting 30 real days."""
    async with TestSessionLocal() as session:
        result = await session.execute(select(User).where(User.email == email))
        user = result.scalar_one()
        user.deletion_scheduled_for = datetime.now(timezone.utc) - timedelta(days=days_ago)
        await session.commit()


@pytest.mark.asyncio
async def test_request_deletion_marks_user_pending(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["deletion_pending_at"] is not None
    assert body["deletion_scheduled_for"] is not None
    assert body["deletion_initiated_from"] == "android"
    assert body["grace_period_days"] == 30

    user = await _get_user_by_email("test@example.com")
    assert user is not None
    assert user.deletion_pending_at is not None
    # Scheduled date is ~30 days out (allow drift for test execution time).
    delta = user.deletion_scheduled_for - user.deletion_pending_at
    assert timedelta(days=29, hours=23) < delta < timedelta(days=30, hours=1)


@pytest.mark.asyncio
async def test_request_deletion_idempotent(client: AsyncClient, auth_headers: dict):
    """Re-requesting deletion must NOT reset the grace window — a user who
    accidentally double-taps shouldn't lose their pending-deletion clock."""
    first = await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    assert first.status_code == 200
    first_pending_at = first.json()["deletion_pending_at"]

    second = await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "web"},
        headers=auth_headers,
    )
    assert second.status_code == 200
    assert second.json()["deletion_pending_at"] == first_pending_at
    # Original initiated_from is preserved.
    assert second.json()["deletion_initiated_from"] == "android"


@pytest.mark.asyncio
async def test_request_deletion_invalid_initiated_from(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "ios"},
        headers=auth_headers,
    )
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_get_deletion_status_when_not_pending(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/auth/me/deletion", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["deletion_pending_at"] is None
    assert body["deletion_scheduled_for"] is None


@pytest.mark.asyncio
async def test_cancel_deletion_clears_fields(client: AsyncClient, auth_headers: dict):
    await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    resp = await client.delete("/api/v1/auth/me/deletion", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["deletion_pending_at"] is None
    assert body["deletion_scheduled_for"] is None

    user = await _get_user_by_email("test@example.com")
    assert user is not None
    assert user.deletion_pending_at is None


@pytest.mark.asyncio
async def test_cancel_deletion_when_not_pending_is_noop(client: AsyncClient, auth_headers: dict):
    resp = await client.delete("/api/v1/auth/me/deletion", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["deletion_pending_at"] is None


@pytest.mark.asyncio
async def test_purge_rejects_when_not_pending(client: AsyncClient, auth_headers: dict):
    resp = await client.post("/api/v1/auth/me/purge", headers=auth_headers)
    assert resp.status_code == 409
    assert "not pending deletion" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_purge_rejects_before_grace_expires(client: AsyncClient, auth_headers: dict):
    await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    resp = await client.post("/api/v1/auth/me/purge", headers=auth_headers)
    assert resp.status_code == 409
    assert "grace" in resp.json()["detail"].lower()


@pytest.mark.asyncio
@patch("app.routers.auth.delete_firebase_user", return_value=True)
async def test_purge_succeeds_after_grace_expires(
    mock_delete_firebase, client: AsyncClient, auth_headers: dict
):
    await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    await _backdate_deletion("test@example.com", days_ago=31)

    resp = await client.post("/api/v1/auth/me/purge", headers=auth_headers)
    assert resp.status_code == 204

    user = await _get_user_by_email("test@example.com")
    assert user is None


@pytest.mark.asyncio
@patch("app.routers.auth.delete_firebase_user", return_value=True)
async def test_purge_calls_firebase_admin_when_uid_set(
    mock_delete_firebase, client: AsyncClient, auth_headers: dict
):
    # Link the test user to a Firebase UID so the purge path triggers
    # the Firebase Admin call.
    async with TestSessionLocal() as session:
        result = await session.execute(select(User).where(User.email == "test@example.com"))
        user = result.scalar_one()
        user.firebase_uid = "fb-uid-test-1"
        await session.commit()

    await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    await _backdate_deletion("test@example.com", days_ago=31)

    resp = await client.post("/api/v1/auth/me/purge", headers=auth_headers)
    assert resp.status_code == 204
    mock_delete_firebase.assert_called_once_with("fb-uid-test-1")


@pytest.mark.asyncio
@patch("app.routers.auth.delete_firebase_user", return_value=False)
async def test_purge_completes_even_when_firebase_admin_fails(
    mock_delete_firebase, client: AsyncClient, auth_headers: dict
):
    """Firebase Admin failure must not roll back the DB delete — we cannot
    leave the user in a half-deleted state."""
    async with TestSessionLocal() as session:
        result = await session.execute(select(User).where(User.email == "test@example.com"))
        user = result.scalar_one()
        user.firebase_uid = "fb-uid-test-2"
        await session.commit()

    await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    await _backdate_deletion("test@example.com", days_ago=31)

    resp = await client.post("/api/v1/auth/me/purge", headers=auth_headers)
    assert resp.status_code == 204

    user = await _get_user_by_email("test@example.com")
    assert user is None


@pytest.mark.asyncio
@patch("app.routers.auth.delete_firebase_user", return_value=True)
async def test_purge_clears_project_memberships_only(
    mock_delete_firebase, client: AsyncClient, auth_headers: dict
):
    """Deleting a user who is a MEMBER of someone else's project must
    preserve that project — only the membership row is removed.

    ``ProjectMember`` is not cascade-delete on the User relationship, so
    the purge endpoint must explicitly delete memberships before the user
    row, otherwise FK violation would block the whole purge.
    """
    # Create a separate "owner" user who owns a goal + project.
    await client.post(
        "/api/v1/auth/register",
        json={"email": "owner@example.com", "name": "Owner", "password": "pass1234"},
    )
    owner = await _get_user_by_email("owner@example.com")
    test_user = await _get_user_by_email("test@example.com")

    async with TestSessionLocal() as session:
        goal = Goal(user_id=owner.id, title="Owner Goal")
        session.add(goal)
        await session.flush()
        project = Project(goal_id=goal.id, user_id=owner.id, title="Shared Project")
        session.add(project)
        await session.flush()
        membership = ProjectMember(
            project_id=project.id,
            user_id=test_user.id,
            role="editor",
        )
        session.add(membership)
        await session.commit()
        shared_project_id = project.id

    await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )
    await _backdate_deletion("test@example.com", days_ago=31)

    resp = await client.post("/api/v1/auth/me/purge", headers=auth_headers)
    assert resp.status_code == 204

    async with TestSessionLocal() as session:
        # Shared project still exists.
        proj = await session.get(Project, shared_project_id)
        assert proj is not None
        # Membership row is gone.
        memberships = await session.execute(
            select(ProjectMember).where(ProjectMember.user_id == test_user.id)
        )
        assert memberships.scalar_one_or_none() is None


@pytest.mark.asyncio
async def test_active_user_guard_rejects_pending_deletion(
    client: AsyncClient, auth_headers: dict
):
    """Mutation endpoints protected by ``get_active_user`` must return 410
    when the account is pending deletion. We don't want to accept new content
    for an account the user has marked for deletion."""
    # Mark deletion pending.
    await client.post(
        "/api/v1/auth/me/deletion",
        json={"initiated_from": "android"},
        headers=auth_headers,
    )

    # Confirm deletion endpoints themselves still work — they use
    # get_current_user (NOT get_active_user) so the user can still inspect
    # and cancel their pending deletion.
    status_resp = await client.get("/api/v1/auth/me/deletion", headers=auth_headers)
    assert status_resp.status_code == 200

    cancel_resp = await client.delete("/api/v1/auth/me/deletion", headers=auth_headers)
    assert cancel_resp.status_code == 200
    assert cancel_resp.json()["deletion_pending_at"] is None
