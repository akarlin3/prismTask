package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.preferences.MedicationReminderMode
import com.averycorp.prismtask.data.preferences.MedicationReminderModePrefs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-function unit tests for the three-level precedence resolver
 * (medication → slot → global).
 */
class MedicationReminderModeResolverTest {

    private val globalClock = MedicationReminderModePrefs(
        mode = MedicationReminderMode.CLOCK,
        intervalDefaultMinutes = 240
    )
    private val globalInterval = MedicationReminderModePrefs(
        mode = MedicationReminderMode.INTERVAL,
        intervalDefaultMinutes = 360
    )
    private val slotInheriting = MedicationSlotEntity(id = 1, name = "Morning", idealTime = "08:00")
    private val medInheriting = MedicationEntity(id = 1, name = "Lipitor")

    @Test
    fun resolveMode_globalWinsWhenAllInherit() {
        assertEquals(
            MedicationReminderMode.CLOCK,
            MedicationReminderModeResolver.resolveReminderMode(
                medInheriting, slotInheriting, globalClock
            )
        )
        assertEquals(
            MedicationReminderMode.INTERVAL,
            MedicationReminderModeResolver.resolveReminderMode(
                medInheriting, slotInheriting, globalInterval
            )
        )
    }

    @Test
    fun resolveMode_slotOverridesGlobal() {
        val slot = slotInheriting.copy(reminderMode = "INTERVAL")
        assertEquals(
            MedicationReminderMode.INTERVAL,
            MedicationReminderModeResolver.resolveReminderMode(
                medInheriting, slot, globalClock
            )
        )
    }

    @Test
    fun resolveMode_medicationOverridesSlotAndGlobal() {
        val slot = slotInheriting.copy(reminderMode = "INTERVAL")
        val med = medInheriting.copy(reminderMode = "CLOCK")
        assertEquals(
            MedicationReminderMode.CLOCK,
            MedicationReminderModeResolver.resolveReminderMode(
                med, slot, globalInterval
            )
        )
    }

    @Test
    fun resolveMode_unknownStringTreatedAsInherit() {
        val slot = slotInheriting.copy(reminderMode = "BOGUS")
        val med = medInheriting.copy(reminderMode = "ALSO_BOGUS")
        // Both unknown → fall through to global.
        assertEquals(
            MedicationReminderMode.INTERVAL,
            MedicationReminderModeResolver.resolveReminderMode(med, slot, globalInterval)
        )
    }

    @Test
    fun resolveMode_nullMedicationConsultsSlotThenGlobal() {
        val slot = slotInheriting.copy(reminderMode = "INTERVAL")
        assertEquals(
            MedicationReminderMode.INTERVAL,
            MedicationReminderModeResolver.resolveReminderMode(
                medication = null,
                slot = slot,
                global = globalClock
            )
        )
        assertEquals(
            MedicationReminderMode.CLOCK,
            MedicationReminderModeResolver.resolveReminderMode(
                medication = null,
                slot = slotInheriting,
                global = globalClock
            )
        )
    }

    @Test
    fun resolveInterval_globalUsedWhenAllInherit() {
        assertEquals(
            240,
            MedicationReminderModeResolver.resolveIntervalMinutes(
                medInheriting, slotInheriting, globalClock
            )
        )
    }

    @Test
    fun resolveInterval_slotOverridesGlobal() {
        val slot = slotInheriting.copy(reminderIntervalMinutes = 120)
        assertEquals(
            120,
            MedicationReminderModeResolver.resolveIntervalMinutes(
                medInheriting, slot, globalClock
            )
        )
    }

    @Test
    fun resolveInterval_medicationOverridesSlotAndGlobal() {
        val slot = slotInheriting.copy(reminderIntervalMinutes = 120)
        val med = medInheriting.copy(reminderIntervalMinutes = 90)
        assertEquals(
            90,
            MedicationReminderModeResolver.resolveIntervalMinutes(med, slot, globalClock)
        )
    }

    @Test
    fun resolveInterval_clampsToMinAndMax() {
        val slotTooSmall = slotInheriting.copy(reminderIntervalMinutes = 5)
        assertEquals(
            60,
            MedicationReminderModeResolver.resolveIntervalMinutes(
                medInheriting, slotTooSmall, globalClock
            )
        )
        val slotTooLarge = slotInheriting.copy(reminderIntervalMinutes = 9999)
        assertEquals(
            1440,
            MedicationReminderModeResolver.resolveIntervalMinutes(
                medInheriting, slotTooLarge, globalClock
            )
        )
    }

    @Test
    fun resolveInterval_intervalModeWithoutAnyMinutesFallsBackToGlobal() {
        // Slot says INTERVAL but provides no interval — global default fills it.
        val slot = slotInheriting.copy(reminderMode = "INTERVAL", reminderIntervalMinutes = null)
        assertEquals(
            360,
            MedicationReminderModeResolver.resolveIntervalMinutes(
                medInheriting, slot, globalInterval
            )
        )
    }
}
