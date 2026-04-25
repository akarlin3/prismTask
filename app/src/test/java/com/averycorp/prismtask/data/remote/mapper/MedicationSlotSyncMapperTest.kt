package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationMarkEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotOverrideEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip tests for the v1.5 medication slot system sync mappers
 * (spec: A2 #6 PR1). One case per new collection:
 * medication_slots, medication_slot_overrides, medication_tier_states.
 *
 * Also covers the junction-rebuild ingredient: `medicationToMap` now
 * accepts a `slotCloudIds` list and serializes it; `extractSlotCloudIds`
 * reads it back on pull. Junction rows themselves are not synced — they
 * are rebuilt from the parent medication's embedded list.
 */
class MedicationSlotSyncMapperTest {

    @Test
    fun medicationSlot_roundTripsAllFields() {
        val source = MedicationSlotEntity(
            id = 7,
            cloudId = null,
            name = "Morning",
            idealTime = "08:30",
            driftMinutes = 60,
            sortOrder = 1,
            isActive = true,
            createdAt = 100L,
            updatedAt = 200L
        )
        val map = MedicationSyncMapper.medicationSlotToMap(source)
        val decoded = MedicationSyncMapper.mapToMedicationSlot(
            map,
            localId = source.id,
            cloudId = "slot-cloud-1"
        )
        assertEquals("slot-cloud-1", decoded.cloudId)
        assertEquals(source.copy(cloudId = "slot-cloud-1"), decoded)
    }

    @Test
    fun medicationSlotOverride_roundTripsWithFkResolution() {
        val source = MedicationSlotOverrideEntity(
            id = 3,
            medicationId = 11,
            slotId = 22,
            overrideIdealTime = "09:15",
            overrideDriftMinutes = 45,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = MedicationSyncMapper.medicationSlotOverrideToMap(
            source,
            medicationCloudId = "med-cloud-abc",
            slotCloudId = "slot-cloud-xyz"
        )
        assertEquals("med-cloud-abc", map["medicationCloudId"])
        assertEquals("slot-cloud-xyz", map["slotCloudId"])
        val decoded = MedicationSyncMapper.mapToMedicationSlotOverride(
            map,
            localId = source.id,
            medicationLocalId = 11,
            slotLocalId = 22,
            cloudId = "override-cloud"
        )
        assertEquals("override-cloud", decoded.cloudId)
        assertEquals(source.copy(cloudId = "override-cloud"), decoded)
    }

    @Test
    fun medicationSlotOverride_nullOverrideFieldsRoundTrip() {
        val source = MedicationSlotOverrideEntity(
            id = 5,
            medicationId = 1,
            slotId = 2,
            overrideIdealTime = null,
            overrideDriftMinutes = null,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = MedicationSyncMapper.medicationSlotOverrideToMap(
            source,
            medicationCloudId = "m",
            slotCloudId = "s"
        )
        val decoded = MedicationSyncMapper.mapToMedicationSlotOverride(
            map,
            localId = source.id,
            medicationLocalId = 1,
            slotLocalId = 2,
            cloudId = null
        )
        assertEquals(null, decoded.overrideIdealTime)
        assertEquals(null, decoded.overrideDriftMinutes)
    }

    @Test
    fun medicationTierState_roundTripsAllFields() {
        val source = MedicationTierStateEntity(
            id = 9,
            medicationId = 33,
            slotId = 44,
            logDate = "2026-04-22",
            tier = "prescription",
            tierSource = "user_set",
            createdAt = 1_000L,
            updatedAt = 2_000L
        )
        val map = MedicationSyncMapper.medicationTierStateToMap(
            source,
            medicationCloudId = "med-c",
            slotCloudId = "slot-c"
        )
        val decoded = MedicationSyncMapper.mapToMedicationTierState(
            map,
            localId = source.id,
            medicationLocalId = 33,
            slotLocalId = 44,
            cloudId = "state-c"
        )
        assertEquals("state-c", decoded.cloudId)
        assertEquals(source.copy(cloudId = "state-c"), decoded)
    }

    @Test
    fun medicationToMap_embedsSlotCloudIdsList() {
        val med = MedicationEntity(id = 1, name = "Lamotrigine")
        val map = MedicationSyncMapper.medicationToMap(
            med,
            slotCloudIds = listOf("slot-a", "slot-b")
        )
        assertEquals(listOf("slot-a", "slot-b"), map["slotCloudIds"])
    }

    @Test
    fun medicationToMap_omittedSlotCloudIdsDefaultsToEmptyList() {
        val med = MedicationEntity(id = 1, name = "Adderall")
        val map = MedicationSyncMapper.medicationToMap(med)
        assertEquals(emptyList<String>(), map["slotCloudIds"])
    }

    @Test
    fun extractSlotCloudIds_parsesList() {
        val data = mapOf<String, Any?>("slotCloudIds" to listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), MedicationSyncMapper.extractSlotCloudIds(data))
    }

    @Test
    fun medicationSlot_reminderModeFieldsRoundTrip() {
        val source = MedicationSlotEntity(
            id = 12,
            cloudId = null,
            name = "Evening",
            idealTime = "20:00",
            driftMinutes = 90,
            sortOrder = 2,
            isActive = true,
            reminderMode = "INTERVAL",
            reminderIntervalMinutes = 360,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = MedicationSyncMapper.medicationSlotToMap(source)
        assertEquals("INTERVAL", map["reminderMode"])
        assertEquals(360, map["reminderIntervalMinutes"])
        val decoded = MedicationSyncMapper.mapToMedicationSlot(map, localId = source.id)
        assertEquals("INTERVAL", decoded.reminderMode)
        assertEquals(360, decoded.reminderIntervalMinutes)
    }

    @Test
    fun medicationSlot_nullReminderModeRoundTripsAsInherit() {
        val source = MedicationSlotEntity(
            id = 1,
            name = "Morning",
            idealTime = "08:00",
            reminderMode = null,
            reminderIntervalMinutes = null,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = MedicationSyncMapper.medicationSlotToMap(source)
        val decoded = MedicationSyncMapper.mapToMedicationSlot(map, localId = source.id)
        assertEquals(null, decoded.reminderMode)
        assertEquals(null, decoded.reminderIntervalMinutes)
    }

    @Test
    fun medication_reminderModeFieldsRoundTrip() {
        val source = MedicationEntity(
            id = 7,
            name = "Adderall",
            reminderMode = "CLOCK",
            reminderIntervalMinutes = null
        )
        val map = MedicationSyncMapper.medicationToMap(source)
        assertEquals("CLOCK", map["reminderMode"])
        val decoded = MedicationSyncMapper.mapToMedication(map, localId = source.id)
        assertEquals("CLOCK", decoded.reminderMode)
        assertEquals(null, decoded.reminderIntervalMinutes)
    }

    @Test
    fun extractSlotCloudIds_missingFieldReturnsEmpty() {
        val data = mapOf<String, Any?>("name" to "X")
        assertEquals(emptyList<String>(), MedicationSyncMapper.extractSlotCloudIds(data))
    }

    // --- v1.5.4 medication time logging (PR2 of 4) ---

    @Test
    fun medicationTierState_roundTripsIntendedTimeAndLoggedAt() {
        val source = MedicationTierStateEntity(
            id = 12,
            medicationId = 1,
            slotId = 1,
            logDate = "2026-04-23",
            tier = "complete",
            tierSource = "user_set",
            intendedTime = 1_700_000_000_000L,
            loggedAt = 1_700_000_360_000L,
            createdAt = 1_700_000_360_000L,
            updatedAt = 1_700_000_360_000L
        )
        val map = MedicationSyncMapper.medicationTierStateToMap(
            source,
            medicationCloudId = "med-c",
            slotCloudId = "slot-c"
        )
        // Confirm both fields land in the wire payload.
        assertEquals(1_700_000_000_000L, map["intendedTime"])
        assertEquals(1_700_000_360_000L, map["loggedAt"])

        val decoded = MedicationSyncMapper.mapToMedicationTierState(
            map,
            localId = source.id,
            medicationLocalId = 1,
            slotLocalId = 1,
            cloudId = "ts-c"
        )
        assertEquals(1_700_000_000_000L, decoded.intendedTime)
        assertEquals(1_700_000_360_000L, decoded.loggedAt)
    }

    @Test
    fun medicationTierState_legacyDocWithoutLoggedAtFallsBackToUpdatedAt() {
        // Pulled docs from older clients won't carry the new fields.
        // intendedTime stays null; loggedAt falls back to updatedAt so
        // every row has a non-zero stamp.
        val legacy = mapOf<String, Any?>(
            "logDate" to "2026-01-01",
            "tier" to "essential",
            "tierSource" to "computed",
            "createdAt" to 100L,
            "updatedAt" to 200L
        )
        val decoded = MedicationSyncMapper.mapToMedicationTierState(
            legacy,
            localId = 1,
            medicationLocalId = 1,
            slotLocalId = 1,
            cloudId = "legacy"
        )
        assertEquals(null, decoded.intendedTime)
        assertEquals(200L, decoded.loggedAt)
    }

    @Test
    fun medicationMark_roundTripsAllFields() {
        val source = MedicationMarkEntity(
            id = 5,
            medicationId = 1,
            medicationTierStateId = 1,
            intendedTime = 1_700_000_000_000L,
            loggedAt = 1_700_000_300_000L,
            markedTaken = true,
            updatedAt = 1_700_000_300_000L
        )
        val map = MedicationSyncMapper.medicationMarkToMap(
            source,
            medicationCloudId = "med-c",
            tierStateCloudId = "ts-c"
        )
        assertEquals(1_700_000_000_000L, map["intendedTime"])
        assertEquals(1_700_000_300_000L, map["loggedAt"])
        assertEquals(true, map["markedTaken"])

        val decoded = MedicationSyncMapper.mapToMedicationMark(
            map,
            localId = source.id,
            medicationLocalId = 1,
            tierStateLocalId = 1,
            cloudId = "mark-c"
        )
        assertEquals("mark-c", decoded.cloudId)
        assertEquals(source.copy(cloudId = "mark-c"), decoded)
    }

    @Test
    fun medicationMark_unmarkedRoundTrips() {
        val source = MedicationMarkEntity(
            id = 6,
            medicationId = 1,
            medicationTierStateId = 1,
            intendedTime = null,
            loggedAt = 5_000L,
            markedTaken = false,
            updatedAt = 5_000L
        )
        val map = MedicationSyncMapper.medicationMarkToMap(
            source,
            medicationCloudId = "m",
            tierStateCloudId = "t"
        )
        val decoded = MedicationSyncMapper.mapToMedicationMark(
            map,
            localId = source.id,
            medicationLocalId = 1,
            tierStateLocalId = 1,
            cloudId = "mark-x"
        )
        assertEquals(false, decoded.markedTaken)
        assertEquals(null, decoded.intendedTime)
    }
}
