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
    def test_tag_change_add_round_trips_through_service(self):
        """Service preserves the TAG_CHANGE proposed_new_values shape so the
        Android client can read tags_added/tags_removed verbatim."""
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
                            "mutation_type": "TAG_CHANGE",
                            "proposed_new_values": {
                                "tags_added": ["personal"],
                                "tags_removed": [],
                            },
                            "human_readable_description": "Tag 'Sprint planning prep' as #personal",
                        }
                    ],
                    "confidence": 0.92,
                    "ambiguous_entities": [],
                }
            )

            result = parse_batch_command(
                "tag sprint planning as #personal",
                _user_context(),
                tier="PRO",
            )
            mutation = result["mutations"][0]
            assert mutation["mutation_type"] == "TAG_CHANGE"
            assert mutation["proposed_new_values"]["tags_added"] == ["personal"]
            assert mutation["proposed_new_values"]["tags_removed"] == []

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_tag_change_remove_round_trips_through_service(self):
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
                            "mutation_type": "TAG_CHANGE",
                            "proposed_new_values": {
                                "tags_added": [],
                                "tags_removed": ["work"],
                            },
                            "human_readable_description": "Untag #work from 'Sprint planning prep'",
                        }
                    ],
                    "confidence": 0.95,
                    "ambiguous_entities": [],
                }
            )

            result = parse_batch_command(
                "untag #work from sprint planning",
                _user_context(),
                tier="PRO",
            )
            mutation = result["mutations"][0]
            assert mutation["proposed_new_values"]["tags_removed"] == ["work"]
            assert mutation["proposed_new_values"]["tags_added"] == []

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_tag_change_combined_add_and_remove_in_single_mutation(self):
        """A "replace #urgent with #later" command should land as a single
        TAG_CHANGE with both lists populated — matches Android's apply
        path which processes adds and removes in one transaction."""
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
                            "mutation_type": "TAG_CHANGE",
                            "proposed_new_values": {
                                "tags_added": ["later"],
                                "tags_removed": ["urgent"],
                            },
                            "human_readable_description": "Swap #urgent for #later",
                        }
                    ],
                    "confidence": 0.88,
                    "ambiguous_entities": [],
                }
            )

            result = parse_batch_command(
                "replace #urgent with #later on overdue tasks",
                _user_context(),
                tier="PRO",
            )
            assert len(result["mutations"]) == 1
            mutation = result["mutations"][0]
            assert mutation["proposed_new_values"]["tags_added"] == ["later"]
            assert mutation["proposed_new_values"]["tags_removed"] == ["urgent"]

    def test_system_prompt_documents_tag_change_schema(self):
        """Regression net for the prompt template — if someone drops the
        TAG_CHANGE bullet or its schema, batch tag commands silently stop
        working. This test makes that loud."""
        from app.services.ai_productivity import _BATCH_PARSE_SYSTEM_PROMPT

        assert "TAG_CHANGE" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "TAG_CHANGE missing from the mutation_type union"
        )
        assert "tags_added" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "tags_added missing from the proposed_new_values schema"
        )
        assert "tags_removed" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "tags_removed missing from the proposed_new_values schema"
        )

    def test_system_prompt_documents_medication_complete_and_skip(self):
        """Haiku must keep COMPLETE/SKIP on MEDICATION in the schema list
        so medication batch commands don't silently regress to TASK-only."""
        from app.services.ai_productivity import _BATCH_PARSE_SYSTEM_PROMPT

        assert "COMPLETE on" in _BATCH_PARSE_SYSTEM_PROMPT and "MEDICATION" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "COMPLETE on MEDICATION missing from prompt"
        )
        assert "SKIP on" in _BATCH_PARSE_SYSTEM_PROMPT, "SKIP missing from prompt"
        assert "slot_key" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "slot_key field missing from medication COMPLETE/SKIP schema"
        )

    def test_system_prompt_documents_state_change_schema(self):
        """STATE_CHANGE is the medication-tier override mutation. The Android
        and web apply paths key off the literal token; if it falls out of the
        prompt's mutation_type union, the AI stops emitting it and tier
        overrides silently regress to TASK-only mutations."""
        from app.services.ai_productivity import _BATCH_PARSE_SYSTEM_PROMPT

        assert "STATE_CHANGE" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "STATE_CHANGE missing from the mutation_type union"
        )
        assert "STATE_CHANGE on MEDICATION" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "STATE_CHANGE on MEDICATION schema bullet missing"
        )
        assert "tier" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "tier field missing from STATE_CHANGE schema"
        )

    def test_system_prompt_documents_display_label(self):
        """The MEDICATIONS context schema must mention `display_label` so
        Haiku knows to consider it as a match target. If this falls out
        of the prompt, users with brand-name display labels (e.g.
        `name="Bupropion HCL XL"`, `display_label="Wellbutrin"`) will
        silently lose name resolution on spoken-phrase batch commands."""
        from app.services.ai_productivity import _BATCH_PARSE_SYSTEM_PROMPT

        assert "display_label" in _BATCH_PARSE_SYSTEM_PROMPT, (
            "display_label missing from MEDICATIONS context schema"
        )
        # The both-fields matching instruction guards against Haiku
        # treating `name` as the only match target.
        assert "display_label" in _BATCH_PARSE_SYSTEM_PROMPT and (
            "match" in _BATCH_PARSE_SYSTEM_PROMPT.lower()
        ), "display_label match instruction missing from prompt"

    def test_pydantic_accepts_state_change_mutation_type(self):
        """Schemas regex must include STATE_CHANGE — otherwise Pydantic
        rejects the AI response and the whole batch fails."""
        from app.schemas.ai import ProposedMutation

        # Must not raise
        ProposedMutation(
            entity_type="MEDICATION",
            entity_id="med-1",
            mutation_type="STATE_CHANGE",
            proposed_new_values={
                "tier": "skipped",
                "date": "2026-04-25",
                "slot_key": "morning",
            },
            human_readable_description="Mark morning tier as skipped",
        )

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_complete_on_medication_round_trips_with_slot_key(self):
        """COMPLETE on MEDICATION carries date + slot_key — the Android
        applyMedicationMutation path keys off both fields to match the
        right dose row."""
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [
                        {
                            "entity_type": "MEDICATION",
                            "entity_id": "med-1",
                            "mutation_type": "COMPLETE",
                            "proposed_new_values": {
                                "date": "2026-04-25",
                                "slot_key": "morning",
                            },
                            "human_readable_description": "Mark Adderall taken (morning)",
                        }
                    ],
                    "confidence": 0.96,
                    "ambiguous_entities": [],
                }
            )

            ctx = _user_context(medications=[{"id": "med-1", "name": "Adderall"}])
            result = parse_batch_command("took my morning Adderall", ctx, tier="PRO")
            mutation = result["mutations"][0]
            assert mutation["entity_type"] == "MEDICATION"
            assert mutation["mutation_type"] == "COMPLETE"
            assert mutation["proposed_new_values"]["slot_key"] == "morning"
            assert mutation["proposed_new_values"]["date"] == "2026-04-25"

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_skip_on_medication_round_trips_with_slot_key(self):
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [
                        {
                            "entity_type": "MEDICATION",
                            "entity_id": "med-1",
                            "mutation_type": "SKIP",
                            "proposed_new_values": {
                                "date": "2026-04-25",
                                "slot_key": "evening",
                            },
                            "human_readable_description": "Skip Adderall (evening)",
                        }
                    ],
                    "confidence": 0.92,
                    "ambiguous_entities": [],
                }
            )

            ctx = _user_context(medications=[{"id": "med-1", "name": "Adderall"}])
            result = parse_batch_command("skip my evening Adderall", ctx, tier="PRO")
            mutation = result["mutations"][0]
            assert mutation["mutation_type"] == "SKIP"
            assert mutation["proposed_new_values"]["slot_key"] == "evening"

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_state_change_on_medication_round_trips_with_tier(self):
        """STATE_CHANGE carries tier + date + slot_key — the Android apply
        path writes a MedicationTierStateEntity row with tier_source='user_set'."""
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [
                        {
                            "entity_type": "MEDICATION",
                            "entity_id": "med-1",
                            "mutation_type": "STATE_CHANGE",
                            "proposed_new_values": {
                                "tier": "prescription",
                                "date": "2026-04-25",
                                "slot_key": "evening",
                            },
                            "human_readable_description": (
                                "Mark Adderall (evening) as prescription tier"
                            ),
                        }
                    ],
                    "confidence": 0.85,
                    "ambiguous_entities": [],
                }
            )

            ctx = _user_context(medications=[{"id": "med-1", "name": "Adderall"}])
            result = parse_batch_command(
                "set evening Adderall to prescription tier", ctx, tier="PRO"
            )
            mutation = result["mutations"][0]
            assert mutation["mutation_type"] == "STATE_CHANGE"
            assert mutation["proposed_new_values"]["tier"] == "prescription"
            assert mutation["proposed_new_values"]["slot_key"] == "evening"

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_surfaces_ambiguity_for_medications(self):
        """When two medication names share a prefix (e.g. two Wellbutrins),
        Haiku's expected behavior is to populate `ambiguous_entities` with
        the candidates and either skip or low-confidence the mutation. The
        service must round-trip the ambiguous-entity shape verbatim — the
        Android client's auto-strip safeguard reads this list to decide
        which mutations to withhold from the preview."""
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
                            "phrase": "Wellbutrin",
                            "candidate_entity_type": "MEDICATION",
                            "candidate_entity_ids": ["med-1", "med-2"],
                            "note": "Two medications match 'Wellbutrin'",
                        }
                    ],
                }
            )

            ctx = _user_context(
                medications=[
                    {"id": "med-1", "name": "Wellbutrin XL 150mg"},
                    {"id": "med-2", "name": "Wellbutrin SR 100mg"},
                ]
            )
            result = parse_batch_command("took my Wellbutrin", ctx, tier="PRO")
            assert result["mutations"] == []
            assert len(result["ambiguous_entities"]) == 1
            hint = result["ambiguous_entities"][0]
            assert hint["candidate_entity_type"] == "MEDICATION"
            assert set(hint["candidate_entity_ids"]) == {"med-1", "med-2"}
            assert result["confidence"] == pytest.approx(0.4)

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_complete_on_medication_with_display_label_round_trips(self):
        """Haiku may match against `display_label` rather than `name`; the
        service must round-trip the resulting COMPLETE mutation faithfully.
        Haiku decides the matching, but the round-trip plumbing must
        preserve whatever entity_id Haiku picked."""
        from app.services.ai_productivity import parse_batch_command

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [
                        {
                            "entity_type": "MEDICATION",
                            "entity_id": "med-1",
                            "mutation_type": "COMPLETE",
                            "proposed_new_values": {
                                "date": "2026-04-25",
                                "slot_key": "morning",
                            },
                            "human_readable_description": (
                                "Mark Wellbutrin (morning) as taken"
                            ),
                        }
                    ],
                    "confidence": 0.94,
                    "ambiguous_entities": [],
                }
            )

            # display_label is the user-facing alias; canonical name is the
            # generic form. Spoken phrase "Wellbutrin" should resolve via
            # display_label per the both-fields match instruction.
            ctx = _user_context(
                medications=[
                    {
                        "id": "med-1",
                        "name": "Bupropion HCL XL 150mg",
                        "display_label": "Wellbutrin",
                    }
                ]
            )
            result = parse_batch_command(
                "took my morning Wellbutrin", ctx, tier="PRO"
            )
            mutation = result["mutations"][0]
            assert mutation["entity_id"] == "med-1"
            assert mutation["mutation_type"] == "COMPLETE"
            assert mutation["proposed_new_values"]["slot_key"] == "morning"
            # And the display_label landed in the prompt-bound payload.
            sent_payload = mock_client.messages.create.call_args.kwargs[
                "messages"
            ][0]["content"]
            assert "Wellbutrin" in sent_payload
            assert "display_label" in sent_payload

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

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_backend_drops_haiku_mutation_for_unknown_medication_id(self):
        """Defensive guard: if Haiku invents a MEDICATION entity_id that
        isn't in the user's medications list, drop the mutation. Closes
        the audit's failure-mode #1 (silent wrong-medication pick) even if
        the model ignores the system prompt's "never invent ids" rule.
        """
        from app.services.ai_productivity import parse_batch_command

        ctx = _user_context()
        ctx["medications"] = [
            {"id": "med-1", "name": "Wellbutrin", "display_label": None},
        ]

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [
                        {
                            "entity_type": "MEDICATION",
                            "entity_id": "med-99",
                            "mutation_type": "COMPLETE",
                            "proposed_new_values": {
                                "slot_key": "morning",
                                "date": "2026-04-23",
                            },
                            "human_readable_description": "took med-99",
                        },
                        {
                            "entity_type": "MEDICATION",
                            "entity_id": "med-1",
                            "mutation_type": "COMPLETE",
                            "proposed_new_values": {
                                "slot_key": "morning",
                                "date": "2026-04-23",
                            },
                            "human_readable_description": "took Wellbutrin",
                        },
                    ],
                    "confidence": 0.9,
                    "ambiguous_entities": [],
                }
            )

            result = parse_batch_command("took my Wellbutrin", ctx, tier="PRO")

        ids = [m["entity_id"] for m in result["mutations"]]
        assert ids == ["med-1"], (
            "MEDICATION mutation referencing a non-listed id must be dropped"
        )

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_backend_appends_forced_ambiguous_phrases_to_response(self):
        """Defensive guard: phrases the client classified as ambiguous get
        unconditionally appended to `ambiguous_entities` so the picker
        always surfaces them, even if Haiku returned a clean response."""
        from app.services.ai_productivity import parse_batch_command

        ctx = _user_context()
        ctx["medications"] = [
            {"id": "med-30", "name": "Wellbutrin"},
            {"id": "med-31", "name": "Wellbutrin"},
        ]
        ctx["forced_ambiguous_phrases"] = [
            {
                "phrase": "wellbutrin",
                "candidate_entity_type": "MEDICATION",
                "candidate_entity_ids": ["med-30", "med-31"],
            }
        ]

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [],
                    "confidence": 0.95,
                    "ambiguous_entities": [],
                }
            )

            result = parse_batch_command("took my Wellbutrin", ctx, tier="PRO")

        assert len(result["ambiguous_entities"]) == 1
        forced = result["ambiguous_entities"][0]
        assert forced["phrase"] == "wellbutrin"
        assert sorted(forced["candidate_entity_ids"]) == ["med-30", "med-31"]

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_backend_dedupes_forced_ambiguous_phrases(self):
        """If Haiku already returned the same (phrase, ids) pair the client
        forced, don't add a duplicate."""
        from app.services.ai_productivity import parse_batch_command

        ctx = _user_context()
        ctx["medications"] = [
            {"id": "med-30", "name": "Wellbutrin"},
            {"id": "med-31", "name": "Wellbutrin"},
        ]
        ctx["forced_ambiguous_phrases"] = [
            {
                "phrase": "wellbutrin",
                "candidate_entity_type": "MEDICATION",
                "candidate_entity_ids": ["med-30", "med-31"],
            }
        ]

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response(
                {
                    "mutations": [],
                    "confidence": 0.95,
                    "ambiguous_entities": [
                        {
                            "phrase": "wellbutrin",
                            "candidate_entity_type": "MEDICATION",
                            "candidate_entity_ids": ["med-30", "med-31"],
                            "note": "two matches",
                        }
                    ],
                }
            )

            result = parse_batch_command("took my Wellbutrin", ctx, tier="PRO")

        assert len(result["ambiguous_entities"]) == 1, (
            "duplicate (phrase, ids) entry must not be appended a second time"
        )

    def test_system_prompt_documents_committed_medication_matches(self):
        """The system prompt must call out the new authoritative-hint
        fields so Haiku honors them instead of re-doing the matching."""
        from app.services.ai_productivity import _BATCH_PARSE_SYSTEM_PROMPT

        assert "committed_medication_matches" in _BATCH_PARSE_SYSTEM_PROMPT
        assert "forced_ambiguous_phrases" in _BATCH_PARSE_SYSTEM_PROMPT


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
