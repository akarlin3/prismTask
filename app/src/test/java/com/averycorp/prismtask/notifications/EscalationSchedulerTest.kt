package com.averycorp.prismtask.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-helper tests for [EscalationScheduler.requestCodeFor]: the
 * request-code derivation is the keystone that lets [cancel] actually
 * target the same alarm we scheduled earlier.
 */
class EscalationSchedulerTest {
    @Test
    fun `request codes are stable across calls`() {
        val first = EscalationScheduler.requestCodeFor(taskId = 42L, stepIndex = 3)
        val second = EscalationScheduler.requestCodeFor(taskId = 42L, stepIndex = 3)
        assertEquals(first, second)
    }

    @Test
    fun `different tasks never collide with same step index`() {
        val a = EscalationScheduler.requestCodeFor(1L, stepIndex = 0)
        val b = EscalationScheduler.requestCodeFor(2L, stepIndex = 0)
        assertNotEquals(a, b)
    }

    @Test
    fun `different steps of the same task never collide`() {
        val codes = (0 until 10).map { EscalationScheduler.requestCodeFor(7L, it) }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `codes are in the reserved escalation range`() {
        val code = EscalationScheduler.requestCodeFor(1L, 0)
        assertTrue("expected >= 900_000, got $code", code >= 900_000)
    }

    @Test
    fun `out-of-range step indices are coerced`() {
        val clamped = EscalationScheduler.requestCodeFor(1L, stepIndex = 42)
        val inRange = EscalationScheduler.requestCodeFor(1L, stepIndex = 9)
        assertEquals(inRange, clamped)
    }
}
