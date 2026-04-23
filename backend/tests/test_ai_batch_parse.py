"""Tests for the A2 batch-parse endpoint and service.

Service tests run against a stubbed Anthropic client. Router tests use the
shared httpx AsyncClient + pro_auth_headers fixtures from conftest.
"""

import json
import sys
import types
from unittest.mock import AsyncMock, MagicMock, patch  # noqa: F401

import pytest
from httpx import AsyncClient


def _make_mock_response(data) -> MagicMock:
    content_block = MagicMock()
    content_block.text = json.dumps(data)
    message = MagicMock()
    message.content = [content_block]
    return message


@pytest.fixture(autouse=True)
def mock_anthropic_module():
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


def _user_context(**overrides) -> dict:
    """Default user_context with two tasks, one habit, one project."""
    base = {
        "today": "2026-04-23",
        "timezone": "America/Los_Angeles",
        "tasks": [
            {
                "id": "task-1",
                "title": "Sprint planning prep",
                "due_date": "2026-04-24",
                "priority": 2,
                "tags": ["work"],
            },
            {
                "id": "task-2",
                "title": "Buy groceries",
                "due_date": "2026-04-24",
                "priority": 1,
                "tags": ["personal"],
            },
        ],
        "habits": [{"id": "habit-1", "name": "Meditation"}],
        "projects": [{"id": "proj-1", "name": "Q2 Planning"}],
        "medications": [],
    }
    base.update(overrides)
    return base


class TestBatchParseService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_returns_structured_mutations(self):
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [
                        {
                            "entity_type": "TASK",
                            "entity_id": "task-1",
                            "mutation_type": "DELETE",
                            "proposed_new_values": {},
                            "human_readable_description": "Delete 'Sprint planning prep'",
                        }
                    ],
                    "confidence": 0.95,
                    "ambiguous_entities": [],
                }
            )

            result = parse_batch_command(
                "Cancel sprint planning",
                _user_context(),
                tier="PRO",
            )
            assert len(result["mutations"]) == 1
            assert result["mutations"][0]["entity_type"] == "TASK"
            assert result["mutations"][0]["entity_id"] == "task-1"
            assert result["mutations"][0]["mutation_type"] == "DELETE"
            assert result["confidence"] == pytest.approx(0.95)
            assert result["ambiguous_entities"] == []

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_surfaces_ambiguity(self):
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [],
                    "confidence": 0.4,
                    "ambiguous_entities": [
                        {
                            "phrase": "work tasks",
                            "candidate_entity_type": "PROJECT",
                            "candidate_entity_ids": ["proj-1", "proj-2"],
                            "note": "Two projects match 'work'",
                        }
                    ],
                }
            )

            ctx = _user_context(
                projects=[
                    {"id": "proj-1", "name": "Work — H1"},
                    {"id": "proj-2", "name": "Work — H2"},
                ]
            )
            result = parse_batch_command("Move all work tasks to Monday", ctx)
            assert result["mutations"] == []
            assert result["confidence"] == pytest.approx(0.4)
            assert len(result["ambiguous_entities"]) == 1
            assert result["ambiguous_entities"][0]["phrase"] == "work tasks"

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_retries_then_succeeds_on_malformed_first_response(self):
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "not valid json"
            bad_response.content = [bad_content]

            good_response = _make_mock_response(
                {"mutations": [], "confidence": 0.0, "ambiguous_entities": []}
            )
            mock_client.messages.create.side_effect = [bad_response, good_response]

            result = parse_batch_command("garbled command", _user_context())
            assert result["mutations"] == []
            assert mock_client.messages.create.call_count == 2

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_raises_value_error_after_two_malformed_responses(self):
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "not json at all"
            bad.content = [bad_content]
            mock_client.messages.create.return_value = bad

            with pytest.raises(ValueError):
                parse_batch_command("anything", _user_context())

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_drops_completed_tasks_from_prompt_context(self):
        """Completed tasks bloat the prompt and shouldn't show up in
        proposed mutations. The service trims them before the AI call."""
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {"mutations": [], "confidence": 1.0, "ambiguous_entities": []}
            )

            ctx = _user_context(
                tasks=[
                    {"id": "open", "title": "Open", "is_completed": False},
                    {"id": "done", "title": "Done", "is_completed": True},
                ]
            )
            parse_batch_command("anything", ctx)

            sent_payload = mock_client.messages.create.call_args.kwargs["messages"][0][
                "content"
            ]
            payload_dict = json.loads(sent_payload)
            sent_task_ids = [t["id"] for t in payload_dict["context"]["tasks"]]
            assert "open" in sent_task_ids
            assert "done" not in sent_task_ids


