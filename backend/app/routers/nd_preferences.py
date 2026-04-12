from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import NdPreferencesModel, User
from app.schemas.nd_preferences import (
    NdPreferencesResponse,
    NdPreferencesUpdate,
    SetModeRequest,
)

router = APIRouter(prefix="/nd-preferences", tags=["nd-preferences"])

# ADHD sub-setting fields that flip with the mode toggle
_ADHD_FIELDS = [
    "task_decomposition_enabled",
    "focus_guard_enabled",
    "body_doubling_enabled",
    "completion_animations",
    "streak_celebrations",
    "show_progress_bars",
    "forgiveness_streaks",
]

# Calm sub-setting fields that flip with the mode toggle
_CALM_FIELDS = [
    "reduce_animations",
    "muted_color_palette",
    "quiet_mode",
    "reduce_haptics",
    "soft_contrast",
]


async def _get_or_create_prefs(user: User, db: AsyncSession) -> NdPreferencesModel:
    """Get existing preferences or create defaults for the user."""
    result = await db.execute(
        select(NdPreferencesModel).where(NdPreferencesModel.user_id == user.id)
    )
    prefs = result.scalar_one_or_none()
    if prefs is None:
        prefs = NdPreferencesModel(user_id=user.id)
        db.add(prefs)
        await db.flush()
    return prefs


@router.get("", response_model=NdPreferencesResponse)
async def get_nd_preferences(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Get the current user's neurodivergent mode preferences."""
    prefs = await _get_or_create_prefs(user, db)
    await db.commit()
    return prefs


@router.patch("", response_model=NdPreferencesResponse)
async def update_nd_preferences(
    body: NdPreferencesUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Update individual ND preference fields. Only provided fields are changed."""
    prefs = await _get_or_create_prefs(user, db)

    update_data = body.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(prefs, field, value)

    await db.commit()
    await db.refresh(prefs)
    return prefs


@router.post("/adhd-mode", response_model=NdPreferencesResponse)
async def set_adhd_mode(
    body: SetModeRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Enable or disable ADHD Mode. When enabled, flips ALL ADHD sub-settings on.
    When disabled, flips ALL ADHD sub-settings off. Does not affect Calm Mode.
    """
    prefs = await _get_or_create_prefs(user, db)
    prefs.adhd_mode_enabled = body.enabled
    for field in _ADHD_FIELDS:
        setattr(prefs, field, body.enabled)

    await db.commit()
    await db.refresh(prefs)
    return prefs


@router.post("/calm-mode", response_model=NdPreferencesResponse)
async def set_calm_mode(
    body: SetModeRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Enable or disable Calm Mode. When enabled, flips ALL Calm sub-settings on.
    When disabled, flips ALL Calm sub-settings off. Does not affect ADHD Mode.
    """
    prefs = await _get_or_create_prefs(user, db)
    prefs.calm_mode_enabled = body.enabled
    for field in _CALM_FIELDS:
        setattr(prefs, field, body.enabled)

    await db.commit()
    await db.refresh(prefs)
    return prefs
