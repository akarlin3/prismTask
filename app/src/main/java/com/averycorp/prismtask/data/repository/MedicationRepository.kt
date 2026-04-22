package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-source-of-truth repository for the top-level Medication entity.
 * Every write path calls [SyncTracker] with the correct entity type
 * (`"medication"` or `"medication_dose"`) so the sync layer picks up
 * the change on the next push cycle.
 *
 * PR 2 of the medication refactor (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md`). The Medication UI still reads
 * from `SelfCareRepository` until PR 4 rewires the viewmodels.
 */
@Singleton
class MedicationRepository
@Inject
constructor(
    private val medicationDao: MedicationDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val syncTracker: SyncTracker,
    private val taskBehaviorPreferences: TaskBehaviorPreferences
) {
    fun observeActive(): Flow<List<MedicationEntity>> = medicationDao.getActive()

    fun observeAll(): Flow<List<MedicationEntity>> = medicationDao.getAll()

    fun observeById(id: Long): Flow<MedicationEntity?> = medicationDao.observeById(id)

    fun observeDosesForDate(date: String): Flow<List<MedicationDoseEntity>> =
        medicationDoseDao.getForDate(date)

    fun observeAllDoses(): Flow<List<MedicationDoseEntity>> =
        medicationDoseDao.observeAll()

    fun observeDosesForMedOnDate(
        medicationId: Long,
        date: String
    ): Flow<List<MedicationDoseEntity>> =
        medicationDoseDao.getForMedOnDate(medicationId, date)

    suspend fun getByIdOnce(id: Long): MedicationEntity? =
        medicationDao.getByIdOnce(id)

    suspend fun getByNameOnce(name: String): MedicationEntity? =
        medicationDao.getByNameOnce(name)

    suspend fun getActiveOnce(): List<MedicationEntity> =
        medicationDao.getActiveOnce()

    suspend fun getAllOnce(): List<MedicationEntity> =
        medicationDao.getAllOnce()

    suspend fun insert(medication: MedicationEntity): Long {
        val now = System.currentTimeMillis()
        val id = medicationDao.insert(
            medication.copy(createdAt = now, updatedAt = now)
        )
        syncTracker.trackCreate(id, "medication")
        return id
    }

    suspend fun update(medication: MedicationEntity) {
        val updated = medication.copy(updatedAt = System.currentTimeMillis())
        medicationDao.update(updated)
        syncTracker.trackUpdate(updated.id, "medication")
    }

    suspend fun archive(id: Long) {
        val now = System.currentTimeMillis()
        medicationDao.archive(id, now)
        syncTracker.trackUpdate(id, "medication")
    }

    /**
     * Deletes the medication AND its dose history via the FK CASCADE.
     * UI primary affordance should be [archive], not delete — this call
     * path is for explicit user-initiated deletions with a confirmation
     * dialog, or for test / cleanup code.
     */
    suspend fun delete(medication: MedicationEntity) {
        medicationDao.delete(medication)
        syncTracker.trackDelete(medication.id, "medication")
    }

    suspend fun logDose(
        medicationId: Long,
        slotKey: String,
        takenAt: Long = System.currentTimeMillis(),
        note: String = ""
    ): Long {
        val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
        val dateLocal = DayBoundary.currentLocalDateString(dayStartHour, takenAt)
        val now = System.currentTimeMillis()
        val dose = MedicationDoseEntity(
            medicationId = medicationId,
            slotKey = slotKey,
            takenAt = takenAt,
            takenDateLocal = dateLocal,
            note = note,
            createdAt = now,
            updatedAt = now
        )
        val id = medicationDoseDao.insert(dose)
        syncTracker.trackCreate(id, "medication_dose")
        return id
    }

    suspend fun unlogDose(dose: MedicationDoseEntity) {
        medicationDoseDao.delete(dose)
        syncTracker.trackDelete(dose.id, "medication_dose")
    }

    suspend fun updateDose(dose: MedicationDoseEntity) {
        val updated = dose.copy(updatedAt = System.currentTimeMillis())
        medicationDoseDao.update(updated)
        syncTracker.trackUpdate(updated.id, "medication_dose")
    }

    suspend fun countDosesForMedOnDateOnce(medicationId: Long, date: String): Int =
        medicationDoseDao.countForMedOnDateOnce(medicationId, date)

    suspend fun getLatestDoseForMedOnce(medicationId: Long): MedicationDoseEntity? =
        medicationDoseDao.getLatestForMedOnce(medicationId)
}
