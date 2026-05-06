from unittest.mock import patch

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_register(client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/register",
        json={"email": "new@example.com", "name": "New User", "password": "pass123"},
    )
    assert resp.status_code == 201
    data = resp.json()
    assert "access_token" in data
    assert "refresh_token" in data
    assert data["token_type"] == "bearer"


@pytest.mark.asyncio
async def test_register_duplicate_email(client: AsyncClient):
    await client.post(
        "/api/v1/auth/register",
        json={"email": "dup@example.com", "name": "User 1", "password": "pass123"},
    )
    resp = await client.post(
        "/api/v1/auth/register",
        json={"email": "dup@example.com", "name": "User 2", "password": "pass456"},
    )
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_login(client: AsyncClient):
    await client.post(
        "/api/v1/auth/register",
        json={"email": "login@example.com", "name": "Login User", "password": "pass123"},
    )
    resp = await client.post(
        "/api/v1/auth/login",
        json={"email": "login@example.com", "password": "pass123"},
    )
    assert resp.status_code == 200
    assert "access_token" in resp.json()


@pytest.mark.asyncio
async def test_login_wrong_password(client: AsyncClient):
    await client.post(
        "/api/v1/auth/register",
        json={"email": "wrong@example.com", "name": "Wrong", "password": "pass123"},
    )
    resp = await client.post(
        "/api/v1/auth/login",
        json={"email": "wrong@example.com", "password": "wrongpass"},
    )
    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_refresh_token(client: AsyncClient):
    reg = await client.post(
        "/api/v1/auth/register",
        json={"email": "refresh@example.com", "name": "Refresh", "password": "pass123"},
    )
    refresh_token = reg.json()["refresh_token"]
    resp = await client.post(
        "/api/v1/auth/refresh",
        json={"refresh_token": refresh_token},
    )
    assert resp.status_code == 200
    assert "access_token" in resp.json()


