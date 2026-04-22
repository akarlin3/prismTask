package com.averycorp.prismtask.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for the pure scheduling helpers in [ReminderScheduler].
 *
 * The Android-coupled portion of the scheduler (AlarmManager + PendingIntent)
 * isn't exercised here — those need instrumentation to run meaningfully. The
 * companion helpers below are the math we want to lock down: when should the
 * alarm fire, and should we bother firing at all?
 */
class ReminderSchedulerTest {
    @Test
    fun computeTriggerTime_subtractsReminderOffsetFromDueDate() {
        val dueDate = 1_700_000_000_000L
        val fifteenMin = 15L * 60 * 1000
        assertEquals(dueDate - fifteenMin, ReminderScheduler.computeTriggerTime(dueDate, fifteenMin))
    }

    @Test
    fun computeTriggerTime_oneDayBeforeDueDate() {
        val dueDate = 1_700_000_000_000L
        val oneDay = 24L * 60 * 60 * 1000
        assertEquals(dueDate - oneDay, ReminderScheduler.computeTriggerTime(dueDate, oneDay))
    }

    @Test
    fun computeTriggerTime_zeroOffsetFiresAtDueTime() {
        val dueDate = 1_700_000_000_000L
        assertEquals(dueDate, ReminderScheduler.computeTriggerTime(dueDate, 0L))
    }

    @Test
    fun isInFuture_trueWhenTriggerAfterNow() {
        assertTrue(ReminderScheduler.isInFuture(triggerTime = 200L, now = 100L))
    }

    @Test
    fun isInFuture_falseWhenTriggerEqualsNow() {
        // The scheduler uses `> now` so equality is treated as "don't schedule".
        assertFalse(ReminderScheduler.isInFuture(triggerTime = 100L, now = 100L))
    }

    @Test
    fun isInFuture_falseWhenTriggerBeforeNow() {
        assertFalse(ReminderScheduler.isInFuture(triggerTime = 50L, now = 100L))
    }

    @Test
    fun combined_pastDueDate_doesNotFire() {
        // A due date that's already behind us with a small offset should end up
        // with a trigger time in the past — the scheduler must skip it.
        val now = 1_700_000_000_000L
        val dueDate = now - 60_000L
        val offset = 10_000L
        val trigger = ReminderScheduler.computeTriggerTime(dueDate, offset)
        assertFalse(ReminderScheduler.isInFuture(trigger, now))
    }

    @Test
    fun combined_futureDueDate_fires() {
        val now = 1_700_000_000_000L
        val dueDate = now + (60L * 60 * 1000) // one hour from now
        val offset = 15L * 60 * 1000 // 15 minute reminder
        val trigger = ReminderScheduler.computeTriggerTime(dueDate, offset)
        assertTrue(ReminderScheduler.isInFuture(trigger, now))
    }

    @Test
    fun combineDateAndTime_returnsDueDateWhenTimeIsNull() {
        val dueDate = 1_700_000_000_000L
        assertEquals(dueDate, ReminderScheduler.combineDateAndTime(dueDate, null))
    }

