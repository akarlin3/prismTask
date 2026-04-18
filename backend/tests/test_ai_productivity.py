import json
import sys
import types
from datetime import date
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from httpx import AsyncClient


def _make_mock_response(data) -> MagicMock:
    content_block = MagicMock()
    content_block.text = json.dumps(data)
    message = MagicMock()
    message.content = [content_block]
    return message


def _fake_task_dto(task_id: str = "task-1", title: str = "Test task", **overrides):
    """Build a TaskDTO with sensible defaults for router-level tests."""
    from app.services.firestore_tasks import TaskDTO

    fields = {
        "task_id": task_id,
        "title": title,
        "description": None,
        "due_date": None,
        "due_time": None,
        "planned_date": None,
        "priority": 0,
        "project_id": None,
        "eisenhower_quadrant": None,
        "urgency_score": 0.0,
        "sort_order": 0,
        "is_recurring": False,
        "completed_at": None,
    }
    fields.update(overrides)
    return TaskDTO(**fields)


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


class TestEisenhowerService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_categorize_eisenhower_success(self):
        from app.services.ai_productivity import categorize_eisenhower

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response([
                {"task_id": 1, "quadrant": "Q1", "reason": "Due tomorrow"},
                {"task_id": 2, "quadrant": "Q2", "reason": "Long-term goal"},
            ])

            tasks = [
                {"task_id": 1, "title": "Fix bug", "due_date": "2026-04-11", "priority": 1},
                {"task_id": 2, "title": "Learn Kotlin", "due_date": None, "priority": 2},
            ]
            result = categorize_eisenhower(tasks, date(2026, 4, 10))

            assert len(result) == 2
            assert result[0]["quadrant"] == "Q1"
            assert result[1]["quadrant"] == "Q2"

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_categorize_eisenhower_malformed_retry(self):
        from app.services.ai_productivity import categorize_eisenhower

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "not valid json"
            bad_response.content = [bad_content]

            good_response = _make_mock_response([
                {"task_id": 1, "quadrant": "Q1", "reason": "Urgent"},
            ])

            mock_client.messages.create.side_effect = [bad_response, good_response]

            result = categorize_eisenhower(
                [{"task_id": 1, "title": "Test"}], date(2026, 4, 10)
            )
            assert result[0]["quadrant"] == "Q1"
            assert mock_client.messages.create.call_count == 2


class TestPomodoroService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_plan_pomodoro_success(self):
        from app.services.ai_productivity import plan_pomodoro

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "sessions": [
                    {
                        "session_number": 1,
                        "tasks": [{"task_id": 1, "title": "Write report", "allocated_minutes": 25}],
                        "rationale": "Most urgent task",
                    }
                ],
                "total_sessions": 1,
                "total_work_minutes": 25,
                "total_break_minutes": 0,
                "skipped_tasks": [],
            })

            tasks = [{"task_id": 1, "title": "Write report", "due_date": "2026-04-11"}]
            result = plan_pomodoro(
                tasks=tasks,
                available_minutes=60,
                session_length=25,
                break_length=5,
                long_break_length=15,
                focus_preference="balanced",
                today=date(2026, 4, 10),
            )

            assert result["total_sessions"] == 1
            assert len(result["sessions"]) == 1
            assert result["sessions"][0]["tasks"][0]["task_id"] == 1

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_plan_pomodoro_malformed_both_fail(self):
        from app.services.ai_productivity import plan_pomodoro

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "{{invalid}}"
            bad_response.content = [bad_content]
            mock_client.messages.create.return_value = bad_response

            with pytest.raises(ValueError, match="Failed to parse AI response"):
                plan_pomodoro(
                    tasks=[{"task_id": 1, "title": "Test"}],
                    available_minutes=60,
                    session_length=25,
                    break_length=5,
                    long_break_length=15,
                    focus_preference="balanced",
                    today=date(2026, 4, 10),
                )


