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
        val zone = TimeZone.getTimeZone("UTC")
        val dueDate = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.APRIL, 15, 0, 0, 0)
        }.timeInMillis
        // dueTime was recorded the day BEFORE on a different day at 15:30.
        val dueTime = Calendar.getInstance(zone).apply {
            clear()
            set(2020, Calendar.JANUARY, 1, 15, 30, 0)
        }.timeInMillis
        val expected = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.APRIL, 15, 15, 30, 0)
        }.timeInMillis

        val combined = ReminderScheduler.combineDateAndTime(dueDate, dueTime)
        // Timezone-agnostic assertion: the combined time is exactly
        // dueDate + 15h30m, regardless of the local zone used by Calendar.
        val fifteen30 = (15L * 60 * 60 + 30 * 60) * 1000
        assertEquals(expected - dueDate, fifteen30)
        // And combined should line up with the expected instant.
        assertEquals(expected, combined)
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
