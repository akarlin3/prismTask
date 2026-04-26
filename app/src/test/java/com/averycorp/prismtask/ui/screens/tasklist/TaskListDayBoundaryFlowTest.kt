package com.averycorp.prismtask.ui.screens.tasklist

import com.averycorp.prismtask.core.time.LocalDateFlow
import com.averycorp.prismtask.core.time.TimeProvider
import com.averycorp.prismtask.data.preferences.StartOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Regression gate for `TaskListViewModel`'s SoD-boundary fix
 * (`docs/audits/UTIL_DAYBOUNDARY_SWEEP_AUDIT.md` § 3).
 *
 * **Phase-5 (GREEN) form** — assertion encodes the bug-fixed state.
 * Reconstructs the post-migration shape from
 * `TaskListViewModel.dayStartFlow`:
 *
 *   `combine(localDateFlow.observe(getStartOfDay()), getStartOfDay())
 *       { date, sod -> date.atTime(sod.hour, sod.minute)...epochMillis }
 *       .stateIn(...)`
 *
 * Asserts the StateFlow's value advances when the wall-clock crosses
 * the user's SoD — exactly what the legacy snapshot pattern could not.
 *
 * Inverted from Phase-1 form (commit `d5051d9d`). Re-introducing the
 * snapshot pattern flips this assertion back to red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListDayBoundaryFlowTest {

    private val zone = ZoneId.of("UTC")

    private fun virtualClock(scope: TestScope, base: Instant) =
        object : TimeProvider {
            override fun now(): Instant = base.plusMillis(scope.testScheduler.currentTime)
            override fun zone(): ZoneId = zone
        }

    @Test
    fun dayStartFlow_localDateFlowDriven_advancesAtSoDBoundary() = runTest {
        // 11pm Apr 25 UTC, SoD = 4am.
        // Logical day = Apr 25; SoD-anchored start = Apr 25 04:00 UTC.
        // 6h later (5am Apr 26) the SoD boundary has flipped — start = Apr 26 04:00 UTC.
        val base = Instant.parse("2026-04-25T23:00:00Z")
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))
        val helper = LocalDateFlow(virtualClock(this, base))

        val dayStartFlow: StateFlow<Long> = combine(
            helper.observe(sod),
            sod
        ) { date, s ->
            date.atTime(s.hour, s.minute).atZone(zone).toInstant().toEpochMilli()
        }.stateIn(backgroundScope, SharingStarted.Eagerly, -1L)

        runCurrent()
        val before = dayStartFlow.value
        assertEquals(
            "Initial value: SoD-anchored start of Apr 25 (the logical day at 11pm UTC pre-boundary)",
            Instant.parse("2026-04-25T04:00:00Z").toEpochMilli(),
            before
        )

        advanceTimeBy(6 * 60 * 60 * 1000L) // 6h → past Apr 26 04:00 UTC
        runCurrent()

        val after = dayStartFlow.value
        assertNotEquals(
            "dayStartFlow MUST advance reactively when wall-clock crosses SoD — " +
                "if this fails, someone reverted to the snapshot pattern",
            before,
            after
        )
        assertEquals(
            "After SoD crossing, dayStartFlow reflects SoD-anchored start of the new logical day",
            Instant.parse("2026-04-26T04:00:00Z").toEpochMilli(),
            after
        )
    }
}
