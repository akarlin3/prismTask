package com.averycorp.prismtask.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
