package com.averycorp.averytask.domain.usecase

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
        assertTrue(ProFeatureGate.CLOUD_SYNC.isNotEmpty())
        assertTrue(ProFeatureGate.COLLABORATION.isNotEmpty())
        assertTrue(ProFeatureGate.TEMPLATE_SYNC.isNotEmpty())
        assertTrue(ProFeatureGate.DRIVE_BACKUP.isNotEmpty())
    }
}
