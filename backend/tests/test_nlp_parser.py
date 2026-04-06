import json
import sys
import types
from datetime import date
from unittest.mock import MagicMock, patch

import pytest


def _make_mock_response(data: dict) -> MagicMock:
    content_block = MagicMock()
    content_block.text = json.dumps(data)
    message = MagicMock()
    message.content = [content_block]
    return message


@pytest.fixture(autouse=True)
def mock_anthropic_module():
    """Ensure a mock anthropic module is available for testing."""
    mock_mod = types.ModuleType("anthropic")
    mock_mod.Anthropic = MagicMock  # type: ignore
    mock_mod.APIError = Exception  # type: ignore
    sys.modules["anthropic"] = mock_mod

    # Re-import to pick up the mock
    import importlib
    import app.services.nlp_parser
    importlib.reload(app.services.nlp_parser)

    yield mock_mod

    # Cleanup
    if "anthropic" in sys.modules and sys.modules["anthropic"] is mock_mod:
        del sys.modules["anthropic"]
    importlib.reload(app.services.nlp_parser)


class TestNlpParser:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_successful_parse(self):
        from app.services.nlp_parser import parse_task_input

        with patch("app.services.nlp_parser.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "title": "Buy groceries",
                "project_suggestion": "Personal",
                "due_date": "2026-04-07",
                "priority": 3,
                "parent_task_suggestion": None,
                "confidence": 0.9,
            })

            result = parse_task_input("Buy groceries tomorrow", ["Personal", "Work"], date(2026, 4, 6))

            assert result.title == "Buy groceries"
            assert result.project_suggestion == "Personal"
            assert str(result.due_date) == "2026-04-07"
            assert result.priority == 3
            assert result.confidence == 0.9

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_title_only_input(self):
        from app.services.nlp_parser import parse_task_input

        with patch("app.services.nlp_parser.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "title": "Fix the bug",
                "project_suggestion": None,
                "due_date": None,
                "priority": None,
                "parent_task_suggestion": None,
                "confidence": 0.7,
            })

            result = parse_task_input("Fix the bug", [], date(2026, 4, 6))

            assert result.title == "Fix the bug"
            assert result.project_suggestion is None
            assert result.due_date is None
            assert result.priority is None

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_all_fields_parsed(self):
        from app.services.nlp_parser import parse_task_input

        with patch("app.services.nlp_parser.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "title": "Deploy v2",
                "project_suggestion": "Backend",
                "due_date": "2026-04-10",
                "priority": 1,
                "parent_task_suggestion": "Release preparation",
                "confidence": 0.95,
            })

            result = parse_task_input(
                "Urgently deploy v2 for Backend by Friday",
                ["Backend", "Frontend"],
                date(2026, 4, 6),
            )

            assert result.title == "Deploy v2"
            assert result.project_suggestion == "Backend"
            assert result.priority == 1
            assert result.parent_task_suggestion == "Release preparation"

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_empty_string_error(self):
        from app.services.nlp_parser import parse_task_input

        with pytest.raises(ValueError, match="Input text cannot be empty"):
            parse_task_input("", [], date(2026, 4, 6))

        with pytest.raises(ValueError, match="Input text cannot be empty"):
            parse_task_input("   ", [], date(2026, 4, 6))

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_malformed_json_retry(self):
        from app.services.nlp_parser import parse_task_input

        with patch("app.services.nlp_parser.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "not valid json {{"
            bad_response.content = [bad_content]

            good_response = _make_mock_response({
                "title": "Test task",
                "project_suggestion": None,
                "due_date": None,
                "priority": None,
                "parent_task_suggestion": None,
                "confidence": 0.8,
            })

            mock_client.messages.create.side_effect = [bad_response, good_response]

            result = parse_task_input("Test task", [], date(2026, 4, 6))
            assert result.title == "Test task"
            assert mock_client.messages.create.call_count == 2

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_malformed_json_both_attempts_fail(self):
        from app.services.nlp_parser import parse_task_input

        with patch("app.services.nlp_parser.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "not valid json"
            bad_response.content = [bad_content]

            mock_client.messages.create.return_value = bad_response

            with pytest.raises(ValueError, match="Failed to parse NLP response"):
                parse_task_input("Test task", [], date(2026, 4, 6))

    def test_missing_api_key(self):
        from app.services.nlp_parser import parse_task_input

        with patch.dict("os.environ", {}, clear=True):
            with pytest.raises(RuntimeError, match="ANTHROPIC_API_KEY"):
                parse_task_input("Test task", [], date(2026, 4, 6))
