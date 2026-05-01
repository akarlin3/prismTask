package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.notifications.MedicationReminderReceiver.Companion.AlarmKind
import com.averycorp.prismtask.notifications.MedicationReminderReceiver.Companion.classifyAlarm
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-function tests for the alarm-extra dispatch contract.
 *
 * Regression coverage for two RED bugs:
 *  - Slot-INTERVAL alarms previously dropped because the receiver only
 *    inspected `medicationId` / `habitId`.
 *  - Slot-CLOCK alarms didn't exist at all — they live on this branch.
 *
 * Priority order: medication > slot-clock > slot-interval > habit. A
 * malformed intent that somehow stamps multiple id extras must still
 * route to a single handler so we never double-fire.
 */
class MedicationReminderReceiverDispatchTest {

    @Test
    fun medicationId_routesToMedicationKind() {
        val kind = classifyAlarm(
            medicationId = 7L,
            clockSlotId = -1L,
            intervalSlotId = -1L,
            habitId = -1L
        )
        assertEquals(AlarmKind.Medication(7L), kind)
    }

    @Test
    fun slotClock_routesWhenMedicationIdMissing() {
        // Mirrors MedicationClockRescheduler.registerAlarmForSlot.
        val kind = classifyAlarm(
            medicationId = -1L,
            clockSlotId = 21L,
            intervalSlotId = -1L,
            habitId = -1L
        )
        assertEquals(AlarmKind.SlotClock(21L), kind)
    }

    @Test
    fun slotInterval_routesWhenMedicationIdMissing() {
        // Mirrors MedicationIntervalRescheduler.registerAlarmForSlot.
        val kind = classifyAlarm(
            medicationId = -1L,
            clockSlotId = -1L,
            intervalSlotId = 42L,
            habitId = -1L
        )
        assertEquals(AlarmKind.SlotInterval(42L), kind)
    }

    @Test
    fun habitId_routesWhenOnlyHabitIdSet() {
        val kind = classifyAlarm(
            medicationId = -1L,
            clockSlotId = -1L,
            intervalSlotId = -1L,
            habitId = 11L
        )
        assertEquals(AlarmKind.Habit(11L), kind)
    }

    @Test
    fun unknown_whenAllIdsAbsent() {
        val kind = classifyAlarm(
            medicationId = -1L,
            clockSlotId = -1L,
            intervalSlotId = -1L,
            habitId = -1L
        )
        assertEquals(AlarmKind.Unknown, kind)
    }

    @Test
    fun medicationId_winsWhenMultipleIdsPresent() {
        val kind = classifyAlarm(
            medicationId = 1L,
            clockSlotId = 2L,
            intervalSlotId = 3L,
            habitId = 4L
        )
        assertEquals(AlarmKind.Medication(1L), kind)
    }

    @Test
    fun slotClock_winsOverSlotIntervalAndHabit() {
        // A slot configured for CLOCK should route via the clock branch
        // even if a stale interval extra somehow sticks around.
        val kind = classifyAlarm(
            medicationId = -1L,
            clockSlotId = 5L,
            intervalSlotId = 7L,
            habitId = 9L
        )
        assertEquals(AlarmKind.SlotClock(5L), kind)
    }

    @Test
    fun slotInterval_winsOverHabit() {
        val kind = classifyAlarm(
            medicationId = -1L,
            clockSlotId = -1L,
            intervalSlotId = 5L,
            habitId = 9L
        )
        assertEquals(AlarmKind.SlotInterval(5L), kind)
    }

    @Test
    fun zero_idsAreValid() {
        // The receiver's previous `>= 0` predicate accepted 0; preserved here
        // because Room autoincrement could theoretically hit 0 on first row.
        assertEquals(
            AlarmKind.Medication(0L),
            classifyAlarm(
                medicationId = 0L,
                clockSlotId = -1L,
                intervalSlotId = -1L,
                habitId = -1L
            )
        )
        assertEquals(
            AlarmKind.SlotClock(0L),
            classifyAlarm(
                medicationId = -1L,
                clockSlotId = 0L,
                intervalSlotId = -1L,
                habitId = -1L
            )
        )
        assertEquals(
            AlarmKind.SlotInterval(0L),
            classifyAlarm(
                medicationId = -1L,
                clockSlotId = -1L,
                intervalSlotId = 0L,
                habitId = -1L
            )
        )
    }
}
