package com.averycorp.prismtask.ui.screens.medication

import com.averycorp.prismtask.core.time.LocalDateFlow
import com.averycorp.prismtask.core.time.TimeProvider
import com.averycorp.prismtask.data.preferences.StartOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Regression gate for the MedicationScreen Start-of-Day boundary bug.
 *
 * Phase 1 of `docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md` reproduced
 * the bug via this same test file with assertions designed so that
 * passing meant the bug existed (the helper snapshot pattern locked the
 * date at construction time). This file has been **rewritten to assert
 * the FIXED contract**: passing now means the bug is fixed.
 *
 * Specifically, the new contract is "MedicationViewModel.todayDate is
 * now backed by `LocalDateFlow.observeIsoString(...)`, which advances
 * reactively at every logical-day boundary." The tests below exercise
 * the same shape `MedicationViewModel` will adopt and assert that:
 *
 *  1. The Flow re-emits across an SoD-boundary crossing — yesterday's
 *     logical date no longer sticks once the wall-clock has crossed
 *     today's SoD.
 *  2. The first emission respects the user's configured SoD, not the
 *     hard-coded `dayStartHour = 0` used by the legacy `stateIn`
 *     initial value.
 *
 * If a regression re-introduces the snapshot pattern (or any other
 * shape that loses the wall-clock subscription), these assertions
 * will fail.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationTodayDateRefreshTest {

    private val zone = ZoneId.of("UTC")

    /**
     * `TimeProvider` whose `now()` is anchored at [base] plus the
     * `runTest` virtual scheduler's `currentTime`. Advancing virtual
     * time via `advanceTimeBy(...)` in the test body propagates to the
     * provider's wall-clock reading, so a `delay(...)` inside the
     * Flow body (waiting until the next logical-day boundary) wakes
     * up at the right moment AND sees the correct new wall-clock.
     */
    private fun virtualClock(scope: TestScope, base: Instant) =
        object : TimeProvider {
            override fun now(): Instant = base.plusMillis(scope.testScheduler.currentTime)
            override fun zone(): ZoneId = zone
        }

    @Test
    fun todayDate_advancesReactively_whenWallClockCrossesSoDBoundary() = runTest {
        // 11pm on Apr 25 with SoD = 4am → logical day = Apr 25.
        // Advance 6 hours → 5am on Apr 26 → logical day should flip to Apr 26.
        val base = Instant.parse("2026-04-25T23:00:00Z")
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))
        val helper = LocalDateFlow(virtualClock(this, base))

        val todayDate: StateFlow<String> = helper
            .observeIsoString(sod)
            .stateIn(backgroundScope, SharingStarted.Eagerly, "")

        runCurrent()
        val before = todayDate.value
        assertEquals(
            "Initial emission should reflect the user's SoD-anchored logical day",
            "2026-04-25",
            before
        )

        advanceTimeBy(6 * 60 * 60 * 1000L) // 6h → past Apr 26 04:00 UTC
        runCurrent()

        val after = todayDate.value
        assertEquals(
            "todayDate must advance reactively when the wall-clock crosses SoD",
            "2026-04-26",
            after
        )
    }

    @Test
    fun todayDate_initialEmission_respectsUserSoD_notZero() = runTest {
        // 1am on Apr 26 — between calendar midnight and SoD = 4am.
        // The legacy `stateIn` initial value used `currentLocalDateString(0)`
        // which would have reported "2026-04-26" — wrong by a day for the
        // window before the upstream landed. The fixed helper must report
        // "2026-04-25" right out of the gate.
        val base = Instant.parse("2026-04-26T01:00:00Z")
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))
        val helper = LocalDateFlow(virtualClock(this, base))

        val first = helper.observeIsoString(sod).first()

        assertEquals(
            "Initial emission must be the user's SoD-anchored logical date, " +
                "not the calendar date implied by SoD = 0",
            "2026-04-25",
            first
        )
    }

    @Test
    fun todayDate_doesNotReEmit_whenWallClockAdvancesWithinSameLogicalDay() = runTest {
        // 4am on Apr 26 with SoD = 4am — at the boundary, logical day = Apr 26.
        // Advance 30 minutes → still inside the same logical day. No new emission.
        val base = Instant.parse("2026-04-26T04:00:00Z")
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))
        val helper = LocalDateFlow(virtualClock(this, base))

        val collected = mutableListOf<String>()
        val job = launch {
            helper.observeIsoString(sod).collect { collected.add(it) }
        }
        runCurrent()
        assertEquals(listOf("2026-04-26"), collected)

        advanceTimeBy(30 * 60 * 1000L) // 30 minutes
        runCurrent()

        assertEquals(
            "No re-emission expected when the wall-clock stays inside the same logical day",
            listOf("2026-04-26"),
            collected
        )
        job.cancel()
        advanceUntilIdle()
    }
}
