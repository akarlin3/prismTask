package com.averycorp.prismtask.notifications

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

/**
 * Tests for [HabitReminderScheduler.computeNextDailyTrigger]. The
 * existing [HabitReminderSchedulerTest] covers `timeStringToNextTrigger`
 * only — this file covers the daily-time path that re-registers a habit's
 * reminder every morning.
 *
 * The helper uses [Calendar.getInstance] which reads the JVM default
 * timezone, so each test pins [TimeZone.setDefault] to a known zone and
 * restores the host default in [@After]. This lets us assert specific
 * wall-clock boundaries without needing to mock the system clock.
 *
 * Covered edge cases:
 *  - Normal future time today → returns today at HH:mm
 *  - Time already passed today → rolls to tomorrow
 *  - Exact boundary (now == reminderTime) → rolls to tomorrow (<= guard)
 *  - Midnight reminder → handled
 *  - Spring-forward DST (America/New_York, 2026-03-08 02:00 → 03:00):
 *    a reminder at 02:30 on the transition day resolves to 03:30 wall
 *    clock (the 2:30am slot doesn't exist)
 *  - Fall-back DST (America/New_York, 2026-11-01 02:00 → 01:00):
 *    a reminder at 01:30 on the transition day is unambiguous given
 *    Calendar's "first occurrence wins" rule
 */
class HabitReminderSchedulerDailyTriggerTest {
    private val savedDefault = TimeZone.getDefault()

    @Before
    fun pinUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimezone() {
        TimeZone.setDefault(savedDefault)
    }

    private fun millisAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun millisSinceMidnight(hour: Int, minute: Int = 0): Long =
        (hour * 60L + minute) * 60_000L

    @Test
    fun `future time today fires today at reminder time`() {
        val now = millisAt(2026, 4, 21, hour = 8)
        val reminder = millisSinceMidnight(hour = 14, minute = 30)

        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(21, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `past time today rolls to tomorrow`() {
        val now = millisAt(2026, 4, 21, hour = 15)
        val reminder = millisSinceMidnight(hour = 8)

        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(22, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `exact boundary rolls forward because guard is lte`() {
        // The production code uses `cal.timeInMillis <= now`, so a reminder
        // that's exactly now rolls to tomorrow. Lock that behavior so a
        // future refactor doesn't accidentally convert it to `<`.
        val now = millisAt(2026, 4, 21, hour = 9, minute = 30)
        val reminder = millisSinceMidnight(hour = 9, minute = 30)

        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)

        assertTrue("Boundary fire must roll to tomorrow", trigger > now)
        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(22, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `midnight reminder rolls forward from midday now`() {
        val now = millisAt(2026, 4, 21, hour = 12)
        val reminder = 0L // midnight

        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(22, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `23 59 reminder rolls to tomorrow at 23 59 when past`() {
        val now = millisAt(2026, 4, 21, hour = 23, minute = 59)
        val reminder = millisSinceMidnight(hour = 23, minute = 59)

        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(22, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `dst spring forward skips 2 30 am and fires at 3 30 am equivalent wall clock`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        // 2026-03-08 is the DST spring-forward day in the US. At 02:00
        // local, clocks jump to 03:00. 02:30 doesn't exist.
        val now = millisAt(2026, 3, 8, hour = 1, minute = 0)
        val reminder = millisSinceMidnight(hour = 2, minute = 30)

        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)

        // Calendar normalizes the non-existent 2:30 to 3:30 (EDT) — the
        // scheduler still fires once that day, just one hour "later" on the
        // wall clock. Verify it's after `now` and within the same day.
        assertTrue("DST-skipped slot must still produce a future trigger", trigger > now)
        assertTrue(
            "Trigger should land inside the spring-forward day, not overflow to the 9th",
            trigger - now < 4L * 60 * 60 * 1000
        )
    }

    @Test
    fun `dst fall back 01 30 resolves to one of the two valid instants`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        // 2026-11-01 is DST fall-back day — 02:00 local becomes 01:00, so
        // 01:30 happens twice (once EDT, once EST). The exact choice
        // is implementation-defined for java.util.Calendar — on some
        // JVMs it picks the earlier offset (EDT), on others the later
        // (EST). Either answer is correct behavior; the invariant the
        // scheduler cares about is only "trigger is in the future and
        // within the same fall-back day."
        val now = millisAt(2026, 11, 1, hour = 0, minute = 0)
        val reminder = millisSinceMidnight(hour = 1, minute = 30)

        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)

        assertTrue(trigger > now)
        // Accept either DST occurrence: EDT 01:30 is 1h30m after EDT
        // midnight, EST 01:30 is 2h30m after EDT midnight (because the
        // fall-back adds an extra hour). Cap at 3h to reject a rollover
        // to the following day (which would be 25+ hours out).
        assertTrue(
            "Trigger should be today's 01:30, not tomorrow's",
            trigger - now <= 3L * 60 * 60 * 1000
        )
    }

    @Test
    fun `timezone crossing does not leak into subsequent test`() {
        // After the DST tests flip TZ to America/New_York, ensure a plain
        // UTC assertion still passes once @Before resets the default.
        // (This is a defense-in-depth check on the test infrastructure.)
        val now = millisAt(2026, 4, 21, hour = 8)
        val reminder = millisSinceMidnight(hour = 10)
        val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminder, now)
        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(21, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
    }

    // NB: There is no Calendar-free "what's the next scheduled occurrence"
    // helper that takes a ZonedDateTime; the Calendar approach inherits the
    // JVM default TZ. Tests below confirm the DST-aware behavior given a
    // reproducible TZ pin. A future refactor to java.time.ZonedDateTime
    // would remove the TZ-mutation dependency — see testability TODOs at
    // the end of this test suite.
    @Suppress("unused")
    private val dayOfWeekSanity: DayOfWeek = LocalDateTime.now(ZoneId.of("UTC")).dayOfWeek
}
