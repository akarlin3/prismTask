package com.averycorp.prismtask.domain.model.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationChainTest {
    @Test
    fun `disabled chain returns no offsets`() {
        val chain = EscalationChain.DISABLED
        assertTrue(chain.absoluteOffsets(UrgencyTier.CRITICAL).isEmpty())
    }

    @Test
    fun `absoluteOffsets accumulates delays`() {
        val chain = EscalationChain(
            enabled = true,
            steps = listOf(
                EscalationStep(EscalationStepAction.GENTLE_PING, delayMs = 0L),
                EscalationStep(EscalationStepAction.STANDARD_ALERT, delayMs = 60_000L),
                EscalationStep(EscalationStepAction.LOUD_VIBRATE, delayMs = 60_000L)
            ),
            maxAttempts = 0 // unlimited
        )
        val offsets = chain.absoluteOffsets(UrgencyTier.MEDIUM)
        assertEquals(listOf(0L, 60_000L, 120_000L), offsets)
    }

    @Test
    fun `tier filter skips steps whose triggerTiers do not include tier`() {
        val chain = EscalationChain(
            enabled = true,
            steps = listOf(
                EscalationStep(EscalationStepAction.GENTLE_PING, delayMs = 0L),
                EscalationStep(
                    EscalationStepAction.STANDARD_ALERT,
                    delayMs = 60_000L,
                    triggerTiers = setOf(UrgencyTier.HIGH)
                ),
                EscalationStep(
                    EscalationStepAction.LOUD_VIBRATE,
                    delayMs = 60_000L
                )
            ),
            maxAttempts = 0
        )
        val lowOffsets = chain.absoluteOffsets(UrgencyTier.LOW)
        // STANDARD_ALERT (filtered out) — but its delay still advances the
        // running clock so LOUD_VIBRATE lands at 120s not 60s.
        assertEquals(listOf(0L, 120_000L), lowOffsets)
    }

    @Test
    fun `maxAttempts caps the number of offsets returned`() {
        val chain = EscalationChain(
            enabled = true,
            steps = List(6) {
                EscalationStep(EscalationStepAction.STANDARD_ALERT, delayMs = 10_000L)
            },
            maxAttempts = 3
        )
        val offsets = chain.absoluteOffsets(UrgencyTier.MEDIUM)
        assertEquals(3, offsets.size)
        assertEquals(listOf(10_000L, 20_000L, 30_000L), offsets)
    }

    @Test
    fun `default aggressive chain targets critical tier`() {
        val chain = EscalationChain.DEFAULT_AGGRESSIVE
        val offsets = chain.absoluteOffsets(UrgencyTier.CRITICAL)
        assertTrue("should have at least 3 steps active for CRITICAL", offsets.size >= 3)
    }

    @Test
    fun `empty triggerTiers applies to every tier`() {
        val step = EscalationStep(EscalationStepAction.STANDARD_ALERT, delayMs = 0L)
        UrgencyTier.values().forEach { t ->
            assertTrue(step.appliesTo(t))
        }
    }
}
