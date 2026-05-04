package com.averycorp.prismtask.workers

import com.averycorp.prismtask.workers.AutomationTimeTickWorker.Companion.computeAlignedDelayMs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Pure-JVM unit tests for [AutomationTimeTickWorker.computeAlignedDelayMs] —
 * the helper used by [com.averycorp.prismtask.PrismTaskApplication] to align
 * the periodic worker's first fire to a wall-clock 00/15/30/45 slot. Per
 * `docs/audits/AUTOMATION_VALIDATION_T2_T4_AUDIT.md` Part D option (ii)
 * (operator-selected May 4 2026) and the matching paragraph in
 * `AUTOMATION_ENGINE_ARCHITECTURE.md` § A1 schedulers row.
 *
 * Pinning timezone to UTC for determinism — the helper builds a
 * [Calendar] from `Calendar.getInstance()`, which reads the JVM default
 * timezone. Mirror of [DailyResetWorkerScheduleTest]'s setup.
 */
class AutomationTimeTickWorkerScheduleTest {
    private var savedTz: TimeZone? = null

    @Before
    fun setUp() {
        savedTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        savedTz?.let { TimeZone.setDefault(it) }
    }

    private fun millisAt(hour: Int, minute: Int, second: Int = 0, ms: Int = 0): Long =
        Calendar.getInstance().apply {
            timeZone = TimeZone.getTimeZone("UTC")
            set(2026, Calendar.MAY, 4, hour, minute, second)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis

    @Test
    fun `delay at exact slot boundary is one full interval, not zero`() {
        val now = millisAt(7, 0, 0, 0)
        assertEquals(15 * 60_000L, computeAlignedDelayMs(now))
    }

    @Test
    fun `delay at 7-02 is 13 minutes`() {
        val now = millisAt(7, 2, 0, 0)
        assertEquals(13 * 60_000L, computeAlignedDelayMs(now))
    }

    @Test
    fun `delay at 7-14-59-999 is 1ms (just before next slot)`() {
        val now = millisAt(7, 14, 59, 999)
        assertEquals(1L, computeAlignedDelayMs(now))
    }

    @Test
    fun `delay at 7-15 is one full interval (next slot is 7-30)`() {
        val now = millisAt(7, 15, 0, 0)
        assertEquals(15 * 60_000L, computeAlignedDelayMs(now))
    }

    @Test
    fun `delay at 7-29-30 is 30 seconds`() {
        val now = millisAt(7, 29, 30, 0)
        assertEquals(30_000L, computeAlignedDelayMs(now))
    }

    @Test
    fun `delay at 7-46 is 14 minutes (next slot is 8-00)`() {
        val now = millisAt(7, 46, 0, 0)
        assertEquals(14 * 60_000L, computeAlignedDelayMs(now))
    }

    @Test
    fun `delay at 23-59-30 wraps to next-day 00-00 within 30 seconds`() {
        val now = millisAt(23, 59, 30, 0)
        assertEquals(30_000L, computeAlignedDelayMs(now))
    }

    @Test
    fun `every result is in (0, intervalMs] for 15-min interval`() {
        val intervalMs = 15 * 60_000L
        // Sample every minute of an hour to spot off-by-one or
        // negative-delay regressions.
        for (minute in 0..59) {
            val now = millisAt(10, minute, 0, 0)
            val delay = computeAlignedDelayMs(now)
            assertTrue(
                "delay must be in (0, $intervalMs] for minute=$minute, was $delay",
                delay in 1..intervalMs
            )
        }
    }

    @Test
    fun `5-minute interval slots align to 00, 05, 10`() {
        // Helper supports any divisor of 60. Spot-check 5-min for
        // potential future fine-grained scheduling — not used in prod.
        val now = millisAt(7, 7, 0, 0)
        assertEquals(3 * 60_000L, computeAlignedDelayMs(now, intervalMin = 5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-divisor of 60 throws`() {
        computeAlignedDelayMs(millisAt(7, 0), intervalMin = 7)
    }
}
