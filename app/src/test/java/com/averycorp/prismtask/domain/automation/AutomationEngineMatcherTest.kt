package com.averycorp.prismtask.domain.automation

import com.averycorp.prismtask.domain.automation.AutomationEngine.Companion.matchTrigger
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Coverage for [AutomationEngine.matchTrigger] — the pure matcher
 * extracted to a companion-object function in PR-A2 of
 * `docs/audits/AUTOMATION_VALIDATION_T2_T4_AUDIT.md`. Tests stand alone
 * (no AutomationEngine instance, no DAO graph), exercising every branch
 * of the `when (trigger)` dispatch.
 *
 * Time semantics under test (option (ii)): TimeOfDay and DayOfWeekTime
 * require exact-minute equality between trigger and tick; the worker
 * delivers ticks only at clock-aligned 00/15/30/45 slots, so rules whose
 * minute is not 0/15/30/45 will not fire even if the matcher is called
 * with a synthetic tick that happens to land on them. These tests
 * exercise the matcher only — slot-alignment is covered by
 * [com.averycorp.prismtask.workers.AutomationTimeTickWorkerScheduleTest].
 *
 * UTC pinning: DayOfWeekTime resolves the day-of-week from
 * `Instant.atZone(ZoneId.systemDefault())`. Setting JVM default to UTC
 * keeps the synthetic occurredAt millis deterministic regardless of
 * developer locale.
 */
class AutomationEngineMatcherTest {
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

    private fun tickAt(
        hour: Int,
        minute: Int,
        date: LocalDate = LocalDate.of(2026, 5, 4) // Mon
    ): AutomationEvent.TimeTick {
        val occurredAt = ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        return AutomationEvent.TimeTick(hour = hour, minute = minute, occurredAt = occurredAt)
    }

    // ----- TimeOfDay -----

    @Test
    fun `TimeOfDay matches on exact hour and minute`() {
        val trigger = AutomationTrigger.TimeOfDay(hour = 7, minute = 0)
        assertTrue(matchTrigger(trigger, tickAt(7, 0), ruleId = 1L))
    }

    @Test
    fun `TimeOfDay does not match when minute differs by one`() {
        val trigger = AutomationTrigger.TimeOfDay(hour = 7, minute = 0)
        assertFalse(matchTrigger(trigger, tickAt(7, 1), ruleId = 1L))
    }

    @Test
    fun `TimeOfDay does not match when hour differs`() {
        val trigger = AutomationTrigger.TimeOfDay(hour = 7, minute = 0)
        assertFalse(matchTrigger(trigger, tickAt(8, 0), ruleId = 1L))
    }

    @Test
    fun `TimeOfDay matches on quarter-hour slots 15-30-45`() {
        val ticks = listOf(tickAt(7, 15), tickAt(7, 30), tickAt(7, 45))
        ticks.forEach { tick ->
            val trigger = AutomationTrigger.TimeOfDay(hour = 7, minute = tick.minute)
            assertTrue("expected match for tick=$tick", matchTrigger(trigger, tick, ruleId = 1L))
        }
    }

    @Test
    fun `TimeOfDay does not match against non-TimeTick events`() {
        val trigger = AutomationTrigger.TimeOfDay(hour = 7, minute = 0)
        assertFalse(matchTrigger(trigger, AutomationEvent.TaskCreated(taskId = 99L), ruleId = 1L))
        assertFalse(matchTrigger(trigger, AutomationEvent.HabitCompleted(habitId = 1L, date = "2026-05-04"), ruleId = 1L))
    }

    // ----- DayOfWeekTime (sibling-primitive axis (e)) -----

    @Test
    fun `DayOfWeekTime matches on exact hour minute and listed weekday`() {
        // 2026-05-04 is a Monday in any timezone-stable construction.
        val trigger = AutomationTrigger.DayOfWeekTime(
            daysOfWeek = setOf(DayOfWeek.MONDAY.name),
            hour = 9,
            minute = 0
        )
        assertTrue(matchTrigger(trigger, tickAt(9, 0, LocalDate.of(2026, 5, 4)), ruleId = 1L))
    }

    @Test
    fun `DayOfWeekTime does not match on excluded weekday`() {
        // 2026-05-05 is a Tuesday.
        val trigger = AutomationTrigger.DayOfWeekTime(
            daysOfWeek = setOf(DayOfWeek.MONDAY.name),
            hour = 9,
            minute = 0
        )
        assertFalse(matchTrigger(trigger, tickAt(9, 0, LocalDate.of(2026, 5, 5)), ruleId = 1L))
    }

    @Test
    fun `DayOfWeekTime requires exact-minute equality even when day matches`() {
        val trigger = AutomationTrigger.DayOfWeekTime(
            daysOfWeek = setOf(DayOfWeek.MONDAY.name),
            hour = 9,
            minute = 15
        )
        // Same Monday, same hour, but minute=14 → no match (sibling-primitive
        // axis (e) parity check vs TimeOfDay).
        assertFalse(matchTrigger(trigger, tickAt(9, 14, LocalDate.of(2026, 5, 4)), ruleId = 1L))
    }

    @Test
    fun `DayOfWeekTime matches when any of multiple days is present`() {
        val trigger = AutomationTrigger.DayOfWeekTime(
            daysOfWeek = setOf(DayOfWeek.MONDAY.name, DayOfWeek.WEDNESDAY.name, DayOfWeek.FRIDAY.name),
            hour = 9,
            minute = 0
        )
        // 2026-05-06 is a Wednesday.
        assertTrue(matchTrigger(trigger, tickAt(9, 0, LocalDate.of(2026, 5, 6)), ruleId = 1L))
        // 2026-05-07 is a Thursday — not in set.
        assertFalse(matchTrigger(trigger, tickAt(9, 0, LocalDate.of(2026, 5, 7)), ruleId = 1L))
    }

    // ----- Other branches (regression net) -----

    @Test
    fun `EntityEvent matches on simpleName equality`() {
        val trigger = AutomationTrigger.EntityEvent(eventKind = "TaskCreated")
        assertTrue(matchTrigger(trigger, AutomationEvent.TaskCreated(taskId = 99L), ruleId = 1L))
        assertFalse(matchTrigger(trigger, AutomationEvent.TaskCompleted(taskId = 99L), ruleId = 1L))
    }

    @Test
    fun `Manual matches only when ManualTrigger event carries this rule's id`() {
        assertTrue(matchTrigger(AutomationTrigger.Manual, AutomationEvent.ManualTrigger(ruleId = 7L), ruleId = 7L))
        assertFalse(matchTrigger(AutomationTrigger.Manual, AutomationEvent.ManualTrigger(ruleId = 8L), ruleId = 7L))
        assertFalse(matchTrigger(AutomationTrigger.Manual, AutomationEvent.TaskCreated(taskId = 99L), ruleId = 7L))
    }

    @Test
    fun `Composed matches only when RuleFired carries the parent rule id`() {
        val trigger = AutomationTrigger.Composed(parentRuleId = 42L)
        assertTrue(matchTrigger(trigger, AutomationEvent.RuleFired(ruleId = 42L, parentLogId = null), ruleId = 5L))
        assertFalse(matchTrigger(trigger, AutomationEvent.RuleFired(ruleId = 99L, parentLogId = null), ruleId = 5L))
        assertFalse(matchTrigger(trigger, AutomationEvent.TaskCreated(taskId = 99L), ruleId = 5L))
    }
}
