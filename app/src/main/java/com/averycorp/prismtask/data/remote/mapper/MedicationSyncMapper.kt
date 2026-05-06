package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotOverrideEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity

/**
 * Firestore ↔ Room mappers for the top-level Medication entity (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §4.1). Split from [SyncMapper] so
 * the medication-specific field set doesn't crowd the main mapper; the
 * object pattern mirrors SyncMapper so call sites stay consistent.
 */
object MedicationSyncMapper {
    /**
     * Embed the list of slot cloud-ids this medication is linked to on
     * the parent Firestore document. The junction table
     * `medication_medication_slots` is rebuilt on pull from this list
     * rather than synced as a first-class collection. Mirrors the
     * `task_tags` ↔ `tagIds`-on-task pattern in [SyncMapper.taskToMap].
     */
    fun medicationToMap(
        med: MedicationEntity,
        slotCloudIds: List<String> = emptyList()
    ): Map<String, Any?> = mapOf(
        "localId" to med.id,
        "name" to med.name,
        "displayLabel" to med.displayLabel,
        "notes" to med.notes,
        "tier" to med.tier,
        "isArchived" to med.isArchived,
        "sortOrder" to med.sortOrder,
        "scheduleMode" to med.scheduleMode,
        "timesOfDay" to med.timesOfDay,
        "specificTimes" to med.specificTimes,
        "intervalMillis" to med.intervalMillis,
        "dosesPerDay" to med.dosesPerDay,
        "pillCount" to med.pillCount,
        "pillsPerDose" to med.pillsPerDose,
        "lastRefillDate" to med.lastRefillDate,
        "pharmacyName" to med.pharmacyName,
        "pharmacyPhone" to med.pharmacyPhone,
        "reminderDaysBefore" to med.reminderDaysBefore,
        "reminderMode" to med.reminderMode,
        "reminderIntervalMinutes" to med.reminderIntervalMinutes,
        "promptDoseAtLog" to med.promptDoseAtLog,
        "slotCloudIds" to slotCloudIds,
        "createdAt" to med.createdAt,
        "updatedAt" to med.updatedAt
    )

    /**
     * Extract the embedded slot cloud-id list from a pulled Firestore
     * medication document. Returns an empty list when the field is
     * missing (older docs written before the slot system shipped).
     */
    @Suppress("UNCHECKED_CAST")
    fun extractSlotCloudIds(data: Map<String, Any?>): List<String> {
        val raw = data["slotCloudIds"] as? List<*> ?: return emptyList()
        return raw.mapNotNull { it as? String }
    }

