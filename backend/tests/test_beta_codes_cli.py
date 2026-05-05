"""Smoke tests for ``scripts/beta_codes.py`` CLI handlers.

The CLI talks to the same SQLAlchemy models as the rest of the app, so
these tests exercise the underlying ``cmd_issue`` / ``cmd_list`` /
``cmd_revoke`` async functions against the in-memory test DB by
overriding ``create_async_engine`` to use the conftest engine.
"""
from __future__ import annotations

import argparse
import importlib.util
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from sqlalchemy import select

from app.models import BetaCode
from tests.conftest import TestSessionLocal


# Load scripts/beta_codes.py as a module without importing through the
# package system (it lives outside ``backend/``).
_CLI_PATH = Path(__file__).resolve().parents[2] / "scripts" / "beta_codes.py"
_spec = importlib.util.spec_from_file_location("beta_codes_cli", _CLI_PATH)
beta_codes_cli = importlib.util.module_from_spec(_spec)  # type: ignore[arg-type]
sys.modules["beta_codes_cli"] = beta_codes_cli
_spec.loader.exec_module(beta_codes_cli)  # type: ignore[union-attr]


@pytest.fixture(autouse=True)
def _patch_cli_session(monkeypatch):
    """Make ``_session()`` reuse the pytest engine + session factory so
    CLI commands hit the in-memory test DB."""

    async def _fake_session():
        class _NoOpEngine:
            async def dispose(self):
                pass

        return _NoOpEngine(), TestSessionLocal

    monkeypatch.setattr(beta_codes_cli, "_session", _fake_session)


@pytest.mark.asyncio
async def test_cli_issue_creates_code(capsys):
    args = argparse.Namespace(
        code="CLI-ISSUE-1",
        description="cli smoke",
        valid_from=None,
        valid_until="2026-06-15",
        grants_pro_until="2026-09-01",
        max_redemptions=50,
    )
    await beta_codes_cli.cmd_issue(args)
    out = capsys.readouterr().out
    assert "Issued 'CLI-ISSUE-1'" in out

    async with TestSessionLocal() as session:
        result = await session.execute(select(BetaCode).where(BetaCode.code == "CLI-ISSUE-1"))
        row = result.scalar_one()
        assert row.description == "cli smoke"
        assert row.max_redemptions == 50


@pytest.mark.asyncio
async def test_cli_issue_rejects_duplicate(capsys):
    args = argparse.Namespace(
        code="CLI-DUP", description=None, valid_from=None, valid_until=None,
        grants_pro_until=None, max_redemptions=None,
    )
    await beta_codes_cli.cmd_issue(args)
    capsys.readouterr()
    with pytest.raises(SystemExit):
        await beta_codes_cli.cmd_issue(args)


@pytest.mark.asyncio
async def test_cli_list_active_only_filters_revoked(capsys):
    async with TestSessionLocal() as session:
        session.add(BetaCode(code="CLI-ACTIVE"))
        session.add(
            BetaCode(
                code="CLI-REVOKED",
                revoked_at=datetime.now(timezone.utc) - timedelta(hours=1),
            )
        )
        await session.commit()

    await beta_codes_cli.cmd_list(argparse.Namespace(active_only=False))
    full = capsys.readouterr().out
    assert "CLI-ACTIVE" in full and "CLI-REVOKED" in full

    await beta_codes_cli.cmd_list(argparse.Namespace(active_only=True))
    active = capsys.readouterr().out
    assert "CLI-ACTIVE" in active
    assert "CLI-REVOKED" not in active


@pytest.mark.asyncio
async def test_cli_revoke_marks_revoked_at(capsys):
    async with TestSessionLocal() as session:
        session.add(BetaCode(code="CLI-REVOKE-ME"))
        await session.commit()

    await beta_codes_cli.cmd_revoke(argparse.Namespace(code="CLI-REVOKE-ME"))
    capsys.readouterr()

    async with TestSessionLocal() as session:
        result = await session.execute(
            select(BetaCode).where(BetaCode.code == "CLI-REVOKE-ME")
        )
        row = result.scalar_one()
        assert row.revoked_at is not None


@pytest.mark.asyncio
async def test_cli_revoke_unknown_code_exits(capsys):
    with pytest.raises(SystemExit):
        await beta_codes_cli.cmd_revoke(argparse.Namespace(code="NEVER-EXISTED"))