class TestAIEndpoints:
    @pytest.mark.asyncio
    async def test_eisenhower_endpoint(self, client: AsyncClient, pro_auth_headers: dict):
        auth_headers = pro_auth_headers
        fake_tasks = [_fake_task_dto(task_id="abc123", title="Test task")]

        with patch("app.routers.ai.ai_rate_limiter"), \
             patch(
                 "app.routers.ai.fetch_incomplete_tasks",
                 new=AsyncMock(return_value=fake_tasks),
             ), \
             patch("app.services.ai_productivity.categorize_eisenhower") as mock_cat:
            mock_cat.return_value = [
                {"task_id": "abc123", "quadrant": "Q1", "reason": "Urgent task"},
            ]
            resp = await client.post(
                "/api/v1/ai/eisenhower",
                json={},
                headers=auth_headers,
            )
            assert resp.status_code == 200, resp.text
            data = resp.json()
            assert "categorizations" in data
            assert "summary" in data
            assert data["categorizations"][0]["task_id"] == "abc123"

    @pytest.mark.asyncio
    async def test_pomodoro_endpoint(self, client: AsyncClient, pro_auth_headers: dict):
        auth_headers = pro_auth_headers
        fake_tasks = [_fake_task_dto(task_id="pom-1", title="Focus task")]

        with patch("app.routers.ai.ai_rate_limiter"), \
             patch(
                 "app.routers.ai.fetch_incomplete_tasks",
                 new=AsyncMock(return_value=fake_tasks),
             ), \
             patch("app.services.ai_productivity.plan_pomodoro") as mock_plan:
            mock_plan.return_value = {
                "sessions": [
                    {
                        "session_number": 1,
                        "tasks": [{"task_id": "pom-1", "title": "Focus task", "allocated_minutes": 25}],
                        "rationale": "Only task available",
                    }
                ],
                "total_sessions": 1,
                "total_work_minutes": 25,
                "total_break_minutes": 0,
                "skipped_tasks": [],
            }
            resp = await client.post(
                "/api/v1/ai/pomodoro-plan",
                json={"available_minutes": 60},
                headers=auth_headers,
            )
            assert resp.status_code == 200, resp.text
            data = resp.json()
            assert data["total_sessions"] == 1

    @pytest.mark.asyncio
    async def test_rate_limiting(self, client: AsyncClient, pro_auth_headers: dict):
        auth_headers = pro_auth_headers
        from app.routers.ai import ai_rate_limiter
        ai_rate_limiter._requests.clear()

        with patch(
            "app.routers.ai.fetch_incomplete_tasks",
            new=AsyncMock(return_value=[]),
        ):
            # First call should work (empty-tasks short-circuit returns 200)
            resp = await client.post(
                "/api/v1/ai/eisenhower",
                json={},
                headers=auth_headers,
            )
            assert resp.status_code == 200, resp.text

            # Second call within window should be rate limited
            resp2 = await client.post(
                "/api/v1/ai/eisenhower",
                json={},
                headers=auth_headers,
            )
            assert resp2.status_code == 429


class TestDailyBriefingService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_generate_daily_briefing_success(self):
        from app.services.ai_productivity import generate_daily_briefing

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "greeting": "Good morning! Moderate day ahead with 5 tasks.",
                "top_priorities": [
                    {"task_id": 1, "title": "Fix bug", "reason": "Due today, high priority"},
                    {"task_id": 2, "title": "Write report", "reason": "Blocks other work"},
                    {"task_id": 3, "title": "Reply to emails", "reason": "Quick win"},
                ],
                "heads_up": ["2 overdue tasks from yesterday"],
                "suggested_order": [
                    {"task_id": 1, "title": "Fix bug", "suggested_time": "9:00 AM", "reason": "Hardest first"},
                    {"task_id": 2, "title": "Write report", "suggested_time": "10:30 AM", "reason": "Focus time"},
                ],
                "habit_reminders": ["Exercise", "Read"],
                "day_type": "moderate",
            })

            result = generate_daily_briefing(
                today=date(2026, 4, 10),
                overdue_tasks=[{"task_id": 4, "title": "Old task"}],
                today_tasks=[{"task_id": 1, "title": "Fix bug"}],
                planned_tasks=[{"task_id": 2, "title": "Write report"}],
                habits=[{"name": "Exercise", "frequency": "daily"}],
                completed_tasks=[{"task_id": 5, "title": "Done task"}],
            )

            assert result["day_type"] == "moderate"
            assert len(result["top_priorities"]) == 3
            assert result["top_priorities"][0]["task_id"] == 1
            assert len(result["suggested_order"]) == 2

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_generate_daily_briefing_malformed_retry(self):
        from app.services.ai_productivity import generate_daily_briefing

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "not json"
            bad_response.content = [bad_content]

            good_response = _make_mock_response({
                "greeting": "Good morning!",
                "top_priorities": [],
                "heads_up": [],
                "suggested_order": [],
                "habit_reminders": [],
                "day_type": "light",
            })

            mock_client.messages.create.side_effect = [bad_response, good_response]

            result = generate_daily_briefing(
                today=date(2026, 4, 10),
                overdue_tasks=[],
                today_tasks=[],
                planned_tasks=[],
                habits=[],
                completed_tasks=[],
            )
            assert result["day_type"] == "light"
            assert mock_client.messages.create.call_count == 2


