import asyncio
from typing import AsyncGenerator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.database import get_db
from app.main import app
from app.middleware.rate_limit import auth_rate_limiter
from app.models import Base

TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"

engine = create_async_engine(TEST_DATABASE_URL, echo=False)
TestSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest_asyncio.fixture(autouse=True)
async def setup_db():
    # Reset the in-memory auth rate limiter between tests so the per-IP
    # counter doesn't leak across the session and start returning 429s
    # once enough register/login calls have been made.
    auth_rate_limiter._requests.clear()
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


async def override_get_db() -> AsyncGenerator[AsyncSession, None]:
    async with TestSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


app.dependency_overrides[get_db] = override_get_db


@pytest_asyncio.fixture
async def client() -> AsyncGenerator[AsyncClient, None]:
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest_asyncio.fixture
async def auth_headers(client: AsyncClient) -> dict:
    reg = await client.post(
        "/api/v1/auth/register",
        json={"email": "test@example.com", "name": "Test User", "password": "testpass123"},
    )
    assert reg.status_code == 201, f"register failed: {reg.status_code} {reg.text}"
    resp = await client.post(
        "/api/v1/auth/login",
        json={"email": "test@example.com", "password": "testpass123"},
    )
    assert resp.status_code == 200, f"login failed: {resp.status_code} {resp.text}"
    token = resp.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


async def _elevate_tier(email: str, tier: str) -> None:
    """Directly set a user's tier in the test DB.

    AI endpoints enforce the tier gate server-side, so tests that exercise
    them need to upgrade the default test user from FREE.
    """
    from sqlalchemy import update as sqla_update

    from app.models import User as UserModel

    async with TestSessionLocal() as session:
        await session.execute(
            sqla_update(UserModel).where(UserModel.email == email).values(tier=tier)
        )
        await session.commit()


@pytest_asyncio.fixture
async def pro_auth_headers(client: AsyncClient, auth_headers: dict) -> dict:
    """Same as ``auth_headers`` but with the user elevated to PRO.

    Use this for tests that hit AI endpoints protected by
    ``DailyAIRateLimiter`` / ``ProFeatureGate``.
    """
    await _elevate_tier("test@example.com", "PRO")
    return auth_headers