class TestBatchParseEndpoint:
    @pytest.mark.asyncio
    async def test_endpoint_returns_proposed_mutations(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import batch_parse_rate_limiter

        batch_parse_rate_limiter._requests.clear()

        with patch("app.services.ai_productivity.parse_batch_command") as mock_parse:
            mock_parse.return_value = {
                "mutations": [
                    {
                        "entity_type": "TASK",
                        "entity_id": "task-1",
                        "mutation_type": "RESCHEDULE",
                        "proposed_new_values": {"due_date": "2026-04-30"},
                        "human_readable_description": "Move to next Friday",
                    }
                ],
                "confidence": 0.9,
                "ambiguous_entities": [],
            }

            resp = await client.post(
                "/api/v1/ai/batch-parse",
                json={
                    "command_text": "Move sprint planning to next Friday",
                    "user_context": _user_context(),
                },
                headers=pro_auth_headers,
            )
            assert resp.status_code == 200, resp.text
            body = resp.json()
            assert body["proposed"] is True
            assert len(body["mutations"]) == 1
            assert body["mutations"][0]["entity_id"] == "task-1"
            assert body["confidence"] == pytest.approx(0.9)

    @pytest.mark.asyncio
    async def test_endpoint_rejects_empty_command(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import batch_parse_rate_limiter

        batch_parse_rate_limiter._requests.clear()

        resp = await client.post(
            "/api/v1/ai/batch-parse",
            json={"command_text": "", "user_context": _user_context()},
            headers=pro_auth_headers,
        )
        # Pydantic catches min_length=1.
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_endpoint_503_when_anthropic_unavailable(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import batch_parse_rate_limiter

        batch_parse_rate_limiter._requests.clear()

        with patch("app.services.ai_productivity.parse_batch_command") as mock_parse:
            mock_parse.side_effect = RuntimeError("ANTHROPIC_API_KEY not set")

            resp = await client.post(
                "/api/v1/ai/batch-parse",
                json={"command_text": "anything", "user_context": _user_context()},
                headers=pro_auth_headers,
            )
            assert resp.status_code == 503

    @pytest.mark.asyncio
    async def test_endpoint_500_on_malformed_ai_response(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import batch_parse_rate_limiter

        batch_parse_rate_limiter._requests.clear()

        with patch("app.services.ai_productivity.parse_batch_command") as mock_parse:
            mock_parse.side_effect = ValueError("bad json")

            resp = await client.post(
                "/api/v1/ai/batch-parse",
                json={"command_text": "anything", "user_context": _user_context()},
                headers=pro_auth_headers,
            )
            assert resp.status_code == 500

    @pytest.mark.asyncio
    async def test_endpoint_rate_limits_after_burst(
        self, client: AsyncClient, pro_auth_headers: dict
    ):
        from app.routers.ai import batch_parse_rate_limiter

        batch_parse_rate_limiter._requests.clear()
        # Force the limiter to think 10 requests have already happened by
        # overriding max_requests for this test — easier than firing 10
        # real requests.
        original_max = batch_parse_rate_limiter.max_requests
        batch_parse_rate_limiter.max_requests = 1

        try:
            with patch("app.services.ai_productivity.parse_batch_command") as mock_parse:
                mock_parse.return_value = {
                    "mutations": [],
                    "confidence": 0.0,
                    "ambiguous_entities": [],
                }
                resp1 = await client.post(
                    "/api/v1/ai/batch-parse",
                    json={"command_text": "anything", "user_context": _user_context()},
                    headers=pro_auth_headers,
                )
                assert resp1.status_code == 200, resp1.text

                resp2 = await client.post(
                    "/api/v1/ai/batch-parse",
                    json={"command_text": "anything", "user_context": _user_context()},
                    headers=pro_auth_headers,
                )
                assert resp2.status_code == 429
        finally:
            batch_parse_rate_limiter.max_requests = original_max
            batch_parse_rate_limiter._requests.clear()
