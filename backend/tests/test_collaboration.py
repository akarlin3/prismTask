import pytest
from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from tests.conftest import TestSessionLocal


async def _create_goal_and_project(client: AsyncClient, headers: dict) -> tuple[int, int]:
    """Helper to create a goal and project, returns (goal_id, project_id)."""
    goal_resp = await client.post(
        "/api/v1/goals", json={"title": "Test Goal"}, headers=headers
    )
    goal_id = goal_resp.json()["id"]
    project_resp = await client.post(
        f"/api/v1/goals/{goal_id}/projects",
        json={"title": "Test Project"},
        headers=headers,
    )
    project_id = project_resp.json()["id"]
    return goal_id, project_id


@pytest.mark.asyncio
async def test_auto_owner_membership_on_project_creation(
    client: AsyncClient, auth_headers: dict
):
    """Creating a project should automatically create an owner membership."""
    from app.models import ProjectMember

    _, project_id = await _create_goal_and_project(client, auth_headers)

    # Query the database directly to verify the membership was created
    async with TestSessionLocal() as session:
        result = await session.execute(
            select(ProjectMember).where(ProjectMember.project_id == project_id)
        )
        members = result.scalars().all()
        assert len(members) == 1
        assert members[0].role == "owner"


@pytest.mark.asyncio
async def test_project_member_unique_constraint(
    client: AsyncClient, auth_headers: dict
):
    """A user cannot be added as a member of the same project twice."""
    from sqlalchemy.exc import IntegrityError

    from app.models import ProjectMember

    _, project_id = await _create_goal_and_project(client, auth_headers)

    # The owner membership already exists from project creation.
    # Try to insert a duplicate — should fail with IntegrityError.
    async with TestSessionLocal() as session:
        result = await session.execute(
            select(ProjectMember).where(ProjectMember.project_id == project_id)
        )
        existing = result.scalars().first()
        assert existing is not None

        duplicate = ProjectMember(
            project_id=project_id,
            user_id=existing.user_id,
            role="editor",
        )
        session.add(duplicate)
        with pytest.raises(IntegrityError):
            await session.flush()
        await session.rollback()


@pytest.mark.asyncio
async def test_invite_token_generation():
    """ProjectInvite.generate_token() should produce unique URL-safe tokens."""
    from app.models import ProjectInvite

    token1 = ProjectInvite.generate_token()
    token2 = ProjectInvite.generate_token()
    assert isinstance(token1, str)
    assert len(token1) > 32  # secrets.token_urlsafe(48) produces ~64 chars
    assert token1 != token2


@pytest.mark.asyncio
async def test_cascade_delete_removes_members(
    client: AsyncClient, auth_headers: dict
):
    """Deleting a project should cascade-delete its members."""
    from app.models import ProjectMember

    _, project_id = await _create_goal_and_project(client, auth_headers)

    # Verify membership exists
    async with TestSessionLocal() as session:
        result = await session.execute(
            select(ProjectMember).where(ProjectMember.project_id == project_id)
        )
        assert result.scalars().first() is not None

    # Delete the project
    resp = await client.delete(f"/api/v1/projects/{project_id}", headers=auth_headers)
    assert resp.status_code == 204

    # Verify membership was cascade-deleted
    async with TestSessionLocal() as session:
        result = await session.execute(
            select(ProjectMember).where(ProjectMember.project_id == project_id)
        )
        assert result.scalars().first() is None
