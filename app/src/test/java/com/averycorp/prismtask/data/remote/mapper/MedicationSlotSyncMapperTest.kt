package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.MedicationEntity
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
    fun extractSlotCloudIds_missingFieldReturnsEmpty() {
        val data = mapOf<String, Any?>("name" to "X")
        assertEquals(emptyList<String>(), MedicationSyncMapper.extractSlotCloudIds(data))
    }
}
