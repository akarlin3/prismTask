package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.notifications.MedicationReminderReceiver.AlarmKind
import com.averycorp.prismtask.notifications.MedicationReminderReceiver.classifyAlarm
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-function tests for the alarm-extra dispatch contract.
 *
 * Regression coverage for the slot-INTERVAL bug fixed in this PR: the
 * previous receiver only checked `medicationId` and `habitId`, so alarms
 * registered by [MedicationIntervalRescheduler.registerAlarmForSlot]
 * (which sets `medicationId = -1L` and `intervalSlotId = slot.id`)
 * silently fell into the `else` branch and never showed a notification.
 *
 * The classifier prefers medicationId > intervalSlotId > habitId so a
 * future alarm that accidentally sets multiple ids still routes to the
 * same handler the original scheduler intended.
 */
class MedicationReminderReceiverDispatchTest {

    @Test
    fun medicationId_routesToMedicationKind() {
        val kind = classifyAlarm(medicationId = 7L, intervalSlotId = -1L, habitId = -1L)
        assertEquals(AlarmKind.Medication(7L), kind)
    }

    @Test
    fun slotInterval_routesWhenMedicationIdMissing() {
        // Mirrors MedicationIntervalRescheduler.registerAlarmForSlot.
        val kind = classifyAlarm(medicationId = -1L, intervalSlotId = 42L, habitId = -1L)
        assertEquals(AlarmKind.SlotInterval(42L), kind)
    }

    @Test
    fun habitId_routesWhenOnlyHabitIdSet() {
        val kind = classifyAlarm(medicationId = -1L, intervalSlotId = -1L, habitId = 11L)
        assertEquals(AlarmKind.Habit(11L), kind)
    }

    @Test
    fun unknown_whenAllIdsAbsent() {
        val kind = classifyAlarm(medicationId = -1L, intervalSlotId = -1L, habitId = -1L)
        assertEquals(AlarmKind.Unknown, kind)
    }

    @Test
    fun medicationId_winsWhenMultipleIdsPresent() {
        // Defensive: a malformed intent shouldn't double-fire.
        val kind = classifyAlarm(medicationId = 1L, intervalSlotId = 2L, habitId = 3L)
        assertEquals(AlarmKind.Medication(1L), kind)
    }

    @Test
    fun slotInterval_winsOverHabit() {
        val kind = classifyAlarm(medicationId = -1L, intervalSlotId = 5L, habitId = 9L)
        assertEquals(AlarmKind.SlotInterval(5L), kind)
    }

    @Test
    fun zero_idsAreValid() {
        // The receiver's previous `>= 0` predicate accepted 0; preserved here
        // because Room autoincrement could theoretically hit 0 on first row.
        assertEquals(
            AlarmKind.Medication(0L),
            classifyAlarm(medicationId = 0L, intervalSlotId = -1L, habitId = -1L)
        )
        assertEquals(
            AlarmKind.SlotInterval(0L),
            classifyAlarm(medicationId = -1L, intervalSlotId = 0L, habitId = -1L)
        )
    }
}
