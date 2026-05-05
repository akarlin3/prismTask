"""Tests for the beta-tester unlock-code redemption endpoint and the
``has_active_beta_pro`` effective_tier integration.
"""
from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient
from sqlalchemy import select

from app.models import BetaCode, BetaCodeRedemption, User
from tests.conftest import TestSessionLocal


async def _make_code(
    code: str = "EARLY-BIRD-2026",
    *,
    grants_pro_until: datetime | None = None,
    valid_from: datetime | None = None,
    valid_until: datetime | None = None,
    max_redemptions: int | None = None,
    revoked_at: datetime | None = None,
    redemption_count: int = 0,
) -> None:
    async with TestSessionLocal() as session:
        session.add(
            BetaCode(
                code=code,
                description="closed beta cohort",
                valid_from=valid_from or datetime.now(timezone.utc) - timedelta(days=1),
                valid_until=valid_until,
                grants_pro_until=grants_pro_until,
                max_redemptions=max_redemptions,
                revoked_at=revoked_at,
                redemption_count=redemption_count,
            )
        )
        await session.commit()


async def _user_id(email: str = "test@example.com") -> int:
    async with TestSessionLocal() as session:
        result = await session.execute(select(User).where(User.email == email))
        return result.scalar_one().id


# ---------------------------------------------------------------------------
# Happy paths
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_redeem_perpetual_code_grants_pro(client: AsyncClient, auth_headers: dict):
    await _make_code(grants_pro_until=None)
    resp = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["granted"] is True
    assert data["pro_until"] is None

    me = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert me.status_code == 200
    assert me.json()["effective_tier"] == "PRO"


@pytest.mark.asyncio
async def test_redeem_with_ttl_grants_pro_until(client: AsyncClient, auth_headers: dict):
    until = datetime.now(timezone.utc) + timedelta(days=30)
    await _make_code(grants_pro_until=until)

    resp = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["granted"] is True
    assert data["pro_until"] is not None

    me = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert me.json()["effective_tier"] == "PRO"


@pytest.mark.asyncio
async def test_expired_redemption_no_longer_grants_pro(client: AsyncClient, auth_headers: dict):
    """If the redemption snapshot's grants_pro_until is in the past,
    /auth/me drops back to FREE even though the row still exists."""
    past = datetime.now(timezone.utc) - timedelta(days=1)
    await _make_code(grants_pro_until=past)
    user_id = await _user_id()
    async with TestSessionLocal() as session:
        session.add(
            BetaCodeRedemption(
                code="EARLY-BIRD-2026",
                user_id=user_id,
                grants_pro_until=past,
            )
        )
        await session.commit()

    me = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert me.status_code == 200
    assert me.json()["effective_tier"] == "FREE"


# ---------------------------------------------------------------------------
# Error paths
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_redeem_unknown_code_400(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "NOPE"}
    )
    assert resp.status_code == 400
    assert "does not exist" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_redeem_revoked_code_400(client: AsyncClient, auth_headers: dict):
    await _make_code(revoked_at=datetime.now(timezone.utc) - timedelta(hours=1))
    resp = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert resp.status_code == 400
    assert "revoked" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_redeem_outside_valid_window_400(client: AsyncClient, auth_headers: dict):
    past = datetime.now(timezone.utc) - timedelta(days=2)
    await _make_code(valid_until=past)
    resp = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert resp.status_code == 400
    assert "expired" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_redeem_not_yet_valid_400(client: AsyncClient, auth_headers: dict):
    future = datetime.now(timezone.utc) + timedelta(days=2)
    await _make_code(valid_from=future)
    resp = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_redeem_double_redemption_409(client: AsyncClient, auth_headers: dict):
    await _make_code()
    first = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert first.status_code == 200
    second = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert second.status_code == 409
    assert "already redeemed" in second.json()["detail"].lower()


@pytest.mark.asyncio
async def test_redeem_max_redemptions_410(client: AsyncClient, auth_headers: dict):
    """Pre-stuff redemption_count to the cap and verify a fresh user is
    rejected with 410."""
    await _make_code(max_redemptions=1, redemption_count=1)
    resp = await client.post(
        "/api/v1/beta/redeem", headers=auth_headers, json={"code": "EARLY-BIRD-2026"}
    )
    assert resp.status_code == 410


@pytest.mark.asyncio
async def test_redeem_requires_auth(client: AsyncClient):
    resp = await client.post(
        "/api/v1/beta/redeem", json={"code": "EARLY-BIRD-2026"}
    )
    assert resp.status_code in (401, 403)


# ---------------------------------------------------------------------------
# effective_tier interaction
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_admin_effective_tier_unaffected_by_expired_beta(
    client: AsyncClient, auth_headers: dict
):
    """Admin still gets PRO even when their beta-code grant has expired."""
    # Make user admin
    from sqlalchemy import update as sqla_update
    async with TestSessionLocal() as session:
        await session.execute(
            sqla_update(User).where(User.email == "test@example.com").values(is_admin=True)
        )
        await session.commit()

    past = datetime.now(timezone.utc) - timedelta(days=1)
    await _make_code(grants_pro_until=past)
    user_id = await _user_id()
    async with TestSessionLocal() as session:
        session.add(
            BetaCodeRedemption(
                code="EARLY-BIRD-2026", user_id=user_id, grants_pro_until=past
            )
        )
        await session.commit()

    me = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert me.status_code == 200
    body = me.json()
    assert body["is_admin"] is True
    assert body["effective_tier"] == "PRO"
