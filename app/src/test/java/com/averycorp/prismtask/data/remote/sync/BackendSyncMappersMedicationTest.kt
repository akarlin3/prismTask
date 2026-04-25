package com.averycorp.prismtask.data.remote.sync

import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapper tests for the medication time-logging /sync/push wiring. The
 * tier_state mapper must:
 * - Send `*_cloud_id` payload keys (NOT integer FKs) so the backend's
 *   `_resolve_cloud_fk_for_medication` resolver can scope by user_id.
 * - Skip the row entirely when a parent's cloud_id isn't available yet
 *   (parents must round-trip through Firestore + sync first).
 * - Round-trip `intended_time` (as ISO 8601) when present, omit when null.
 *
 * The mark mapper was dropped in chore/drop-orphan-medication-marks
 * because no production write path ever populated the table.
 */
class BackendSyncMappersMedicationTest {

    @Test
    fun medication_serializesNameNotesIsActive() {
        val med = MedicationEntity(
            id = 7,
            cloudId = "med-cloud-7",
            name = "Adderall",
            notes = "20mg",
            isArchived = false,
            updatedAt = 1_700_000_000_000L
        )
        val op = medicationToOperation(med)
        assertEquals("medication", op.entityType)
        assertEquals("update", op.operation)
        assertEquals(7L, op.entityId)
        assertEquals("med-cloud-7", op.data?.get("cloud_id")?.asString)
        assertEquals("Adderall", op.data?.get("name")?.asString)
        assertEquals("20mg", op.data?.get("notes")?.asString)
        assertEquals(true, op.data?.get("is_active")?.asBoolean)
    }

    @Test
    fun medication_archivedFlagInverts() {
        val med = MedicationEntity(
            id = 1,
            name = "Old",
            isArchived = true,
            updatedAt = 1L
        )
        val op = medicationToOperation(med)
        // Backend's `is_active` is the negation of Android's `is_archived`.
        assertEquals(false, op.data?.get("is_active")?.asBoolean)
    }

    @Test
    fun medicationSlot_serializesAllFields() {
        val slot = MedicationSlotEntity(
            id = 11,
            cloudId = "slot-cloud-11",
            name = "Morning",
            idealTime = "08:00",
            driftMinutes = 60,
            isActive = true,
            updatedAt = 1_700_000_000_000L
        )
        val op = medicationSlotToOperation(slot)
        assertEquals("medication_slot", op.entityType)
        assertEquals(11L, op.entityId)
        assertEquals("slot-cloud-11", op.data?.get("cloud_id")?.asString)
        assertEquals("Morning", op.data?.get("slot_key")?.asString)
        assertEquals("08:00", op.data?.get("ideal_time")?.asString)
        assertEquals(60, op.data?.get("drift_minutes")?.asInt)
        assertEquals(true, op.data?.get("is_active")?.asBoolean)
    }

    @Test
    fun tierState_skippedWhenMedicationCloudIdMissing() {
        val state = MedicationTierStateEntity(
            id = 1,
            medicationId = 1,
            slotId = 1,
            logDate = "2026-04-23",
            tier = "complete"
        )
        // Parent medication isn't synced yet → can't safely push.
        val op = medicationTierStateToOperation(
            state,
            medicationCloudId = null,
            slotCloudId = "slot-c"
        )
        assertNull(op)
    }

    @Test
    fun tierState_skippedWhenSlotCloudIdMissing() {
        val state = MedicationTierStateEntity(
            id = 1,
            medicationId = 1,
            slotId = 1,
            logDate = "2026-04-23",
            tier = "complete"
        )
        val op = medicationTierStateToOperation(
            state,
            medicationCloudId = "med-c",
            slotCloudId = null
        )
        assertNull(op)
    }

    @Test
    fun tierState_emitsCloudIdFKsAndIntendedTimeWhenPresent() {
        val state = MedicationTierStateEntity(
            id = 5,
            cloudId = "ts-cloud-5",
            medicationId = 1,
            slotId = 1,
            logDate = "2026-04-23",
            tier = "complete",
            tierSource = "user_set",
            intendedTime = 1_700_000_000_000L,
            loggedAt = 1_700_000_360_000L,
            updatedAt = 1_700_000_360_000L
        )
        val op = medicationTierStateToOperation(
            state,
            medicationCloudId = "med-cloud-1",
            slotCloudId = "slot-cloud-1"
        )!!
        assertEquals("medication_tier_state", op.entityType)
        assertEquals("ts-cloud-5", op.data?.get("cloud_id")?.asString)
        assertEquals("med-cloud-1", op.data?.get("medication_cloud_id")?.asString)
        assertEquals("slot-cloud-1", op.data?.get("slot_cloud_id")?.asString)
        assertEquals("complete", op.data?.get("tier")?.asString)
        assertEquals("user_set", op.data?.get("tier_source")?.asString)
        assertEquals("2026-04-23", op.data?.get("log_date")?.asString)
        // Both timestamps land as ISO strings (Pydantic rejects epoch millis).
        assertTrue(op.data?.get("intended_time")?.asString?.startsWith("20") == true)
        assertTrue(op.data?.get("logged_at")?.asString?.startsWith("20") == true)
        // Backend integer FKs MUST NOT be present.
        assertNull(op.data?.get("medication_id"))
        assertNull(op.data?.get("slot_id"))
    }

    @Test
    fun tierState_omitsIntendedTimeWhenNull() {
        val state = MedicationTierStateEntity(
            id = 1,
            medicationId = 1,
            slotId = 1,
            logDate = "2026-04-23",
            tier = "skipped",
            intendedTime = null,
            loggedAt = 1L
        )
        val op = medicationTierStateToOperation(
            state,
            medicationCloudId = "m",
            slotCloudId = "s"
        )!!
        assertNull(op.data?.get("intended_time"))
    }

}
