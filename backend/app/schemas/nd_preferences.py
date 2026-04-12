from pydantic import BaseModel, Field


class NdPreferencesResponse(BaseModel):
    adhd_mode_enabled: bool = False
    calm_mode_enabled: bool = False

    # Calm Mode sub-settings
    reduce_animations: bool = False
    muted_color_palette: bool = False
    quiet_mode: bool = False
    reduce_haptics: bool = False
    soft_contrast: bool = False

    # ADHD Mode sub-settings
    task_decomposition_enabled: bool = False
    focus_guard_enabled: bool = False
    body_doubling_enabled: bool = False
    check_in_interval_minutes: int = Field(default=25, ge=10, le=60)
    completion_animations: bool = False
    streak_celebrations: bool = False
    show_progress_bars: bool = False
    forgiveness_streaks: bool = False

    model_config = {"from_attributes": True}


class NdPreferencesUpdate(BaseModel):
    adhd_mode_enabled: bool | None = None
    calm_mode_enabled: bool | None = None

    reduce_animations: bool | None = None
    muted_color_palette: bool | None = None
    quiet_mode: bool | None = None
    reduce_haptics: bool | None = None
    soft_contrast: bool | None = None

    task_decomposition_enabled: bool | None = None
    focus_guard_enabled: bool | None = None
    body_doubling_enabled: bool | None = None
    check_in_interval_minutes: int | None = Field(default=None, ge=10, le=60)
    completion_animations: bool | None = None
    streak_celebrations: bool | None = None
    show_progress_bars: bool | None = None
    forgiveness_streaks: bool | None = None


class SetModeRequest(BaseModel):
    enabled: bool
