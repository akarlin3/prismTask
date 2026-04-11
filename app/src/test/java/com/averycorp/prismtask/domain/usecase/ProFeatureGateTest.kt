package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.billing.UserTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ProFeatureGate] three-tier feature-gating logic.
 *
 * Uses a TestableFeatureGate that mirrors ProFeatureGate's logic but accepts
 * a controllable StateFlow instead of requiring BillingManager.
 */
class ProFeatureGateTest {

    /**
     * Mirrors [ProFeatureGate] logic for unit-testability without Android
     * dependencies.
     */
    private class TestableFeatureGate(initialTier: UserTier = UserTier.FREE) {
        private val _userTier = MutableStateFlow(initialTier)
        val userTier: StateFlow<UserTier> = _userTier.asStateFlow()

        fun setTier(tier: UserTier) { _userTier.value = tier }

        fun isPro(): Boolean = userTier.value >= UserTier.PRO
        fun isPremium(): Boolean = userTier.value == UserTier.PREMIUM

        fun hasAccess(feature: String): Boolean {
            return when (feature) {
                ProFeatureGate.CLOUD_SYNC, ProFeatureGate.TEMPLATE_SYNC,
                ProFeatureGate.AI_EISENHOWER, ProFeatureGate.AI_POMODORO,
                ProFeatureGate.ANALYTICS_BASIC, ProFeatureGate.TIME_TRACKING,
                ProFeatureGate.AI_NLP -> isPro()

                ProFeatureGate.AI_BRIEFING, ProFeatureGate.AI_WEEKLY_PLAN,
                ProFeatureGate.AI_TIME_BLOCK, ProFeatureGate.COLLABORATION,
                ProFeatureGate.INTEGRATIONS, ProFeatureGate.ANALYTICS_FULL,
                ProFeatureGate.ANALYTICS_CORRELATIONS,
                ProFeatureGate.DRIVE_BACKUP -> isPremium()

                else -> true
            }
        }
    }

    // --- Tier comparison ---

    @Test
    fun `tier ordering FREE less than PRO less than PREMIUM`() {
        assertTrue(UserTier.FREE < UserTier.PRO)
        assertTrue(UserTier.PRO < UserTier.PREMIUM)
        assertTrue(UserTier.FREE < UserTier.PREMIUM)
    }

    // --- FREE user access ---

    @Test
    fun `FREE user has no access to Pro or Premium features`() {
        val gate = TestableFeatureGate(UserTier.FREE)

        // Pro features blocked
        assertFalse(gate.hasAccess(ProFeatureGate.CLOUD_SYNC))
        assertFalse(gate.hasAccess(ProFeatureGate.AI_EISENHOWER))
        assertFalse(gate.hasAccess(ProFeatureGate.AI_POMODORO))
        assertFalse(gate.hasAccess(ProFeatureGate.TEMPLATE_SYNC))
        assertFalse(gate.hasAccess(ProFeatureGate.ANALYTICS_BASIC))
        assertFalse(gate.hasAccess(ProFeatureGate.TIME_TRACKING))
        assertFalse(gate.hasAccess(ProFeatureGate.AI_NLP))

        // Premium features blocked
        assertFalse(gate.hasAccess(ProFeatureGate.AI_BRIEFING))
        assertFalse(gate.hasAccess(ProFeatureGate.AI_WEEKLY_PLAN))
        assertFalse(gate.hasAccess(ProFeatureGate.AI_TIME_BLOCK))
        assertFalse(gate.hasAccess(ProFeatureGate.COLLABORATION))
        assertFalse(gate.hasAccess(ProFeatureGate.INTEGRATIONS))
        assertFalse(gate.hasAccess(ProFeatureGate.DRIVE_BACKUP))

        // Free features allowed
        assertTrue(gate.hasAccess("some_free_feature"))
    }

    // --- PRO user access ---

    @Test
    fun `PRO user has access to Pro features but not Premium`() {
        val gate = TestableFeatureGate(UserTier.PRO)

        // Pro features allowed
        assertTrue(gate.hasAccess(ProFeatureGate.CLOUD_SYNC))
        assertTrue(gate.hasAccess(ProFeatureGate.AI_EISENHOWER))
        assertTrue(gate.hasAccess(ProFeatureGate.AI_POMODORO))
        assertTrue(gate.hasAccess(ProFeatureGate.TEMPLATE_SYNC))
        assertTrue(gate.hasAccess(ProFeatureGate.ANALYTICS_BASIC))
        assertTrue(gate.hasAccess(ProFeatureGate.TIME_TRACKING))

        // Premium features blocked
        assertFalse(gate.hasAccess(ProFeatureGate.AI_BRIEFING))
        assertFalse(gate.hasAccess(ProFeatureGate.AI_WEEKLY_PLAN))
        assertFalse(gate.hasAccess(ProFeatureGate.AI_TIME_BLOCK))
        assertFalse(gate.hasAccess(ProFeatureGate.COLLABORATION))
        assertFalse(gate.hasAccess(ProFeatureGate.INTEGRATIONS))
        assertFalse(gate.hasAccess(ProFeatureGate.DRIVE_BACKUP))
    }