class TestWeeklyPlanService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_generate_weekly_plan_success(self):
        from app.services.ai_productivity import generate_weekly_plan

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "plan": {
                    "Monday": {
                        "date": "2026-04-13",
                        "tasks": [
                            {"task_id": 1, "title": "Write report", "suggested_time": "9:00 AM",
                             "duration_minutes": 60, "reason": "Due Tuesday"},
                        ],
                        "total_hours": 1.0,
                        "calendar_events": [],
                        "habits": ["Exercise"],
                    },
                },
                "unscheduled": [
                    {"task_id": 5, "title": "Low priority", "reason": "Defer to next week"},
                ],
                "week_summary": "Light week with 3 tasks.",
                "tips": ["Focus on the report Monday morning"],
            })

            result = generate_weekly_plan(
                week_start=date(2026, 4, 13),
                week_end=date(2026, 4, 19),
                work_days=["MO", "TU", "WE", "TH", "FR"],
                focus_hours_per_day=6,
                prefer_front_loading=True,
                tasks=[{"task_id": 1, "title": "Write report", "due_date": "2026-04-14", "priority": 2}],
                recurring_tasks=[],
            )

            assert "Monday" in result["plan"]
            assert len(result["plan"]["Monday"]["tasks"]) == 1
            assert result["plan"]["Monday"]["tasks"][0]["task_id"] == 1
            assert len(result["unscheduled"]) == 1
            assert result["week_summary"] == "Light week with 3 tasks."

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_generate_weekly_plan_both_fail(self):
        from app.services.ai_productivity import generate_weekly_plan

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "{{not valid}}"
            bad_response.content = [bad_content]
            mock_client.messages.create.return_value = bad_response

            with pytest.raises(ValueError, match="Failed to parse AI response"):
                generate_weekly_plan(
                    week_start=date(2026, 4, 13),
                    week_end=date(2026, 4, 19),
                    work_days=["MO", "TU", "WE", "TH", "FR"],
                    focus_hours_per_day=6,
                    prefer_front_loading=True,
                    tasks=[{"task_id": 1, "title": "Test"}],
                    recurring_tasks=[],
                )


