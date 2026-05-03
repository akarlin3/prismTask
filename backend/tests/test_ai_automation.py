"""Tests for the A7 automation action AI endpoints.

Covers `POST /api/v1/ai/automation/complete` and
`POST /api/v1/ai/automation/summarize`. Service tests run against a stubbed
Anthropic client; router tests use the shared `client` + `pro_auth_headers`
fixtures from conftest.
"""

import sys
import types
from unittest.mock import MagicMock, patch

import pytest
from httpx import AsyncClient


def _make_text_response(text: str) -> MagicMock:
    block = MagicMock()
    block.text = text
    message = MagicMock()
    message.content = [block]
    return message


@pytest.fixture(autouse=True)
def mock_anthropic_module():
    """Force the service module to bind against a stubbed `anthropic`
    package so service-level tests don't need a real API key."""
    mock_mod = types.ModuleType("anthropic")
    mock_mod.Anthropic = MagicMock  # type: ignore
    mock_mod.APIError = Exception  # type: ignore
    sys.modules["anthropic"] = mock_mod

    import importlib

    import app.services.ai_productivity
    importlib.reload(app.services.ai_productivity)

    yield mock_mod

    if "anthropic" in sys.modules and sys.modules["anthropic"] is mock_mod:
        del sys.modules["anthropic"]
    importlib.reload(app.services.ai_productivity)


# --------------------------------------------------------------------------
# Service layer
# --------------------------------------------------------------------------


class TestAutomationCompleteService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_returns_stripped_text(self):
        from app.services.ai_productivity import generate_automation_completion

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_text_response(
                '  "Reschedule the kickoff to Tuesday morning."  '
            )

            text = generate_automation_completion(
                prompt="What should I tell the team?",
                context={"task_id": "abc-123", "title": "Kickoff"},
            )
            assert text == "Reschedule the kickoff to Tuesday morning."

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_forwards_prompt_and_context_to_anthropic(self):
        from app.services.ai_productivity import generate_automation_completion

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_text_response("ok")

            generate_automation_completion(
                prompt="Draft a follow-up note",
                context={"entity_type": "task", "title": "Send proposal"},
            )

            call_kwargs = mock_client.messages.create.call_args.kwargs
            user_payload = call_kwargs["messages"][0]["content"]
            assert "Draft a follow-up note" in user_payload
            assert "Send proposal" in user_payload

    def test_raises_runtime_error_without_api_key(self):
        from app.services import ai_productivity
        from app.services.ai_productivity import generate_automation_completion

        with patch.dict("os.environ", {}, clear=True), patch.object(
            ai_productivity.settings, "ANTHROPIC_API_KEY", ""
        ):
            with pytest.raises(RuntimeError):
                generate_automation_completion(prompt="hi")


class TestAutomationSummarizeService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_returns_stripped_summary(self):
        from app.services.ai_productivity import generate_automation_summary

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_text_response(
                'You finished 4 tasks today, 2 are still open.'
            )

            summary = generate_automation_summary(
                scope="today",
                max_items=10,
            )
            assert summary == "You finished 4 tasks today, 2 are still open."

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_forwards_scope_and_max_items(self):
        from app.services.ai_productivity import generate_automation_summary

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_text_response("ok")

            generate_automation_summary(
                scope="week",
                max_items=25,
                context={"completed": 4},
            )
            call_kwargs = mock_client.messages.create.call_args.kwargs
            user_payload = call_kwargs["messages"][0]["content"]
            assert '"scope": "week"' in user_payload
            assert '"max_items": 25' in user_payload


# --------------------------------------------------------------------------
# Router — /ai/automation/complete
# --------------------------------------------------------------------------


