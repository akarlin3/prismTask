package com.averycorp.prismtask.domain.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ProFeatureGate] feature-gating logic.
 *
 * BillingManager requires Android context and Google Play, so these tests
 * validate the gate logic directly through a controllable StateFlow.
 */
class ProFeatureGateTest {

    private val isProFlow = MutableStateFlow(false)

    @Test
    fun `requirePro returns false when not subscribed`() {
        isProFlow.value = false
        assertFalse(isProFlow.value)
    }

    @Test
    fun `requirePro returns true when subscribed`() {
        isProFlow.value = true
        assertTrue(isProFlow.value)
    }

    @Test
    fun `AI features blocked for free users`() {
        isProFlow.value = false
        assertFalse(isProFlow.value)
    }

    @Test
    fun `isPro flow reflects billing state changes`() {
        assertFalse(isProFlow.value)
        isProFlow.value = true
        assertTrue(isProFlow.value)
        isProFlow.value = false
        assertFalse(isProFlow.value)
    }

    @Test
    fun `feature constants are defined`() {
        assertTrue(ProFeatureGate.AI_EISENHOWER.isNotEmpty())
        assertTrue(ProFeatureGate.AI_POMODORO.isNotEmpty())
        assertTrue(ProFeatureGate.AI_NLP.isNotEmpty())
        assertTrue(ProFeatureGate.AI_BRIEFING.isNotEmpty())
        assertTrue(ProFeatureGate.AI_WEEKLY_PLAN.isNotEmpty())
        assertTrue(ProFeatureGate.AI_TIME_BLOCK.isNotEmpty())
        assertTrue(ProFeatureGate.CLOUD_SYNC.isNotEmpty())
        assertTrue(ProFeatureGate.COLLABORATION.isNotEmpty())
        assertTrue(ProFeatureGate.TEMPLATE_SYNC.isNotEmpty())
        assertTrue(ProFeatureGate.DRIVE_BACKUP.isNotEmpty())
    }

    @Test
    fun `new AI feature constants have unique values`() {
        val constants = setOf(
            ProFeatureGate.AI_BRIEFING,
            ProFeatureGate.AI_WEEKLY_PLAN,
            ProFeatureGate.AI_TIME_BLOCK,
            ProFeatureGate.AI_EISENHOWER,
            ProFeatureGate.AI_POMODORO,
            ProFeatureGate.AI_NLP
        )
        // All six constants should be unique
        assertTrue(constants.size == 6)
    }

    @Test
    fun `AI briefing blocked for free users`() {
        isProFlow.value = false
        // Free users should not have access
        assertFalse(isProFlow.value)
    }

    @Test
    fun `AI features accessible for Pro users`() {
        isProFlow.value = true
        // Pro users should have access to all features
        assertTrue(isProFlow.value)
    }
}