class TestTimeBlockService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_generate_time_blocks_success(self):
        from app.services.ai_productivity import generate_time_blocks

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "schedule": [
                    {"start": "09:00", "end": "09:30", "type": "task", "task_id": 1,
                     "title": "Write report", "reason": "Deep work while fresh"},
                    {"start": "09:30", "end": "10:00", "type": "event", "task_id": None,
                     "title": "Team standup", "reason": "Fixed calendar event"},
                    {"start": "10:00", "end": "10:15", "type": "break", "task_id": None,
                     "title": "Break", "reason": "Recovery after work block"},
                ],
                "unscheduled_tasks": [
                    {"task_id": 3, "title": "Low priority task", "reason": "Not enough time"},
                ],
                "stats": {
                    "total_work_minutes": 30,
                    "total_break_minutes": 15,
                    "total_free_minutes": 495,
                    "tasks_scheduled": 1,
                    "tasks_deferred": 1,
                },
            })

            result = generate_time_blocks(
                target_date=date(2026, 4, 10),
                day_start="09:00",
                day_end="18:00",
                block_size_minutes=30,
                include_breaks=True,
                break_frequency_minutes=90,
                break_duration_minutes=15,
                tasks=[{"task_id": 1, "title": "Write report"}],
                calendar_events=[{"title": "Team standup", "start": "09:30", "end": "10:00"}],
            )

            assert len(result["schedule"]) == 3
            assert result["schedule"][0]["type"] == "task"
            assert result["schedule"][1]["type"] == "event"
            assert result["schedule"][2]["type"] == "break"
            assert result["stats"]["tasks_scheduled"] == 1

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_generate_time_blocks_preserves_calendar_events(self):
        from app.services.ai_productivity import generate_time_blocks

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "schedule": [
                    {"start": "09:00", "end": "09:30", "type": "task", "task_id": 1,
                     "title": "Task before meeting", "reason": "Morning slot"},
                    {"start": "10:00", "end": "11:00", "type": "event", "task_id": None,
                     "title": "Team meeting", "reason": "Fixed calendar event"},
                    {"start": "11:00", "end": "11:30", "type": "task", "task_id": 2,
                     "title": "Task after meeting", "reason": "After meeting slot"},
                ],
                "unscheduled_tasks": [],
                "stats": {
                    "total_work_minutes": 60,
                    "total_break_minutes": 0,
                    "total_free_minutes": 480,
                    "tasks_scheduled": 2,
                    "tasks_deferred": 0,
                },
            })

            result = generate_time_blocks(
                target_date=date(2026, 4, 10),
                day_start="09:00",
                day_end="18:00",
                block_size_minutes=30,
                include_breaks=False,
                break_frequency_minutes=90,
                break_duration_minutes=15,
                tasks=[
                    {"task_id": 1, "title": "Task before meeting"},
                    {"task_id": 2, "title": "Task after meeting"},
                ],
                calendar_events=[{"title": "Team meeting", "start": "10:00", "end": "11:00"}],
            )

            # Verify calendar event is in the schedule
            event_blocks = [b for b in result["schedule"] if b["type"] == "event"]
            assert len(event_blocks) == 1
            assert event_blocks[0]["title"] == "Team meeting"
            assert result["stats"]["tasks_scheduled"] == 2


class TestNewAIEndpoints:
    @pytest.mark.asyncio
    async def test_daily_briefing_rate_limiting(self, client: AsyncClient, pro_auth_headers: dict):
        auth_headers = pro_auth_headers
        from app.routers.ai import briefing_rate_limiter
        briefing_rate_limiter._requests.clear()

        with patch(
            "app.routers.ai.fetch_incomplete_tasks",
            new=AsyncMock(return_value=[]),
        ), patch(
            "app.routers.ai.fetch_recently_completed_tasks",
            new=AsyncMock(return_value=[]),
        ):
            # First call should succeed (empty briefing)
            resp = await client.post(
                "/api/v1/ai/daily-briefing",
                json={},
                headers=auth_headers,
            )
            assert resp.status_code == 200, resp.text

            # Second call within 1-hour window should be rate limited
            resp2 = await client.post(
                "/api/v1/ai/daily-briefing",
                json={},
                headers=auth_headers,
            )
            assert resp2.status_code == 429

    @pytest.mark.asyncio
    async def test_weekly_plan_rate_limiting(self, client: AsyncClient, pro_auth_headers: dict):
        auth_headers = pro_auth_headers
        from app.routers.ai import weekly_plan_rate_limiter
        weekly_plan_rate_limiter._requests.clear()

        with patch(
            "app.routers.ai.fetch_incomplete_tasks",
            new=AsyncMock(return_value=[]),
        ):
            # First call should succeed (empty plan)
            resp = await client.post(
                "/api/v1/ai/weekly-plan",
                json={},
                headers=auth_headers,
            )
            assert resp.status_code == 200, resp.text

            # Second call within 30-min window should be rate limited
            resp2 = await client.post(
                "/api/v1/ai/weekly-plan",
                json={},
                headers=auth_headers,
            )
            assert resp2.status_code == 429

    @pytest.mark.asyncio
    async def test_time_block_rate_limiting(self, client: AsyncClient, pro_auth_headers: dict):
        auth_headers = pro_auth_headers
        from app.routers.ai import time_block_rate_limiter
        time_block_rate_limiter._requests.clear()

        with patch(
            "app.routers.ai.fetch_incomplete_tasks",
            new=AsyncMock(return_value=[]),
        ):
            # First call should succeed (empty schedule)
            resp = await client.post(
                "/api/v1/ai/time-block",
                json={},
                headers=auth_headers,
            )
            assert resp.status_code == 200, resp.text

            # Second call within 15-min window should be rate limited
            resp2 = await client.post(
                "/api/v1/ai/time-block",
                json={},
                headers=auth_headers,
            )
            assert resp2.status_code == 429