    @Test
    fun combineDateAndTime_appliesHourAndMinuteFromDueTime() {
        // Tasks store dueDate as midnight of the due day and dueTime as a
        // separate timestamp whose HH:mm encodes the user's time-of-day.
        // The helper should land the combined instant on the due day at
        // the requested time, regardless of which day the time picker ran.
        //
        // All three Calendars use the default zone — the same zone
        // [ReminderScheduler.combineDateAndTime] reads the input hour/
        // minute in. Using UTC here (as a prior version did) passed in
        // CI's UTC runner but broke on any non-UTC dev machine, because
        // the helper's internal `Calendar.getInstance()` would interpret
        // the UTC dueTime in the system zone and read a different hour.
        val zone = TimeZone.getDefault()
        val dueDate = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.APRIL, 15, 0, 0, 0)
        }.timeInMillis
        // dueTime was recorded on a different day at 15:30 — only HH:mm matters.
        val dueTime = Calendar.getInstance(zone).apply {
            clear()
            set(2020, Calendar.JANUARY, 1, 15, 30, 0)
        }.timeInMillis
        val expected = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.APRIL, 15, 15, 30, 0)
        }.timeInMillis

        val combined = ReminderScheduler.combineDateAndTime(dueDate, dueTime)
        // Sanity: the expected instant IS dueDate + 15h30m in the default zone
        // (no DST shifts on a same-day operation).
        val fifteen30 = (15L * 60 * 60 + 30 * 60) * 1000
        assertEquals(expected - dueDate, fifteen30)
        // And combined should line up with the expected instant.
        assertEquals(expected, combined)
    }

    @Test
    fun computeEffectiveTrigger_pastWithin24hFallsForwardInsteadOfDropping() {
        // Regression guard for the silent-drop bug: scheduleReminder used to
        // bail out whenever triggerTime <= now, which silently swallowed
        // same-day reminders whose trigger fell a few minutes (or hours) in
        // the past — for example, a same-day reminder created after the
        // intended trigger time, or a reminder restored on boot just after
        // its scheduled instant. The fall-forward behavior should turn those
        // into a "fire ASAP" alarm at now + 5s instead of silently dropping.
        val now = 1_700_000_000_000L
        val tenMinutesAgo = now - (10L * 60 * 1000)
        val effective = ReminderScheduler.computeEffectiveTrigger(tenMinutesAgo, now)
        assertEquals(
            "Past-but-within-24h triggers should fall forward to now + 5s",
            now + 5_000L,
            effective
        )
    }

    @Test
    fun computeEffectiveTrigger_pastJustUnder24hStillFallsForward() {
        // The 24h boundary is exclusive: anything strictly less than 24h
        // stale should still fall forward.
        val now = 1_700_000_000_000L
        val almostADayAgo = now - (24L * 60 * 60 * 1000) + 1
        val effective = ReminderScheduler.computeEffectiveTrigger(almostADayAgo, now)
        assertEquals(now + 5_000L, effective)
    }

    @Test
    fun computeEffectiveTrigger_pastBeyond24hIsDropped() {
        // Reminders more than a day stale are genuinely outdated. The helper
        // should signal "drop it" by returning null so scheduleReminder
        // refuses to register an alarm.
        val now = 1_700_000_000_000L
        val twoDaysAgo = now - (2L * 24 * 60 * 60 * 1000)
        assertEquals(null, ReminderScheduler.computeEffectiveTrigger(twoDaysAgo, now))
    }

    @Test
    fun computeEffectiveTrigger_futureTriggerPassesThrough() {
        // Forward-dated triggers are scheduled exactly as requested.
        val now = 1_700_000_000_000L
        val anHourFromNow = now + (60L * 60 * 1000)
        assertEquals(anHourFromNow, ReminderScheduler.computeEffectiveTrigger(anHourFromNow, now))
    }

    @Test
    fun combineDateAndTime_triggerTimeIsSameDayNotPreviousDay() {
        // Regression guard: this is the exact bug that silently dropped
        // reminders. A task due "today at 3pm" with a 10-minute reminder
        // must produce a trigger 10 minutes before 3pm today — not 10
        // minutes before midnight (which lands YESTERDAY and gets skipped).
        val zone = TimeZone.getDefault()
        val dueDate = Calendar.getInstance(zone).apply {
            set(2026, Calendar.APRIL, 15, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dueTime = Calendar.getInstance(zone).apply {
            // Arbitrary date; only HH:mm matters.
            set(2025, Calendar.DECEMBER, 1, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tenMin = 10L * 60 * 1000
        val combined = ReminderScheduler.combineDateAndTime(dueDate, dueTime)
        val trigger = ReminderScheduler.computeTriggerTime(combined, tenMin)

        val cal = Calendar.getInstance(zone).apply { timeInMillis = trigger }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(50, cal.get(Calendar.MINUTE))
    }
}
