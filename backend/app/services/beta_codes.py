"""Beta-tester unlock code redemption logic.

Single source of truth for: (a) checking whether a user currently has
beta-pro entitlement (used by ``/auth/me`` to elevate
``effective_tier``), and (b) atomically redeeming a code with a
``SELECT ... FOR UPDATE`` lock so concurrent redemptions cannot blow
past ``max_redemptions``.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import BetaCode, BetaCodeRedemption


@dataclass
class RedeemOutcome:
    """Result of a redeem attempt. ``http_status`` is the suggested
    response code for the router; ``detail`` is the error message when
    not granted; ``pro_until`` mirrors the redemption snapshot."""

    granted: bool
    http_status: int
    detail: str = ""
    pro_until: Optional[datetime] = None


def _utc(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    return dt if dt.tzinfo is not None else dt.replace(tzinfo=timezone.utc)


async def has_active_beta_pro(db: AsyncSession, user_id: int) -> bool:
    """Does the user currently have an unexpired beta-code redemption?

    A redemption with ``grants_pro_until IS NULL`` is perpetual. A
    redemption with a future ``grants_pro_until`` is active. Past
    snapshots are inactive.
    """
    now = datetime.now(timezone.utc)
    stmt = select(BetaCodeRedemption).where(
        BetaCodeRedemption.user_id == user_id,
    )
    result = await db.execute(stmt)
    for row in result.scalars():
        until = _utc(row.grants_pro_until)
        if until is None or until > now:
            return True
    return False


async def redeem_code(
    db: AsyncSession, *, user_id: int, code: str
) -> RedeemOutcome:
    """Attempt to redeem ``code`` for ``user_id``.

    Concurrency: locks the ``beta_codes`` row with ``SELECT ... FOR
    UPDATE`` so two simultaneous redeems cannot both observe
    ``redemption_count = max_redemptions - 1`` and both succeed. Falls
    back gracefully on SQLite (which ignores ``FOR UPDATE`` but
    serializes writes with WAL + busy_timeout — see
    ``backend/tests/conftest.py``).
    """
    now = datetime.now(timezone.utc)

    stmt = select(BetaCode).where(BetaCode.code == code).with_for_update()
    result = await db.execute(stmt)
    code_row = result.scalar_one_or_none()
    if code_row is None:
        return RedeemOutcome(False, 400, "Code does not exist")

    if code_row.revoked_at is not None:
        return RedeemOutcome(False, 400, "Code has been revoked")

    valid_from = _utc(code_row.valid_from)
    valid_until = _utc(code_row.valid_until)
    if valid_from is not None and now < valid_from:
        return RedeemOutcome(False, 400, "Code is not yet valid")
    if valid_until is not None and now > valid_until:
        return RedeemOutcome(False, 400, "Code has expired")

    existing = await db.execute(
        select(BetaCodeRedemption).where(
            BetaCodeRedemption.code == code,
            BetaCodeRedemption.user_id == user_id,
        )
    )
    if existing.scalar_one_or_none() is not None:
        return RedeemOutcome(False, 409, "Code already redeemed by this account")

    if (
        code_row.max_redemptions is not None
        and code_row.redemption_count >= code_row.max_redemptions
    ):
        return RedeemOutcome(False, 410, "Code has reached its redemption cap")

    redemption = BetaCodeRedemption(
        code=code_row.code,
        user_id=user_id,
        grants_pro_until=code_row.grants_pro_until,
    )
    db.add(redemption)
    code_row.redemption_count = (code_row.redemption_count or 0) + 1
    await db.flush()

    return RedeemOutcome(
        granted=True,
        http_status=200,
        pro_until=_utc(code_row.grants_pro_until),
    )
