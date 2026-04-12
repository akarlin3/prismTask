import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_get_nd_preferences_defaults(client: AsyncClient, auth_headers: dict):
    """GET returns defaults with both modes off."""
    resp = await client.get("/api/v1/nd-preferences", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["adhd_mode_enabled"] is False
    assert data["calm_mode_enabled"] is False
    assert data["reduce_animations"] is False
    assert data["muted_color_palette"] is False
    assert data["quiet_mode"] is False
    assert data["reduce_haptics"] is False
    assert data["soft_contrast"] is False
    assert data["task_decomposition_enabled"] is False
    assert data["focus_guard_enabled"] is False
    assert data["body_doubling_enabled"] is False
    assert data["check_in_interval_minutes"] == 25
    assert data["completion_animations"] is False
    assert data["streak_celebrations"] is False
    assert data["show_progress_bars"] is False
    assert data["forgiveness_streaks"] is False


@pytest.mark.asyncio
async def test_get_nd_preferences_requires_auth(client: AsyncClient):
    """GET without auth returns 401."""
    resp = await client.get("/api/v1/nd-preferences")
    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_set_adhd_mode_on_flips_all_adhd_sub_settings(
    client: AsyncClient, auth_headers: dict
):
    """POST /adhd-mode with enabled=true flips all ADHD sub-settings on."""
    resp = await client.post(
        "/api/v1/nd-preferences/adhd-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["adhd_mode_enabled"] is True
    assert data["task_decomposition_enabled"] is True
    assert data["focus_guard_enabled"] is True
    assert data["body_doubling_enabled"] is True
    assert data["completion_animations"] is True
    assert data["streak_celebrations"] is True
    assert data["show_progress_bars"] is True
    assert data["forgiveness_streaks"] is True


@pytest.mark.asyncio
async def test_set_adhd_mode_on_does_not_affect_calm_settings(
    client: AsyncClient, auth_headers: dict
):
    """Enabling ADHD mode does not change Calm sub-settings."""
    resp = await client.post(
        "/api/v1/nd-preferences/adhd-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["calm_mode_enabled"] is False
    assert data["reduce_animations"] is False
    assert data["muted_color_palette"] is False
    assert data["quiet_mode"] is False
    assert data["reduce_haptics"] is False
    assert data["soft_contrast"] is False


@pytest.mark.asyncio
async def test_set_calm_mode_on_flips_all_calm_sub_settings(
    client: AsyncClient, auth_headers: dict
):
    """POST /calm-mode with enabled=true flips all Calm sub-settings on."""
    resp = await client.post(
        "/api/v1/nd-preferences/calm-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["calm_mode_enabled"] is True
    assert data["reduce_animations"] is True
    assert data["muted_color_palette"] is True
    assert data["quiet_mode"] is True
    assert data["reduce_haptics"] is True
    assert data["soft_contrast"] is True


@pytest.mark.asyncio
async def test_set_calm_mode_on_does_not_affect_adhd_settings(
    client: AsyncClient, auth_headers: dict
):
    """Enabling Calm mode does not change ADHD sub-settings."""
    resp = await client.post(
        "/api/v1/nd-preferences/calm-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["adhd_mode_enabled"] is False
    assert data["task_decomposition_enabled"] is False
    assert data["focus_guard_enabled"] is False


@pytest.mark.asyncio
async def test_disable_adhd_mode_does_not_affect_calm_mode(
    client: AsyncClient, auth_headers: dict
):
    """Disabling ADHD mode while Calm is on preserves Calm settings."""
    # Enable both
    await client.post(
        "/api/v1/nd-preferences/adhd-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    await client.post(
        "/api/v1/nd-preferences/calm-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    # Disable ADHD
    resp = await client.post(
        "/api/v1/nd-preferences/adhd-mode",
        json={"enabled": False},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["adhd_mode_enabled"] is False
    assert data["task_decomposition_enabled"] is False
    # Calm still on
    assert data["calm_mode_enabled"] is True
    assert data["reduce_animations"] is True
    assert data["soft_contrast"] is True


@pytest.mark.asyncio
async def test_disable_calm_mode_does_not_affect_adhd_mode(
    client: AsyncClient, auth_headers: dict
):
    """Disabling Calm mode while ADHD is on preserves ADHD settings."""
    # Enable both
    await client.post(
        "/api/v1/nd-preferences/adhd-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    await client.post(
        "/api/v1/nd-preferences/calm-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    # Disable Calm
    resp = await client.post(
        "/api/v1/nd-preferences/calm-mode",
        json={"enabled": False},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["calm_mode_enabled"] is False
    assert data["reduce_animations"] is False
    # ADHD still on
    assert data["adhd_mode_enabled"] is True
    assert data["completion_animations"] is True
    assert data["forgiveness_streaks"] is True


@pytest.mark.asyncio
async def test_patch_individual_setting(client: AsyncClient, auth_headers: dict):
    """PATCH updates only the specified fields."""
    resp = await client.patch(
        "/api/v1/nd-preferences",
        json={"reduce_animations": True, "check_in_interval_minutes": 45},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["reduce_animations"] is True
    assert data["check_in_interval_minutes"] == 45
    # Other fields unchanged
    assert data["adhd_mode_enabled"] is False
    assert data["calm_mode_enabled"] is False


@pytest.mark.asyncio
async def test_patch_does_not_disable_parent_mode(
    client: AsyncClient, auth_headers: dict
):
    """Changing a sub-setting via PATCH does not disable the parent mode toggle."""
    await client.post(
        "/api/v1/nd-preferences/adhd-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    resp = await client.patch(
        "/api/v1/nd-preferences",
        json={"completion_animations": False},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["adhd_mode_enabled"] is True  # parent stays on
    assert data["completion_animations"] is False  # sub-setting overridden


@pytest.mark.asyncio
async def test_both_modes_on_has_reduce_and_completion_animations(
    client: AsyncClient, auth_headers: dict
):
    """Both modes active: reduceAnimations=true AND completionAnimations=true."""
    await client.post(
        "/api/v1/nd-preferences/adhd-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    await client.post(
        "/api/v1/nd-preferences/calm-mode",
        json={"enabled": True},
        headers=auth_headers,
    )
    resp = await client.get("/api/v1/nd-preferences", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["reduce_animations"] is True
    assert data["completion_animations"] is True
