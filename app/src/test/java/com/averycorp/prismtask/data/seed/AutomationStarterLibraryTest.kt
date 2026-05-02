package com.averycorp.prismtask.data.seed

import com.averycorp.prismtask.domain.automation.AutomationJsonAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parse-smoke + structural coverage for the starter library.
 *
 * The Phase 3 STOP-condition from `AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md`
 * § Phase 3.2 says "every rule in the library parses to a valid
 * AutomationRule. Failure of even one rule is a P0 block." That gate is
 * enforced here — every trigger/condition/actions tuple is round-tripped
 * through [AutomationJsonAdapter], so this test fails loud if any rule
 * uses a node shape the adapter can't handle.
 */
class AutomationStarterLibraryTest {

    @Test fun inventory_size_matches_audit_doc() {
        // Audit doc § A4 ships 27 rules. Update both this number and the
        // doc together when content changes.
        assertEquals(27, AutomationStarterLibrary.ALL_TEMPLATES.size)
    }

    @Test fun every_template_has_unique_id() {
        val ids = AutomationStarterLibrary.ALL_TEMPLATES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun every_template_round_trips_through_json_adapter() {
        for (t in AutomationStarterLibrary.ALL_TEMPLATES) {
            val triggerJson = AutomationJsonAdapter.encodeTrigger(t.trigger)
            val triggerDecoded = AutomationJsonAdapter.decodeTrigger(triggerJson)
            assertEquals("trigger round-trip mismatch for ${t.id}", t.trigger, triggerDecoded)

            val conditionJson = AutomationJsonAdapter.encodeCondition(t.condition)
            val conditionDecoded = AutomationJsonAdapter.decodeCondition(conditionJson)
            assertEquals("condition round-trip mismatch for ${t.id}", t.condition, conditionDecoded)

            val actionsJson = AutomationJsonAdapter.encodeActions(t.actions)
            val actionsDecoded = AutomationJsonAdapter.decodeActions(actionsJson)
            assertEquals(
                "actions count mismatch for ${t.id}",
                t.actions.size, actionsDecoded.size
            )
            // Every decoded action's type matches the original — value-shape
            // round-trip for action payloads is covered in AutomationJsonAdapterTest.
            t.actions.zip(actionsDecoded).forEach { (orig, decoded) ->
                assertEquals(
                    "action type mismatch in ${t.id}",
                    orig.type, decoded.type
                )
            }
        }
    }

    @Test fun every_category_has_at_least_one_template() {
        val byCategory = AutomationStarterLibrary.TEMPLATES_BY_CATEGORY
        for (cat in AutomationTemplateCategory.values()) {
            val rules = byCategory[cat]
            assertNotNull("category ${cat.name} is empty", rules)
            assertTrue(
                "category ${cat.name} has no rules",
                rules!!.isNotEmpty()
            )
        }
    }

    @Test fun first_install_seed_subset_matches_audit_doc() {
        // Audit § A8 (commit 4 description) says first-install seeding
        // preserves the original 5 PR #1056 templateKeys so existing
        // users see no diff. Verify the subset is exactly 5 + matches
        // the historical builtin.* keys.
        val seed = AutomationStarterLibrary.FIRST_INSTALL_SEED_IDS
        assertEquals(5, seed.size)
        val expected = setOf(
            "builtin.notify_overdue_urgent",
            "builtin.morning_routine",
            "builtin.streak_achievement",
            "builtin.autotag_today",
            "builtin.ai_summary_completions"
        )
        assertEquals(expected, seed)
    }

    @Test fun seed_ids_resolve_to_real_templates() {
        for (id in AutomationStarterLibrary.FIRST_INSTALL_SEED_IDS) {
            assertNotNull(
                "seed id $id has no matching template",
                AutomationStarterLibrary.findById(id)
            )
        }
    }

    @Test fun aiTemplates_correctly_flagged() {
        val aiIds = AutomationStarterLibrary.ALL_TEMPLATES
            .filter { it.requiresAi }
            .map { it.id }
            .toSet()
        // Per audit § A4 — the four AI-action rules.
        val expectedAi = setOf(
            "builtin.ai_summary_completions",
            "starter.med.weekly_ai_summary",
            "starter.power.manual_ai_briefing",
            "starter.power.daily_ai_eod_summary",
            "starter.power.weekly_ai_reflection"
        )
        assertEquals(expectedAi, aiIds)
    }
}
