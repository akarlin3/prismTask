package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity

/**
 * Firestore ↔ Room mappers for the top-level Medication entity (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §4.1). Split from [SyncMapper] so
 * the medication-specific field set doesn't crowd the main mapper; the
 * object pattern mirrors SyncMapper so call sites stay consistent.
 */
object MedicationSyncMapper {
    fun medicationToMap(med: MedicationEntity): Map<String, Any?> = mapOf(
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
        "createdAt" to med.createdAt,
        "updatedAt" to med.updatedAt
    )

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
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun medicationDoseToMap(
        dose: MedicationDoseEntity,
        medicationCloudId: String
    ): Map<String, Any?> = mapOf(
        "localId" to dose.id,
        "medicationCloudId" to medicationCloudId,
        "slotKey" to dose.slotKey,
        "takenAt" to dose.takenAt,
        "takenDateLocal" to dose.takenDateLocal,
        "note" to dose.note,
        "createdAt" to dose.createdAt,
        "updatedAt" to dose.updatedAt
    )

    fun mapToMedicationDose(
        data: Map<String, Any?>,
        localId: Long = 0,
        medicationLocalId: Long = 0,
        cloudId: String? = null
    ): MedicationDoseEntity = MedicationDoseEntity(
        id = localId,
        cloudId = cloudId,
        medicationId = medicationLocalId,
        slotKey = data["slotKey"] as? String ?: "anytime",
        takenAt = (data["takenAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        takenDateLocal = data["takenDateLocal"] as? String ?: "",
        note = data["note"] as? String ?: "",
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}