    fun mapToMedication(
        data: Map<String, Any?>,
        localId: Long = 0,
        cloudId: String? = null
    ): MedicationEntity = MedicationEntity(
        id = localId,
        cloudId = cloudId,
        name = data["name"] as? String ?: "",
        displayLabel = data["displayLabel"] as? String,
        notes = data["notes"] as? String ?: "",
        tier = data["tier"] as? String ?: "essential",
        isArchived = data["isArchived"] as? Boolean ?: false,
        sortOrder = (data["sortOrder"] as? Number)?.toInt() ?: 0,
        scheduleMode = data["scheduleMode"] as? String ?: "TIMES_OF_DAY",
        timesOfDay = data["timesOfDay"] as? String,
        specificTimes = data["specificTimes"] as? String,
        intervalMillis = (data["intervalMillis"] as? Number)?.toLong(),
        dosesPerDay = (data["dosesPerDay"] as? Number)?.toInt() ?: 1,
        pillCount = (data["pillCount"] as? Number)?.toInt(),
        pillsPerDose = (data["pillsPerDose"] as? Number)?.toInt() ?: 1,
        lastRefillDate = (data["lastRefillDate"] as? Number)?.toLong(),
        pharmacyName = data["pharmacyName"] as? String,
        pharmacyPhone = data["pharmacyPhone"] as? String,
        reminderDaysBefore = (data["reminderDaysBefore"] as? Number)?.toInt() ?: 3,
        reminderMode = (data["reminderMode"] as? String)?.takeIf { it.isNotBlank() },
        reminderIntervalMinutes = (data["reminderIntervalMinutes"] as? Number)?.toInt(),
        promptDoseAtLog = data["promptDoseAtLog"] as? Boolean ?: false,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun medicationDoseToMap(
        dose: MedicationDoseEntity,
        medicationCloudId: String?
    ): Map<String, Any?> = mapOf(
        "localId" to dose.id,
        "medicationCloudId" to medicationCloudId,
        "customMedicationName" to dose.customMedicationName,
        "slotKey" to dose.slotKey,
        "takenAt" to dose.takenAt,
        "takenDateLocal" to dose.takenDateLocal,
        "note" to dose.note,
        "isSyntheticSkip" to dose.isSyntheticSkip,
        "doseAmount" to dose.doseAmount,
        "createdAt" to dose.createdAt,
        "updatedAt" to dose.updatedAt
    )

    fun mapToMedicationDose(
        data: Map<String, Any?>,
        localId: Long = 0,
        medicationLocalId: Long? = null,
        cloudId: String? = null
    ): MedicationDoseEntity = MedicationDoseEntity(
        id = localId,
        cloudId = cloudId,
        medicationId = medicationLocalId,
        customMedicationName = data["customMedicationName"] as? String,
        slotKey = data["slotKey"] as? String ?: "anytime",
        takenAt = (data["takenAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        takenDateLocal = data["takenDateLocal"] as? String ?: "",
        note = data["note"] as? String ?: "",
        isSyntheticSkip = data["isSyntheticSkip"] as? Boolean ?: false,
        doseAmount = data["doseAmount"] as? String,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    // ── v1.5 medication slot system (A2 #6 PR1) ──────────────────────

    fun medicationSlotToMap(slot: MedicationSlotEntity): Map<String, Any?> = mapOf(
        "localId" to slot.id,
        "name" to slot.name,
        "idealTime" to slot.idealTime,
        "driftMinutes" to slot.driftMinutes,
        "sortOrder" to slot.sortOrder,
        "isActive" to slot.isActive,
        "reminderMode" to slot.reminderMode,
        "reminderIntervalMinutes" to slot.reminderIntervalMinutes,
        "createdAt" to slot.createdAt,
        "updatedAt" to slot.updatedAt
    )

    fun mapToMedicationSlot(
        data: Map<String, Any?>,
        localId: Long = 0,
        cloudId: String? = null
    ): MedicationSlotEntity = MedicationSlotEntity(
        id = localId,
        cloudId = cloudId,
        name = data["name"] as? String ?: "",
        idealTime = data["idealTime"] as? String ?: "09:00",
        driftMinutes = (data["driftMinutes"] as? Number)?.toInt() ?: 180,
        sortOrder = (data["sortOrder"] as? Number)?.toInt() ?: 0,
        isActive = data["isActive"] as? Boolean ?: true,
        reminderMode = (data["reminderMode"] as? String)?.takeIf { it.isNotBlank() },
        reminderIntervalMinutes = (data["reminderIntervalMinutes"] as? Number)?.toInt(),
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun medicationSlotOverrideToMap(
        override: MedicationSlotOverrideEntity,
        medicationCloudId: String,
        slotCloudId: String
    ): Map<String, Any?> = mapOf(
        "localId" to override.id,
        "medicationCloudId" to medicationCloudId,
        "slotCloudId" to slotCloudId,
        "overrideIdealTime" to override.overrideIdealTime,
        "overrideDriftMinutes" to override.overrideDriftMinutes,
        "createdAt" to override.createdAt,
        "updatedAt" to override.updatedAt
    )

    fun mapToMedicationSlotOverride(
        data: Map<String, Any?>,
        localId: Long = 0,
        medicationLocalId: Long = 0,
        slotLocalId: Long = 0,
        cloudId: String? = null
    ): MedicationSlotOverrideEntity = MedicationSlotOverrideEntity(
        id = localId,
        cloudId = cloudId,
        medicationId = medicationLocalId,
        slotId = slotLocalId,
        overrideIdealTime = data["overrideIdealTime"] as? String,
        overrideDriftMinutes = (data["overrideDriftMinutes"] as? Number)?.toInt(),
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun medicationTierStateToMap(
        state: MedicationTierStateEntity,
        medicationCloudId: String,
        slotCloudId: String
    ): Map<String, Any?> = mapOf(
        "localId" to state.id,
        "medicationCloudId" to medicationCloudId,
        "slotCloudId" to slotCloudId,
        "logDate" to state.logDate,
        "tier" to state.tier,
        "tierSource" to state.tierSource,
        "intendedTime" to state.intendedTime,
        "loggedAt" to state.loggedAt,
        "createdAt" to state.createdAt,
        "updatedAt" to state.updatedAt
    )

    fun mapToMedicationTierState(
        data: Map<String, Any?>,
        localId: Long = 0,
        medicationLocalId: Long = 0,
        slotLocalId: Long = 0,
        cloudId: String? = null
    ): MedicationTierStateEntity {
        // Pulled docs from older clients won't carry intendedTime / loggedAt.
        // intendedTime stays null (honest "we don't know"); loggedAt falls
        // back to updatedAt so every row has a non-zero stamp.
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        return MedicationTierStateEntity(
            id = localId,
            cloudId = cloudId,
            medicationId = medicationLocalId,
            slotId = slotLocalId,
            logDate = data["logDate"] as? String ?: "",
            tier = data["tier"] as? String ?: "skipped",
            tierSource = data["tierSource"] as? String ?: "computed",
            intendedTime = (data["intendedTime"] as? Number)?.toLong(),
            loggedAt = (data["loggedAt"] as? Number)?.toLong() ?: updatedAt,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = updatedAt
        )
    }
}
