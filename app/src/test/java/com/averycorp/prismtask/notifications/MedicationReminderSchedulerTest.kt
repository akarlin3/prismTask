package com.averycorp.prismtask.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Tests for the pure companion helper in [MedicationReminderScheduler]. This
 * is the piece that takes a "HH:mm" string and turns it into the next trigger
 * timestamp — the most fragile slice of the scheduler because of how cheaply
 * timezone/day-rollover edge cases slip in.
 */
class MedicationReminderSchedulerTest {
    private fun epochFor(hour: Int, minute: Int, daysFromEpoch: Int): Long {
        val cal = Calendar.getInstance().apply {
            clear()
            set(1970, 0, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, daysFromEpoch)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return cal.timeInMillis
    }

    @Test
    fun timeStringToNextTrigger_futureTimeTodayFiresToday() {
        val now = epochFor(hour = 8, minute = 0, daysFromEpoch = 10_000)
        val trigger = MedicationReminderScheduler.timeStringToNextTrigger("14:30", now)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertTrue("Trigger should be later today", trigger > now)
    }

    @Test
    fun timeStringToNextTrigger_pastTimeRollsToNextDay() {
        val now = epochFor(hour = 15, minute = 0, daysFromEpoch = 10_000)
        val trigger = MedicationReminderScheduler.timeStringToNextTrigger("08:00", now)

        // Should be tomorrow at 08:00.
        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue("Trigger should be after now", trigger > now)
        // And at least 17 hours into the future (from 15:00 to next 08:00 == 17h).
        assertTrue(trigger - now >= 17L * 60 * 60 * 1000)
    }

    @Test
    fun timeStringToNextTrigger_currentMinuteRollsForward() {
        // Exact match with 'now' should roll to tomorrow because the method
        // uses `<=` rather than `<`.
        val now = epochFor(hour = 9, minute = 30, daysFromEpoch = 10_000)
        val trigger = MedicationReminderScheduler.timeStringToNextTrigger("09:30", now)
        assertTrue(trigger > now)
    }

    @Test
    fun timeStringToNextTrigger_malformedStringFallsBackToDefaultHour() {
        // The parser uses .toIntOrNull() with 8/0 defaults.
        val now = epochFor(hour = 0, minute = 0, daysFromEpoch = 10_000)
        val trigger = MedicationReminderScheduler.timeStringToNextTrigger("not:a:time", now)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun timeStringToNextTrigger_emptyStringFallsBackToDefaultHour() {
        val now = epochFor(hour = 0, minute = 0, daysFromEpoch = 10_000)
        val trigger = MedicationReminderScheduler.timeStringToNextTrigger("", now)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun timeStringToNextTrigger_midnightTimeIsHandled() {
        val now = epochFor(hour = 12, minute = 0, daysFromEpoch = 10_000)
        val trigger = MedicationReminderScheduler.timeStringToNextTrigger("00:00", now)

        // Should be tomorrow at midnight.
        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue(trigger > now)
    }
}
