"""Tests for tier-based AI model selection."""

from unittest.mock import MagicMock, patch


def test_get_model_free_returns_haiku():
    from app.services.ai_productivity import get_model, MODEL_HAIKU
    assert get_model("FREE") == MODEL_HAIKU


def test_get_model_pro_returns_haiku():
    from app.services.ai_productivity import get_model, MODEL_HAIKU
    assert get_model("PRO") == MODEL_HAIKU


def test_get_model_premium_returns_haiku():
    from app.services.ai_productivity import get_model, MODEL_HAIKU
    assert get_model("PREMIUM") == MODEL_HAIKU


def test_get_model_ultra_returns_sonnet():
    from app.services.ai_productivity import get_model, MODEL_SONNET
    assert get_model("ULTRA") == MODEL_SONNET


def test_eisenhower_with_ultra_uses_sonnet_model():
    from datetime import date
    from app.services.ai_productivity import MODEL_SONNET
    mock_client = MagicMock()
    mock_message = MagicMock()
    mock_message.content = [MagicMock(text='[{"task_id": 1, "quadrant": "Q1", "reason": "test"}]')]
    mock_client.messages.create.return_value = mock_message
    with patch("app.services.ai_productivity._get_client", return_value=mock_client):
        from app.services.ai_productivity import categorize_eisenhower
        result = categorize_eisenhower(
            [{"task_id": 1, "title": "Test task", "priority": 3}],
            date(2026, 4, 12), tier="ULTRA",
        )
    assert mock_client.messages.create.call_args.kwargs["model"] == MODEL_SONNET
    assert len(result) == 1


def test_nlp_parse_with_ultra_includes_suggestions_prompt():
    from datetime import date
    mock_client = MagicMock()
    mock_message = MagicMock()
    mock_message.content = [MagicMock(text='{"title": "Buy groceries", "confidence": 0.9, "suggestions": ["Create shopping list"]}')]
    mock_client.messages.create.return_value = mock_message
    with patch("app.services.nlp_parser.anthropic") as mock_anthropic, \
         patch.dict("os.environ", {"ANTHROPIC_API_KEY": "test-key"}):
        mock_anthropic.Anthropic.return_value = mock_client
        from app.services.nlp_parser import parse_task_input
        result = parse_task_input("Buy groceries", [], date(2026, 4, 12), tier="ULTRA")
    assert result.title == "Buy groceries"
    assert result.suggestions is not None
    assert "suggestions" in mock_client.messages.create.call_args.kwargs["messages"][0]["content"]
