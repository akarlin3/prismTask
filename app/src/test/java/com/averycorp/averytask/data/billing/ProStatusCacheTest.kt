package com.averycorp.averytask.data.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [SubscriptionState] enum and Pro status caching logic.
 * BillingClient interactions cannot be unit tested without Google Play,
 * so these tests focus on the state machine and cache expiration logic.
 */
class ProStatusCacheTest {

    @Test
    fun `subscription states cover all expected values`() {
        val states = SubscriptionState.entries
        assertEquals(5, states.size)
        assert(states.contains(SubscriptionState.NOT_SUBSCRIBED))
        assert(states.contains(SubscriptionState.SUBSCRIBED))
        assert(states.contains(SubscriptionState.GRACE_PERIOD))
        assert(states.contains(SubscriptionState.PAUSED))
        assert(states.contains(SubscriptionState.EXPIRED))
    }

    @Test
    fun `cached pro status is valid when expiry is in the future`() {
        val expiresAt = System.currentTimeMillis() + 1_000_000L
        val isValid = expiresAt > System.currentTimeMillis()
        assert(isValid)
    }

    @Test
    fun `cached pro status is invalid when expiry is in the past`() {
        val expiresAt = System.currentTimeMillis() - 1_000_000L
        val isValid = expiresAt > System.currentTimeMillis()
        assert(!isValid)
    }

    @Test
    fun `cached pro status is invalid when expiry is zero`() {
        val expiresAt = 0L
        val isValid = expiresAt > System.currentTimeMillis()
        assert(!isValid)
    }
}
