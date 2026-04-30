package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.notifications.MedicationClockRescheduler.Companion.SLOT_BASE_REQUEST_CODE
import com.averycorp.prismtask.notifications.MedicationClockRescheduler.Companion.nextTriggerForClock
import com.averycorp.prismtask.notifications.MedicationClockRescheduler.Companion.slotRequestCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Pure-helper tests for [MedicationClockRescheduler]. Calendar-based
 * helpers respect the JVM default timezone, so each test pins a
 * deterministic anchor with [Calendar.getInstance] explicitly.
 */
class MedicationClockReschedulerTest {

    @Test
    fun slotRequestCode_isStableAndUniquePerSlotIdMod1000() {
        assertEquals(SLOT_BASE_REQUEST_CODE, slotRequestCode(0L))
        assertEquals(SLOT_BASE_REQUEST_CODE + 1, slotRequestCode(1L))
        assertEquals(SLOT_BASE_REQUEST_CODE + 999, slotRequestCode(999L))
        // Wraps cleanly so the namespace doesn't collide with other schedulers.
        assertEquals(SLOT_BASE_REQUEST_CODE, slotRequestCode(1000L))
        assertEquals(SLOT_BASE_REQUEST_CODE + 7, slotRequestCode(1007L))
    }

    @Test
    fun slotRequestCode_namespaceDistinctFromInterval() {
        // Sanity: don't shadow MedicationIntervalRescheduler at request 500_000.
        assertEquals(700_000, SLOT_BASE_REQUEST_CODE)
        assertTrue(SLOT_BASE_REQUEST_CODE > MedicationIntervalRescheduler.MED_BASE_REQUEST_CODE)
    }

    @Test
    fun nextTriggerForClock_invalidInputReturnsNull() {
        val now = nowAtNoon()
        assertNull(nextTriggerForClock("", now))
        assertNull(nextTriggerForClock("8", now))
        assertNull(nextTriggerForClock("8:00:00", now))
        assertNull(nextTriggerForClock("ab:cd", now))
        assertNull(nextTriggerForClock("24:00", now))
        assertNull(nextTriggerForClock("23:60", now))
        assertNull(nextTriggerForClock("-1:00", now))
    }

    @Test
    fun nextTriggerForClock_pastTimeRollsToTomorrow() {
        val now = nowAtNoon()
        val trigger = nextTriggerForClock("08:00", now)
        assertNotNull(trigger)
        // Trigger must be strictly in the future and within 24 hours.
        assertTrue(trigger!! > now)
        assertTrue(trigger - now <= MS_IN_DAY)
    }

    @Test
    fun nextTriggerForClock_futureTimeStaysToday() {
        val now = nowAtNoon()
        val trigger = nextTriggerForClock("18:00", now)
        assertNotNull(trigger)
        assertTrue(trigger!! > now)
        // Less than one day off (in fact ~6 hours).
        assertTrue(trigger - now < MS_IN_DAY)
        assertTrue(trigger - now > 5 * 3_600_000L)
    }

    @Test
    fun nextTriggerForClock_exactlyNowRollsToTomorrow() {
        val cal = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val trigger = nextTriggerForClock("08:00", now)
        assertNotNull(trigger)
        // Same wall-clock time — should fire the same time tomorrow.
        assertEquals(MS_IN_DAY, trigger!! - now)
    }

    private fun nowAtNoon(): Long {
        val cal = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    companion object {
        private const val MS_IN_DAY = 24 * 60 * 60 * 1_000L
    }
}
