"""Tests for admin users automatically receiving the PRO tier.

Covers:
  - User.effective_tier property returns PRO for admins
  - User.effective_tier returns stored tier for non-admins
  - GET /auth/me includes effective_tier in response
  - Admin effective_tier is PRO regardless of stored tier
"""

import pytest
from httpx import AsyncClient
from sqlalchemy import update

from app.models import User
from tests.conftest import TestSessionLocal


async def _make_admin(email: str = "test@example.com") -> None:
    """Set is_admin=True for the user with the given email."""
    async with TestSessionLocal() as session:
        await session.execute(
            update(User).where(User.email == email).values(is_admin=True)
        )
        await session.commit()


async def _set_tier(email: str, tier: str) -> None:
    """Set tier for the user with the given email."""
    async with TestSessionLocal() as session:
        await session.execute(
            update(User).where(User.email == email).values(tier=tier)
        )
        await session.commit()


# ---------------------------------------------------------------------------
# effective_tier property
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_non_admin_effective_tier_matches_stored_tier(
    client: AsyncClient, auth_headers: dict
):
    """Non-admin users get their stored tier as effective_tier."""
    resp = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["is_admin"] is False
    assert data["effective_tier"] == "FREE"
    assert data["tier"] == "FREE"


@pytest.mark.asyncio
async def test_admin_effective_tier_is_pro(
    client: AsyncClient, auth_headers: dict
):
    """Admin users always get PRO as their effective_tier."""
    await _make_admin()
    resp = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["is_admin"] is True
    assert data["effective_tier"] == "PRO"


@pytest.mark.asyncio
async def test_admin_effective_tier_pro_regardless_of_stored_tier(
    client: AsyncClient, auth_headers: dict
):
    """Admin effective_tier is PRO even when stored tier is FREE."""
    await _set_tier("test@example.com", "FREE")
    await _make_admin()
    resp = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["tier"] == "FREE"
    assert data["effective_tier"] == "PRO"


@pytest.mark.asyncio
async def test_non_admin_pro_tier(
    client: AsyncClient, auth_headers: dict
):
    """Non-admin PRO user gets PRO as effective_tier."""
    await _set_tier("test@example.com", "PRO")
    resp = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["is_admin"] is False
    assert data["effective_tier"] == "PRO"
    assert data["tier"] == "PRO"
