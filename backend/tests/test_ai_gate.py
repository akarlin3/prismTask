"""Tests for the require_ai_features_enabled gate (PII egress audit, 2026-04-26).

The gate is a router-level dependency on /ai/* and an endpoint-level
dependency on /tasks/parse, /tasks/parse-import, /tasks/parse-checklist,
and /syllabus/parse. When the caller sends
``X-PrismTask-AI-Features: disabled``, the gate must return 451
*before* the route handler runs — i.e. before any Anthropic call.

These tests assert the gate's behavior without hitting Anthropic. The
critical privacy invariant is the negative case: a request with the
opt-out header MUST NOT execute the AI service code that would call
``anthropic.Anthropic()``.
"""

from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient
from httpx import AsyncClient

from app.main import app
from app.middleware.ai_gate import (
    HEADER_NAME,
    HEADER_VALUE_DISABLED,
    require_ai_features_enabled,
)


def _client() -> TestClient:
    """Synchronous test client — preferred when we don't need the async
    conftest fixtures (auth, Pro tier, etc.) because the gate is supposed
    to run *before* those deps anyway."""
    return TestClient(app)


class TestRequireAiFeaturesEnabledHeaderHandling:
    """The gate dependency itself, isolated from any endpoint."""

    @pytest.mark.asyncio
    async def test_no_header_is_a_noop(self):
        # Should not raise when the header is absent.
        result = await require_ai_features_enabled(x_prismtask_ai_features=None)
        assert result is None

    @pytest.mark.asyncio
    async def test_header_with_disabled_value_raises_451(self):
        from fastapi import HTTPException

        with pytest.raises(HTTPException) as exc:
            await require_ai_features_enabled(
                x_prismtask_ai_features=HEADER_VALUE_DISABLED
            )
        assert exc.value.status_code == 451

    @pytest.mark.asyncio
    async def test_header_with_disabled_value_is_case_insensitive(self):
        from fastapi import HTTPException

        with pytest.raises(HTTPException) as exc:
            await require_ai_features_enabled(
                x_prismtask_ai_features="DISABLED"
            )
        assert exc.value.status_code == 451

    @pytest.mark.asyncio
    async def test_header_with_disabled_value_tolerates_whitespace(self):
        from fastapi import HTTPException

        with pytest.raises(HTTPException) as exc:
            await require_ai_features_enabled(
                x_prismtask_ai_features="  disabled  "
            )
        assert exc.value.status_code == 451

    @pytest.mark.asyncio
    async def test_header_with_other_value_is_a_noop(self):
        # A future signal (e.g. "enabled", "audit-only") must NOT trigger 451.
        # Only the literal "disabled" sentinel should reject.
        result = await require_ai_features_enabled(
            x_prismtask_ai_features="enabled"
        )
        assert result is None

        result = await require_ai_features_enabled(
            x_prismtask_ai_features="something-else"
        )
        assert result is None


class TestAiRouterRejectsOptOutHeader:
    """End-to-end: sending the opt-out header to a real /ai/* endpoint
    must return 451 *before* the route handler runs."""

    def test_batch_parse_returns_451_with_opt_out_header(self):
        """The router-level dependency on /ai must reject before
        ``parse_batch_command`` (which would call Anthropic) is reached."""
        with patch("app.services.ai_productivity.parse_batch_command") as mock_parse:
            client = _client()
            resp = client.post(
                "/api/v1/ai/batch-parse",
                json={
                    "command_text": "Skip my medication today",
                    "user_context": {
                        "today": "2026-04-26",
                        "timezone": "UTC",
                        "tasks": [],
                        "habits": [],
                        "projects": [],
                        "medications": [{"id": "med-1", "name": "Adderall"}],
                    },
                },
                headers={HEADER_NAME: HEADER_VALUE_DISABLED},
            )
            assert resp.status_code == 451, resp.text
            # Critical: the AI service must NOT have been called. This is
            # the privacy invariant — no medication name reaches Anthropic
            # when the user has opted out.
            mock_parse.assert_not_called()

    def test_eisenhower_categorize_returns_451_with_opt_out_header(self):
        # Path is `/api/v1/ai/eisenhower` (see backend/app/routers/ai.py:122).
        with patch("app.services.ai_productivity.categorize_eisenhower") as mock_cat:
            client = _client()
            resp = client.post(
                "/api/v1/ai/eisenhower",
                json={"task_ids": ["task-1", "task-2"]},
                headers={HEADER_NAME: HEADER_VALUE_DISABLED},
            )
            assert resp.status_code == 451, resp.text
            mock_cat.assert_not_called()

    def test_habit_correlations_returns_451_with_opt_out_header(self):
        # Path is `/api/v1/analytics/habit-correlations`. The endpoint
        # calls Anthropic via `analyze_habit_correlations`, so the gate
        # must reject before the route handler runs (PR closing the gap
        # left when the endpoint was originally written without the dep).
        with patch(
            "app.services.ai_productivity.analyze_habit_correlations"
        ) as mock_analyze:
            client = _client()
            resp = client.get(
                "/api/v1/analytics/habit-correlations",
                headers={HEADER_NAME: HEADER_VALUE_DISABLED},
            )
            assert resp.status_code == 451, resp.text
            # Critical privacy invariant: habit names + completion
            # patterns must NOT reach Anthropic when the user has opted
            # out of AI features.
            mock_analyze.assert_not_called()


class TestParseEndpointsRejectOptOutHeader:
    """The /tasks/parse* and /syllabus/parse endpoints have endpoint-level
    deps (because their parent routers serve non-AI routes too)."""

    def test_tasks_parse_returns_451_with_opt_out_header(self):
        with patch("app.services.nlp_parser.parse_task_input") as mock_parse:
            client = _client()
            resp = client.post(
                "/api/v1/tasks/parse",
                json={"text": "buy milk tomorrow"},
                headers={HEADER_NAME: HEADER_VALUE_DISABLED},
            )
            assert resp.status_code == 451, resp.text
            mock_parse.assert_not_called()


class TestRouterAcceptsRequestWithoutOptOutHeader:
    """Sanity check: omitting the header does not break existing flows."""

    @pytest.mark.asyncio
    async def test_batch_parse_proceeds_without_header(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        """When no opt-out header is supplied, the gate is a no-op and the
        request proceeds normally (and reaches the mocked AI service)."""
        from app.routers.ai import batch_parse_rate_limiter

        batch_parse_rate_limiter._requests.clear()

        with patch("app.services.ai_productivity.parse_batch_command") as mock_parse:
            mock_parse.return_value = {
                "mutations": [],
                "confidence": 0.0,
                "ambiguous_entities": [],
            }
            resp = await client.post(
                "/api/v1/ai/batch-parse",
                json={
                    "command_text": "no-op command",
                    "user_context": {
                        "today": "2026-04-26",
                        "timezone": "UTC",
                        "tasks": [],
                        "habits": [],
                        "projects": [],
                        "medications": [],
                    },
                },
                headers=pro_auth_headers,
            )
            # Should reach the mocked service rather than 451.
            assert resp.status_code == 200, resp.text
            mock_parse.assert_called_once()
