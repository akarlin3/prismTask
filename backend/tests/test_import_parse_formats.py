"""Multi-format coverage for POST /api/v1/tasks/parse-checklist.

The F.8 audit (docs/audits/F8_STRUCTURED_SCHEDULE_IMPORT_AUDIT.md) hypothesises
that the existing ``parse-checklist`` endpoint already accepts JSX / JSON /
YAML / Markdown / CSV without prompt changes — Haiku is fundamentally
format-agnostic. This module ships fixtures that express the same logical
4-week schedule in all five formats and provides two test layers:

1. **Unit (always run).** Each fixture round-trips through a mocked
   ``_call_haiku`` helper. This validates the request/response contract and
   the Pydantic shape, but does NOT prove the multi-format hypothesis.

2. **Integration (gated).** When ``RUN_AI_INTEGRATION_TESTS=1`` is set in
   the environment AND ``ANTHROPIC_API_KEY`` is real, each fixture is sent
   to live Haiku via the running endpoint. The hypothesis is confirmed if
   each format extracts at least 6 of the 12 fixture tasks (allowing some
   slack for items Haiku may judge non-actionable).

Cost note: integration mode burns ~$0.013 per fixture (~$0.07 total per
test run). Default-off so CI doesn't pay that bill on every push.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
from unittest.mock import patch

import pytest
from httpx import AsyncClient

FIXTURES_DIR = Path(__file__).parent / "fixtures" / "import_formats"

FORMATS = [
    ("jsx", "sample.jsx"),
    ("json", "sample.json"),
    ("yaml", "sample.yaml"),
    ("md", "sample.md"),
    ("csv", "sample.csv"),
]


def _read_fixture(filename: str) -> str:
    return (FIXTURES_DIR / filename).read_text(encoding="utf-8")


# Canned Haiku response for the mocked tests. Same logical content for all
# formats — the contract assertion is "endpoint accepts the format and
# returns a valid ParseChecklistResponse", not "Haiku produced the right
# output for this particular format" (the latter is the integration test).
_MOCKED_CHECKLIST_RESPONSE = json.dumps({
    "course": {"code": "FIRMWARE-101", "name": "Embedded Firmware Sprint"},
    "project": {
        "name": "Embedded Firmware Sprint",
        "color": "#4A90D9",
        "icon": "🔌",
    },
    "tags": [
        {"name": "exam", "color": "#E94B3C"},
        {"name": "code", "color": "#4A90D9"},
    ],
    "tasks": [
        {
            "title": "Set up toolchain (gcc-arm, openocd)",
            "description": None,
            "dueDate": None,
            "priority": 4,
            "completed": False,
            "tags": ["code"],
            "estimatedMinutes": None,
            "subtasks": [],
        },
        {
            "title": "EXAM: bring-up review with mentor",
            "description": None,
            "dueDate": None,
            "priority": 4,
            "completed": False,
            "tags": ["exam"],
            "estimatedMinutes": None,
            "subtasks": [],
        },
    ],
    # F.8 fields. The mocked response exercises them so the contract test
    # below covers the new schema branch even when the integration suite
    # is gated off in CI.
    "phases": [
        {"name": "Foundation", "description": None, "startDate": None, "endDate": None, "orderIndex": 0},
        {"name": "Bring-up", "description": None, "startDate": None, "endDate": None, "orderIndex": 1},
    ],
    "risks": [
        {"title": "Hardware shipping delay", "description": None, "level": "MEDIUM"},
    ],
    "externalAnchors": [
        {"title": "Demo to advisor", "type": "calendar_deadline", "phaseName": "Integration", "targetDate": None},
    ],
    "taskDependencies": [
        {"blockerTitle": "First boot + LED blink", "blockedTitle": "Configure UART + clock tree"},
    ],
})


# --------------------------------------------------------------------------
# Unit layer — always runs, no API cost
# --------------------------------------------------------------------------


@pytest.mark.asyncio
@pytest.mark.parametrize("fmt,filename", FORMATS, ids=[f for f, _ in FORMATS])
async def test_parse_checklist_accepts_format(
    fmt: str,
    filename: str,
    client: AsyncClient,
    auth_headers: dict,
):
    """Each fixture format passes the request validator and returns 200.

    Mocks ``_call_haiku`` so the test does not depend on a real API key.
    Reset the rate limiter so prior tests in the suite don't poison this run.
    """
    from app.middleware.rate_limit import import_parse_rate_limiter

    import_parse_rate_limiter._requests.clear()

    content = _read_fixture(filename)
    assert len(content) > 0, f"fixture {filename} is empty"

    with (
        patch.dict("os.environ", {"ANTHROPIC_API_KEY": "test-key"}),
        patch("app.routers.tasks._call_haiku", return_value=_MOCKED_CHECKLIST_RESPONSE),
    ):
        resp = await client.post(
            "/api/v1/tasks/parse-checklist",
            json={"content": content},
            headers=auth_headers,
        )

    assert resp.status_code == 200, f"{fmt} fixture rejected: {resp.text}"
    body = resp.json()
    assert "course" in body
    assert "project" in body
    assert "tags" in body
    assert "tasks" in body
    assert len(body["tasks"]) >= 1
    # F.8 extension: verify the new project-shape fields round-trip.
    assert body["phases"][0]["name"] == "Foundation"
    assert body["phases"][0]["orderIndex"] == 0
    assert body["risks"][0]["level"] == "MEDIUM"
    assert body["externalAnchors"][0]["type"] == "calendar_deadline"
    assert body["taskDependencies"][0]["blockerTitle"] == "First boot + LED blink"


@pytest.mark.asyncio
async def test_parse_checklist_backwards_compat_empty_extensions(
    client: AsyncClient,
    auth_headers: dict,
):
    """Schoolwork callers (existing) get empty F.8 fields and don't break.

    Mocked Haiku response omits phases/risks/anchors/dependencies entirely;
    the response model must default them to empty arrays, matching the
    pre-F.8 contract for callers that ignore the new fields.
    """
    from app.middleware.rate_limit import import_parse_rate_limiter

    import_parse_rate_limiter._requests.clear()

    legacy_response = json.dumps({
        "course": {"code": "OLD-101", "name": "Legacy Course"},
        "project": {"name": "Old", "color": "#000", "icon": "📘"},
        "tags": [],
        "tasks": [
            {
                "title": "Legacy task",
                "description": None,
                "dueDate": None,
                "priority": 0,
                "completed": False,
                "tags": [],
                "estimatedMinutes": None,
                "subtasks": [],
            }
        ],
    })

    with (
        patch.dict("os.environ", {"ANTHROPIC_API_KEY": "test-key"}),
        patch("app.routers.tasks._call_haiku", return_value=legacy_response),
    ):
        resp = await client.post(
            "/api/v1/tasks/parse-checklist",
            json={"content": "anything"},
            headers=auth_headers,
        )

    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["phases"] == []
    assert body["risks"] == []
    assert body["externalAnchors"] == []
    assert body["taskDependencies"] == []


# --------------------------------------------------------------------------
# Integration layer — gated by RUN_AI_INTEGRATION_TESTS=1
# --------------------------------------------------------------------------


_INTEGRATION_ENABLED = os.environ.get("RUN_AI_INTEGRATION_TESTS") == "1"


@pytest.mark.asyncio
@pytest.mark.skipif(
    not _INTEGRATION_ENABLED,
    reason="Set RUN_AI_INTEGRATION_TESTS=1 + a real ANTHROPIC_API_KEY to run.",
)
@pytest.mark.parametrize("fmt,filename", FORMATS, ids=[f for f, _ in FORMATS])
async def test_parse_checklist_real_haiku_extracts_tasks(
    fmt: str,
    filename: str,
    client: AsyncClient,
    auth_headers: dict,
):
    """Real Claude Haiku call. Each fixture should yield >= 6 tasks.

    The fixtures express the same 12-task schedule. Haiku may legitimately
    drop some items (e.g. consider an EXAM line both task and event), so
    the bar is half. Failure here means the F.8 multi-format hypothesis
    needs prompt edits for the affected format.
    """
    from app.middleware.rate_limit import import_parse_rate_limiter

    import_parse_rate_limiter._requests.clear()

    content = _read_fixture(filename)

    resp = await client.post(
        "/api/v1/tasks/parse-checklist",
        json={"content": content},
        headers=auth_headers,
    )

    assert resp.status_code == 200, f"{fmt} extraction failed: {resp.text}"
    body = resp.json()
    tasks = body.get("tasks", [])
    assert len(tasks) >= 6, (
        f"{fmt} produced only {len(tasks)} tasks; expected >= 6 of 12. "
        "Hypothesis: this format may need a prompt tweak."
    )
