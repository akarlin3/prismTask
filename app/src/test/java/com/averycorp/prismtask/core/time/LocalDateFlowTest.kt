package com.averycorp.prismtask.core.time

import com.averycorp.prismtask.data.preferences.StartOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Unit tests for [LocalDateFlow]. Uses kotlinx-coroutines-test virtual
 * time + a [TimeProvider] anchored at a base [Instant] plus the test
 * scheduler's `currentTime`. Advancing virtual time via `advanceTimeBy(...)`
 * propagates through both the `delay(...)` inside the Flow body AND the
 * provider's `now()` reading, so the helper sees a consistent view of
 * "the wall-clock just advanced N millis."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalDateFlowTest {

    private val utc = ZoneId.of("UTC")
    private val ny = ZoneId.of("America/New_York")

    private fun virtualClock(scope: TestScope, base: Instant, zone: ZoneId = utc) =
        object : TimeProvider {
            override fun now(): Instant = base.plusMillis(scope.testScheduler.currentTime)
            override fun zone(): ZoneId = zone
        }

    private fun atUtc(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(utc).toInstant()

    private fun atNy(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(ny).toInstant()

    @Test
    fun observe_initialEmission_matchesSoDAnchoredLogicalDate() = runTest {
        val helper = LocalDateFlow(virtualClock(this, atUtc(2026, 4, 26, 1, 0)))
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))

        val first = helper.observe(sod).first()

        assertEquals(LocalDate.of(2026, 4, 25), first)
    }

    @Test
    fun observe_reemits_whenWallClockCrossesNextBoundary() = runTest {
        val helper = LocalDateFlow(virtualClock(this, atUtc(2026, 4, 25, 23, 0)))
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))

        val collected = mutableListOf<LocalDate>()
        val job = launch { helper.observe(sod).collect { collected.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 4, 25)), collected)

        // Advance 6 hours — past Apr 26 04:00 UTC (the next logical-day boundary).
        advanceTimeBy(6 * 60 * 60 * 1000L)
        runCurrent()

        assertEquals(
            listOf(LocalDate.of(2026, 4, 25), LocalDate.of(2026, 4, 26)),
            collected
        )

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun observe_doesNotReemit_whenWallClockStaysWithinLogicalDay() = runTest {
        val helper = LocalDateFlow(virtualClock(this, atUtc(2026, 4, 26, 4, 0)))
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))

        val collected = mutableListOf<LocalDate>()
        val job = launch { helper.observe(sod).collect { collected.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 4, 26)), collected)

        // Advance 23 hours — still inside Apr 26 (boundary is Apr 27 04:00 UTC).
        advanceTimeBy(23 * 60 * 60 * 1000L)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 4, 26)), collected)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun observe_reemits_whenSoDChangesAndShiftsLogicalDay() = runTest {
        // 02:00 UTC on Apr 26.
        // SoD = 0 → logical today is Apr 26 (calendar = logical).
        // Switch SoD to 4am → logical today is Apr 25 (we're before the new SoD).
        val helper = LocalDateFlow(virtualClock(this, atUtc(2026, 4, 26, 2, 0)))
        val sod = MutableStateFlow(StartOfDay(hour = 0, minute = 0, hasBeenSet = false))

        val collected = mutableListOf<LocalDate>()
        val job = launch { helper.observe(sod).collect { collected.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 4, 26)), collected)

        sod.value = StartOfDay(hour = 4, minute = 0, hasBeenSet = true)
        runCurrent()

        assertEquals(
            listOf(LocalDate.of(2026, 4, 26), LocalDate.of(2026, 4, 25)),
            collected
        )

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun observe_distinctUntilChanged_suppressesRedundantEmissions() = runTest {
        // 02:00 UTC on Apr 26 with SoD = 0. Switching SoD to a different value
        // that resolves to the SAME logical date must NOT double-emit.
        val helper = LocalDateFlow(virtualClock(this, atUtc(2026, 4, 26, 14, 0)))
        val sod = MutableStateFlow(StartOfDay(hour = 0, minute = 0, hasBeenSet = false))

        val collected = mutableListOf<LocalDate>()
        val job = launch { helper.observe(sod).collect { collected.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 4, 26)), collected)

        // SoD = 4 at 14:00 → logical day is still Apr 26. No new emission.
        sod.value = StartOfDay(hour = 4, minute = 0, hasBeenSet = true)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 4, 26)), collected)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun observe_dstSpringForward_handlesBoundaryWithoutFlapping() = runTest {
        // US spring-forward 2026: Sun Mar 8, NY clock 02:00 → 03:00.
        // Start the test the day before so we cross the DST boundary AND a
        // logical-day boundary in the same run.
        // Mar 7 23:00 NY (= Mar 8 04:00 UTC). SoD = 4am NY-local.
        // Logical today before the next boundary = Mar 7 (still before Mar 8 04:00 NY).
        val helper = LocalDateFlow(virtualClock(this, atNy(2026, 3, 7, 23, 0), zone = ny))
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))

        val collected = mutableListOf<LocalDate>()
        val job = launch { helper.observe(sod).collect { collected.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 3, 7)), collected)

        // Advance just over 5h to put us past Mar 8 04:00 NY (DST already
        // applied: by then NY clocks read 05:something on Mar 8, post-jump).
        advanceTimeBy(5 * 60 * 60 * 1000L + 1)
        runCurrent()

        assertEquals(
            listOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8)),
            collected
        )

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun observeIsoString_emitsIsoFormattedLocalDate() = runTest {
        val helper = LocalDateFlow(virtualClock(this, atUtc(2026, 4, 26, 14, 0)))
        val sod = MutableStateFlow(StartOfDay(hour = 0, minute = 0, hasBeenSet = false))

        val first = helper.observeIsoString(sod).first()

        assertEquals("2026-04-26", first)
    }
}
