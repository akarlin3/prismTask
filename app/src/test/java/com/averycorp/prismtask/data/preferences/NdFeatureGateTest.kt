package com.averycorp.prismtask.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NdFeatureGate].
 */
class NdFeatureGateTest {

    // region isAnyNdActive

    @Test
    fun `isAnyNdActive returns false when both modes off`() {
        val prefs = NdPreferences(adhdModeEnabled = false, calmModeEnabled = false)
        assertFalse(NdFeatureGate.isAnyNdActive(prefs))
    }

    @Test
    fun `isAnyNdActive returns true when ADHD mode on only`() {
        val prefs = NdPreferences(adhdModeEnabled = true, calmModeEnabled = false)
        assertTrue(NdFeatureGate.isAnyNdActive(prefs))
    }

    @Test
    fun `isAnyNdActive returns true when Calm mode on only`() {
        val prefs = NdPreferences(adhdModeEnabled = false, calmModeEnabled = true)
        assertTrue(NdFeatureGate.isAnyNdActive(prefs))
    }

    @Test
    fun `isAnyNdActive returns true when both modes on`() {
        val prefs = NdPreferences(adhdModeEnabled = true, calmModeEnabled = true)
        assertTrue(NdFeatureGate.isAnyNdActive(prefs))
    }

    // endregion

    // region requiresPro

    @Test
    fun `requiresPro returns true for ai_decomposition`() {
        assertTrue(NdFeatureGate.requiresPro(NdFeatureGate.AI_DECOMPOSITION))
    }

    @Test
    fun `requiresPro returns true for smart_nudges`() {
        assertTrue(NdFeatureGate.requiresPro(NdFeatureGate.SMART_NUDGES))
    }

    @Test
    fun `requiresPro returns false for manual_decomposition`() {
        assertFalse(NdFeatureGate.requiresPro("manual_decomposition"))
    }

    @Test
    fun `requiresPro returns false for focus_guard`() {
        assertFalse(NdFeatureGate.requiresPro("focus_guard"))
    }

    @Test
    fun `requiresPro returns false for body_doubling`() {
        assertFalse(NdFeatureGate.requiresPro("body_doubling"))
    }

    @Test
    fun `requiresPro returns false for completion_animations`() {
        assertFalse(NdFeatureGate.requiresPro("completion_animations"))
    }

    @Test
    fun `requiresPro returns false for streak_celebrations`() {
        assertFalse(NdFeatureGate.requiresPro("streak_celebrations"))
    }

    @Test
    fun `requiresPro returns false for progress_bars`() {
        assertFalse(NdFeatureGate.requiresPro("progress_bars"))
    }

    @Test
    fun `requiresPro returns false for forgiveness_streaks`() {
        assertFalse(NdFeatureGate.requiresPro("forgiveness_streaks"))
    }

    @Test
    fun `requiresPro returns false for reduce_animations`() {
        assertFalse(NdFeatureGate.requiresPro("reduce_animations"))
    }

    @Test
    fun `requiresPro returns false for muted_color_palette`() {
        assertFalse(NdFeatureGate.requiresPro("muted_color_palette"))
    }

    @Test
    fun `requiresPro returns false for quiet_mode`() {
        assertFalse(NdFeatureGate.requiresPro("quiet_mode"))
    }

    @Test
    fun `requiresPro returns false for reduce_haptics`() {
        assertFalse(NdFeatureGate.requiresPro("reduce_haptics"))
    }

    @Test
    fun `requiresPro returns false for soft_contrast`() {
        assertFalse(NdFeatureGate.requiresPro("soft_contrast"))
    }

    @Test
    fun `requiresPro returns false for unknown feature`() {
        assertFalse(NdFeatureGate.requiresPro("some_random_feature"))
    }

    // endregion
}