@pytest.mark.asyncio
async def test_protected_route_no_token(client: AsyncClient):
    resp = await client.get("/api/v1/auth/me")
    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_protected_route_with_token(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/auth/me", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["email"] == "test@example.com"
    assert data["name"] == "Test User"


# --- Firebase token auth tests ---

FAKE_FIREBASE_CLAIMS = {
    "uid": "firebase-uid-123",
    "email": "firebase@example.com",
    "name": "Firebase User",
}


@pytest.mark.asyncio
@patch("app.routers.auth.verify_firebase_token", return_value=FAKE_FIREBASE_CLAIMS)
async def test_firebase_login_creates_user(mock_verify, client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/firebase",
        json={"firebase_token": "fake-id-token"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert "access_token" in data
    assert "refresh_token" in data
    assert data["token_type"] == "bearer"


@pytest.mark.asyncio
@patch("app.routers.auth.verify_firebase_token", return_value=FAKE_FIREBASE_CLAIMS)
async def test_firebase_login_returns_same_user_on_repeat(mock_verify, client: AsyncClient):
    resp1 = await client.post(
        "/api/v1/auth/firebase",
        json={"firebase_token": "fake-id-token"},
    )
    resp2 = await client.post(
        "/api/v1/auth/firebase",
        json={"firebase_token": "fake-id-token"},
    )
    assert resp1.status_code == 200
    assert resp2.status_code == 200

    # Verify it's the same user by checking /me with each token
    headers1 = {"Authorization": f"Bearer {resp1.json()['access_token']}"}
    headers2 = {"Authorization": f"Bearer {resp2.json()['access_token']}"}
    me1 = await client.get("/api/v1/auth/me", headers=headers1)
    me2 = await client.get("/api/v1/auth/me", headers=headers2)
    assert me1.json()["id"] == me2.json()["id"]


@pytest.mark.asyncio
@patch("app.routers.auth.verify_firebase_token", return_value=FAKE_FIREBASE_CLAIMS)
async def test_firebase_login_links_existing_email_account(mock_verify, client: AsyncClient):
    """If a user registered via email/password, Firebase login should link to that account."""
    await client.post(
        "/api/v1/auth/register",
        json={"email": "firebase@example.com", "name": "Existing User", "password": "pass123"},
    )
    resp = await client.post(
        "/api/v1/auth/firebase",
        json={"firebase_token": "fake-id-token"},
    )
    assert resp.status_code == 200

    # Verify it linked to the existing account (same email, name preserved)
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = await client.get("/api/v1/auth/me", headers=headers)
    assert me.json()["email"] == "firebase@example.com"
    assert me.json()["name"] == "Existing User"


@pytest.mark.asyncio
@patch("app.routers.auth.verify_firebase_token", return_value=None)
async def test_firebase_login_invalid_token(mock_verify, client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/firebase",
        json={"firebase_token": "bad-token"},
    )
    assert resp.status_code == 401
    assert resp.json()["detail"] == "Invalid Firebase token"


@pytest.mark.asyncio
@patch(
    "app.routers.auth.verify_firebase_token",
    return_value={"uid": "no-email-uid", "name": "No Email"},
)
async def test_firebase_login_no_email(mock_verify, client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/firebase",
        json={"firebase_token": "fake-id-token"},
    )
    assert resp.status_code == 400
    assert resp.json()["detail"] == "Firebase account has no email"


# --- Admin allowlist tests ---


@pytest.mark.asyncio
async def test_register_promotes_allowlisted_email_to_admin(client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/register",
        json={
            "email": "avery.karlin@gmail.com",
            "name": "Avery",
            "password": "pass123",
        },
    )
    assert resp.status_code == 201
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = await client.get("/api/v1/auth/me", headers=headers)
    assert me.status_code == 200
    assert me.json()["is_admin"] is True


@pytest.mark.asyncio
async def test_register_does_not_promote_non_allowlisted_email(client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/register",
        json={"email": "rando@example.com", "name": "Rando", "password": "pass123"},
    )
    assert resp.status_code == 201
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = await client.get("/api/v1/auth/me", headers=headers)
    assert me.json()["is_admin"] is False


@pytest.mark.asyncio
async def test_login_retroactively_promotes_allowlisted_email(client: AsyncClient):
    """A user already in the DB without is_admin gets promoted on next login
    once their email is added to the allowlist."""
    from sqlalchemy import update

    from app.models import User
    from tests.conftest import TestSessionLocal

    await client.post(
        "/api/v1/auth/register",
        json={
            "email": "avery.karlin@gmail.com",
            "name": "Avery",
            "password": "pass123",
        },
    )
    # Simulate "user existed before being added to the allowlist"
    async with TestSessionLocal() as session:
        await session.execute(
            update(User)
            .where(User.email == "avery.karlin@gmail.com")
            .values(is_admin=False)
        )
        await session.commit()

    resp = await client.post(
        "/api/v1/auth/login",
        json={"email": "avery.karlin@gmail.com", "password": "pass123"},
    )
    assert resp.status_code == 200
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = await client.get("/api/v1/auth/me", headers=headers)
    assert me.json()["is_admin"] is True


@pytest.mark.asyncio
async def test_admin_email_match_is_case_insensitive(client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/register",
        json={
            "email": "Avery.Karlin@Gmail.com",
            "name": "Avery",
            "password": "pass123",
        },
    )
    assert resp.status_code == 201
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = await client.get("/api/v1/auth/me", headers=headers)
    assert me.json()["is_admin"] is True


@pytest.mark.asyncio
@patch(
    "app.routers.auth.verify_firebase_token",
    return_value={
        "uid": "firebase-admin-uid",
        "email": "avery.karlin@gmail.com",
        "name": "Avery",
    },
)
async def test_firebase_login_promotes_allowlisted_email(mock_verify, client: AsyncClient):
    resp = await client.post(
        "/api/v1/auth/firebase",
        json={"firebase_token": "fake-id-token"},
    )
    assert resp.status_code == 200
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = await client.get("/api/v1/auth/me", headers=headers)
    assert me.json()["is_admin"] is True
