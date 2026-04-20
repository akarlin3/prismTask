package com.averycorp.prismtask.ui.screens.templates

import com.averycorp.prismtask.domain.model.SelfCareRoutines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state tests for [TemplateSelections]. These guard the contract the
 * onboarding viewmodel depends on when it translates picks into repository
 * calls — empty selections map to "seed nothing", tier picks cumulate, and
 * per-step customization overrides the tier.
 */
class TemplateSelectionsTest {
    @Test
    fun default_effectiveStepIds_isEmpty() {
        val s = TemplateSelections()
        assertEquals(emptySet<String>(), s.effectiveStepIds("morning"))
        assertEquals(emptySet<String>(), s.effectiveStepIds("bedtime"))
        assertEquals(emptySet<String>(), s.effectiveStepIds("housework"))
    }

    @Test
    fun tierChoice_expandsCumulativelyViaTierIncludes() {
        val s = TemplateSelections(morningTier = "solid")
        val effective = s.effectiveStepIds("morning")

        val expected = SelfCareRoutines.morningSteps
            .filter { it.tier == "survival" || it.tier == "solid" }
            .map { it.id }
            .toSet()

        assertEquals(expected, effective)
    }

    @Test
    fun customStepIds_overrideTier() {
        val s = TemplateSelections(
            morningTier = "full",
            morningCustomStepIds = setOf("cleanser")
        )
        assertEquals(setOf("cleanser"), s.effectiveStepIds("morning"))
    }

    @Test
    fun withTier_clearsCustomOverride() {
        val s = TemplateSelections(
            morningTier = "full",
            morningCustomStepIds = setOf("cleanser")
        ).withTier("morning", "survival")

        assertEquals(null, s.morningCustomStepIds)
        assertEquals("survival", s.morningTier)
    }

    @Test
    fun withStepToggled_materializesCustomFromTier() {
        // Use a step id that actually lives in the survival tier so the toggle
        // removes it from the expanded set (post v1.4.0 default-template
        // expansion the morning routine is the broader Self-Care list).
        val toggledId = "sc_water"
        val s = TemplateSelections(morningTier = "survival")
            .withStepToggled("morning", toggledId)

        // Tier-expanded set included toggledId; toggling it should drop it
        // while materializing the rest of the survival set as the base.
        val expected = SelfCareRoutines.morningSteps
            .filter { it.tier == "survival" && it.id != toggledId }
            .map { it.id }
            .toSet()
        assertEquals(expected, s.morningCustomStepIds)
        // effectiveStepIds should now mirror the custom override.
        assertEquals(expected, s.effectiveStepIds("morning"))
    }

    @Test
    fun withStepToggled_addsStepIfAbsent() {
        val s = TemplateSelections().withStepToggled("housework", "dishes")
        assertEquals(setOf("dishes"), s.houseworkCustomStepIds)
    }

    @Test
    fun withMusicToggled_flipsMembership() {
        val once = TemplateSelections().withMusicToggled("guitar")
        assertEquals(setOf("guitar"), once.musicIds)
        val twice = once.withMusicToggled("guitar")
        assertTrue(twice.musicIds.isEmpty())
    }

    @Test
    fun unknownRoutineType_returnsEmpty() {
        val s = TemplateSelections()
        assertEquals(emptySet<String>(), s.effectiveStepIds("nonsense"))
    }
}
