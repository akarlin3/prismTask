"""Beta-tester unlock code redemption endpoint.

``POST /beta/redeem`` is account-bound (``Depends(get_current_user)``)
and idempotent on the *(code, user)* pair: a second redeem returns 409
without incrementing ``redemption_count``. See
``app/services/beta_codes.py`` for the concurrency-safe redeem logic.
"""
import logging

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import User
from app.schemas.beta_codes import RedeemRequest, RedeemResponse
from app.services.beta_codes import redeem_code

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/beta", tags=["beta"])


@router.post("/redeem", response_model=RedeemResponse)
async def redeem(
    body: RedeemRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> RedeemResponse:
    outcome = await redeem_code(db, user_id=current_user.id, code=body.code)
    if not outcome.granted:
        raise HTTPException(status_code=outcome.http_status, detail=outcome.detail)

    logger.info(
        "Beta code redeemed: user_id=%s code=%s pro_until=%s",
        current_user.id,
        body.code,
        outcome.pro_until.isoformat() if outcome.pro_until else None,
    )
    return RedeemResponse(
        granted=True,
        pro_until=outcome.pro_until.isoformat() if outcome.pro_until else None,
    )