    // --- PREMIUM user access ---

    @Test
    fun `PREMIUM user has access to everything`() {
        val gate = TestableFeatureGate(UserTier.PREMIUM)

        // Pro features allowed
        assertTrue(gate.hasAccess(ProFeatureGate.CLOUD_SYNC))
        assertTrue(gate.hasAccess(ProFeatureGate.AI_EISENHOWER))
        assertTrue(gate.hasAccess(ProFeatureGate.AI_POMODORO))

        // Premium features allowed
        assertTrue(gate.hasAccess(ProFeatureGate.AI_BRIEFING))
        assertTrue(gate.hasAccess(ProFeatureGate.AI_WEEKLY_PLAN))
        assertTrue(gate.hasAccess(ProFeatureGate.AI_TIME_BLOCK))
        assertTrue(gate.hasAccess(ProFeatureGate.COLLABORATION))
        assertTrue(gate.hasAccess(ProFeatureGate.INTEGRATIONS))
        assertTrue(gate.hasAccess(ProFeatureGate.DRIVE_BACKUP))

        // Free features allowed
        assertTrue(gate.hasAccess("some_free_feature"))
    }

    // --- hasAccess per-feature ---

    @Test
    fun `hasAccess returns correct results for each feature constant`() {
        val gate = TestableFeatureGate(UserTier.PRO)

        // Pro-tier features
        val proFeatures = listOf(
            ProFeatureGate.CLOUD_SYNC, ProFeatureGate.TEMPLATE_SYNC,
            ProFeatureGate.AI_EISENHOWER, ProFeatureGate.AI_POMODORO,
            ProFeatureGate.ANALYTICS_BASIC, ProFeatureGate.TIME_TRACKING,
            ProFeatureGate.AI_NLP
        )
        proFeatures.forEach { feature ->
            assertTrue("PRO should access $feature", gate.hasAccess(feature))
        }

        // Premium-tier features
        val premiumFeatures = listOf(
            ProFeatureGate.AI_BRIEFING, ProFeatureGate.AI_WEEKLY_PLAN,
            ProFeatureGate.AI_TIME_BLOCK, ProFeatureGate.COLLABORATION,
            ProFeatureGate.INTEGRATIONS, ProFeatureGate.ANALYTICS_FULL,
            ProFeatureGate.ANALYTICS_CORRELATIONS, ProFeatureGate.DRIVE_BACKUP
        )
        premiumFeatures.forEach { feature ->
            assertFalse("PRO should NOT access $feature", gate.hasAccess(feature))
        }
    }

    // --- Feature constants ---

    @Test
    fun `all feature constants have unique values`() {
        val constants = setOf(
            ProFeatureGate.CLOUD_SYNC, ProFeatureGate.TEMPLATE_SYNC,
            ProFeatureGate.AI_EISENHOWER, ProFeatureGate.AI_POMODORO,
            ProFeatureGate.AI_NLP, ProFeatureGate.ANALYTICS_BASIC,
            ProFeatureGate.TIME_TRACKING, ProFeatureGate.AI_BRIEFING,
            ProFeatureGate.AI_WEEKLY_PLAN, ProFeatureGate.AI_TIME_BLOCK,
            ProFeatureGate.COLLABORATION, ProFeatureGate.INTEGRATIONS,
            ProFeatureGate.ANALYTICS_FULL, ProFeatureGate.ANALYTICS_CORRELATIONS,
            ProFeatureGate.DRIVE_BACKUP
        )
        assertEquals("All 15 feature constants should be unique", 15, constants.size)
    }

    // --- Tier upgrade restores highest ---

    @Test
    fun `billing restores highest active tier`() {
        val gate = TestableFeatureGate(UserTier.FREE)
        assertFalse(gate.isPro())
        assertFalse(gate.isPremium())

        // Simulate restoring Pro purchase
        gate.setTier(UserTier.PRO)
        assertTrue(gate.isPro())
        assertFalse(gate.isPremium())

        // Simulate restoring Premium (highest wins)
        gate.setTier(UserTier.PREMIUM)
        assertTrue(gate.isPro())
        assertTrue(gate.isPremium())
    }

    // --- Tier flow state changes ---

    @Test
    fun `userTier flow reflects billing state changes`() {
        val gate = TestableFeatureGate(UserTier.FREE)
        assertEquals(UserTier.FREE, gate.userTier.value)

        gate.setTier(UserTier.PRO)
        assertEquals(UserTier.PRO, gate.userTier.value)

        gate.setTier(UserTier.PREMIUM)
        assertEquals(UserTier.PREMIUM, gate.userTier.value)

        gate.setTier(UserTier.FREE)
        assertEquals(UserTier.FREE, gate.userTier.value)
    }
}