class TestWeeklyReviewService:
    """The prompt assembly is the only non-trivial logic here — verify that
    all four input sections land in the Sonnet prompt."""

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_prompt_includes_all_four_sections(self):
        from app.services.ai_productivity import generate_weekly_review

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "wins": ["Shipped the payment fix"],
                "slips": ["Dishes never got done"],
                "patterns": ["Q2 work slipped"],
                "next_week_focus": ["Block Tuesday AM for the migration"],
                "narrative": "Solid week, with one recurring slip pattern.",
            })

            completed = [
                {"task_id": "c1", "title": "Ship payment fix", "priority": 3,
                 "eisenhower_quadrant": "Q1", "life_category": "work",
                 "completed_at": "2026-04-15T10:00:00"},
            ]
            slipped = [
                {"task_id": "s1", "title": "Do the dishes", "priority": 1,
                 "life_category": "personal"},
            ]
            open_tasks = [
                {"task_id": "o1", "title": "Finish DB migration", "priority": 4,
                 "eisenhower_quadrant": "Q2", "due_date": "2026-04-22"},
            ]

            result = generate_weekly_review(
                week_start="2026-04-13",
                week_end="2026-04-19",
                completed_tasks=completed,
                slipped_tasks=slipped,
                open_tasks=open_tasks,
                habit_summary={"exercise_streak": 4, "meditation_rate": 0.71},
                pomodoro_summary={"total_minutes": 480, "sessions": 12},
                notes="Felt scattered on Wednesday but recovered.",
                tier="PRO",
            )

            assert result["narrative"].startswith("Solid week")
            assert result["patterns"] == ["Q2 work slipped"]
            assert result["next_week_focus"] == ["Block Tuesday AM for the migration"]

            # Inspect the prompt Sonnet received.
            call_args = mock_client.messages.create.call_args
            prompt = call_args.kwargs["messages"][0]["content"]

            # Section 1: week bounds
            assert "2026-04-13 to 2026-04-19" in prompt

            # Section 2: completed tasks (header with count + the task title)
            assert "completed 1 task" in prompt
            assert "Ship payment fix" in prompt
            assert "Q1" in prompt  # quadrant metadata

            # Section 3: slipped tasks
            assert "1 task(s) that slipped" in prompt
            assert "Do the dishes" in prompt

            # Section 4: open tasks
            assert "Currently open on their plate" in prompt
            assert "Finish DB migration" in prompt
            assert "due 2026-04-22" in prompt

            # Opaque pass-through blocks
            assert "exercise_streak" in prompt
            assert "total_minutes" in prompt

            # User notes
            assert "Felt scattered on Wednesday" in prompt

            # Sonnet, not Haiku (weekly review runs on monthly_review model).
            from app.services.ai_productivity import MODEL_SONNET
            assert call_args.kwargs["model"] == MODEL_SONNET

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_prompt_handles_empty_inputs_gracefully(self):
        """A week with no completed/slipped/open tasks and no summaries
        should still produce a valid prompt (no KeyError, no crash)."""
        from app.services.ai_productivity import generate_weekly_review

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "wins": [],
                "slips": [],
                "patterns": [],
                "next_week_focus": [],
                "narrative": "A quiet week with nothing logged.",
            })

            generate_weekly_review(
                week_start="2026-04-13",
                week_end="2026-04-19",
                completed_tasks=[],
                slipped_tasks=[],
                open_tasks=[],
                habit_summary=None,
                pomodoro_summary=None,
                notes=None,
                tier="PRO",
            )

            prompt = mock_client.messages.create.call_args.kwargs["messages"][0]["content"]
            # Empty sections render as "(none)" / "(not provided)" so the
            # model doesn't see dangling headers.
            assert "completed 0 task" in prompt
            assert "0 task(s) that slipped" in prompt
            assert "(none)" in prompt
            assert "(not provided)" in prompt
