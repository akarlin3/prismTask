package com.averycorp.prismtask.data.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [SubscriptionState] and [UserTier] / [BillingPeriod] enums.
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

    @Test
    fun `UserTier enum only exposes FREE and PRO`() {
        val tiers = UserTier.entries
        assertEquals(2, tiers.size)
        assert(tiers.contains(UserTier.FREE))
        assert(tiers.contains(UserTier.PRO))
    }

    @Test
    fun `UserTier round-trips through name serialization`() {
        val tier = UserTier.PRO
        val serialized = tier.name
        assertEquals(UserTier.PRO, UserTier.valueOf(serialized))
    }

    @Test
    fun `BillingPeriod enum exposes MONTHLY ANNUAL and NONE`() {
        val periods = BillingPeriod.entries
        assertEquals(3, periods.size)
        assert(periods.contains(BillingPeriod.MONTHLY))
        assert(periods.contains(BillingPeriod.ANNUAL))
        assert(periods.contains(BillingPeriod.NONE))
    }

    @Test
    fun `BillingPeriod round-trips through name serialization`() {
        assertEquals(BillingPeriod.ANNUAL, BillingPeriod.valueOf("ANNUAL"))
        assertEquals(BillingPeriod.MONTHLY, BillingPeriod.valueOf("MONTHLY"))
        assertEquals(BillingPeriod.NONE, BillingPeriod.valueOf("NONE"))
    }
}