class TestAutomationCompleteEndpoint:
    @pytest.mark.asyncio
    async def test_returns_text(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        with patch(
            "app.services.ai_productivity.generate_automation_completion"
        ) as mock_gen:
            mock_gen.return_value = "Send the wrap-up email this afternoon."

            resp = await client.post(
                "/api/v1/ai/automation/complete",
                json={
                    "prompt": "Suggest a follow-up for this task",
                    "context": {"task_id": "abc"},
                },
                headers=pro_auth_headers,
            )
            assert resp.status_code == 200, resp.text
            assert resp.json() == {
                "text": "Send the wrap-up email this afternoon."
            }

    @pytest.mark.asyncio
    async def test_rejects_empty_prompt(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        resp = await client.post(
            "/api/v1/ai/automation/complete",
            json={"prompt": ""},
            headers=pro_auth_headers,
        )
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_503_when_anthropic_unavailable(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        with patch(
            "app.services.ai_productivity.generate_automation_completion"
        ) as mock_gen:
            mock_gen.side_effect = RuntimeError("ANTHROPIC_API_KEY not set")

            resp = await client.post(
                "/api/v1/ai/automation/complete",
                json={"prompt": "hello"},
                headers=pro_auth_headers,
            )
            assert resp.status_code == 503

    @pytest.mark.asyncio
    async def test_500_on_value_error(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        with patch(
            "app.services.ai_productivity.generate_automation_completion"
        ) as mock_gen:
            mock_gen.side_effect = ValueError("bad response")

            resp = await client.post(
                "/api/v1/ai/automation/complete",
                json={"prompt": "hello"},
                headers=pro_auth_headers,
            )
            assert resp.status_code == 500

    @pytest.mark.asyncio
    async def test_403_for_free_tier(
        self, client: AsyncClient, auth_headers: dict
    ):
        """Free users hit the daily AI rate limiter's tier gate before any
        Anthropic call."""
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        resp = await client.post(
            "/api/v1/ai/automation/complete",
            json={"prompt": "hi"},
            headers=auth_headers,
        )
        assert resp.status_code == 403

    @pytest.mark.asyncio
    async def test_451_when_ai_features_disabled(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.middleware.ai_gate import HEADER_NAME, HEADER_VALUE_DISABLED
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        headers = {**pro_auth_headers, HEADER_NAME: HEADER_VALUE_DISABLED}
        resp = await client.post(
            "/api/v1/ai/automation/complete",
            json={"prompt": "hi"},
            headers=headers,
        )
        assert resp.status_code == 451


# --------------------------------------------------------------------------
# Router — /ai/automation/summarize
# --------------------------------------------------------------------------


class TestAutomationSummarizeEndpoint:
    @pytest.mark.asyncio
    async def test_returns_summary(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        with patch(
            "app.services.ai_productivity.generate_automation_summary"
        ) as mock_gen:
            mock_gen.return_value = "You finished 4 tasks today."

            resp = await client.post(
                "/api/v1/ai/automation/summarize",
                json={"scope": "today", "max_items": 10},
                headers=pro_auth_headers,
            )
            assert resp.status_code == 200, resp.text
            assert resp.json() == {"summary": "You finished 4 tasks today."}

    @pytest.mark.asyncio
    async def test_rejects_invalid_scope(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        resp = await client.post(
            "/api/v1/ai/automation/summarize",
            json={"scope": "decade"},
            headers=pro_auth_headers,
        )
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_rejects_oversized_max_items(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        resp = await client.post(
            "/api/v1/ai/automation/summarize",
            json={"scope": "today", "max_items": 999},
            headers=pro_auth_headers,
        )
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_503_when_anthropic_unavailable(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        with patch(
            "app.services.ai_productivity.generate_automation_summary"
        ) as mock_gen:
            mock_gen.side_effect = RuntimeError("ANTHROPIC_API_KEY not set")

            resp = await client.post(
                "/api/v1/ai/automation/summarize",
                json={"scope": "today"},
                headers=pro_auth_headers,
            )
            assert resp.status_code == 503

    @pytest.mark.asyncio
    async def test_403_for_free_tier(
        self, client: AsyncClient, auth_headers: dict
    ):
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        resp = await client.post(
            "/api/v1/ai/automation/summarize",
            json={"scope": "today"},
            headers=auth_headers,
        )
        assert resp.status_code == 403

    @pytest.mark.asyncio
    async def test_451_when_ai_features_disabled(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.middleware.ai_gate import HEADER_NAME, HEADER_VALUE_DISABLED
        from app.routers.ai import automation_action_rate_limiter

        automation_action_rate_limiter._requests.clear()

        headers = {**pro_auth_headers, HEADER_NAME: HEADER_VALUE_DISABLED}
        resp = await client.post(
            "/api/v1/ai/automation/summarize",
            json={"scope": "today"},
            headers=headers,
        )
        assert resp.status_code == 451
