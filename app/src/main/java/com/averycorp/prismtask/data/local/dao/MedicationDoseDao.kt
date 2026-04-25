package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDoseDao {
    @Query("SELECT * FROM medication_doses WHERE taken_date_local = :date ORDER BY taken_at ASC")
    fun getForDate(date: String): Flow<List<MedicationDoseEntity>>

    @Query("SELECT * FROM medication_doses ORDER BY taken_at DESC")
    fun observeAll(): Flow<List<MedicationDoseEntity>>

    /**
     * Most-recent dose across every medication, real or synthetic. The
     * interval-mode reminder rescheduler uses this as the anchor — synthetic
     * skip doses (written by the SKIPPED tier-state path) MUST be included
     * so a skipped slot resets the interval clock.
     */
    @Query("SELECT * FROM medication_doses ORDER BY taken_at DESC LIMIT 1")
    suspend fun getMostRecentDoseAnyOnce(): MedicationDoseEntity?

    /**
     * Reactive equivalent of [getMostRecentDoseAnyOnce]. Emits whenever any
     * dose row changes; downstream consumers debounce burst writes.
     */
    @Query("SELECT * FROM medication_doses ORDER BY taken_at DESC LIMIT 1")
    fun observeMostRecentDoseAny(): Flow<MedicationDoseEntity?>

    /**
     * Most-recent NON-synthetic dose. Use for UI dose history / streak /
     * last-taken displays — never for interval-mode anchoring (that needs
     * [getMostRecentDoseAnyOnce]).
     */
    @Query(
        "SELECT * FROM medication_doses WHERE is_synthetic_skip = 0 " +
            "ORDER BY taken_at DESC LIMIT 1"
    )
    suspend fun getMostRecentRealDoseOnce(): MedicationDoseEntity?

    @Query(
        "SELECT * FROM medication_doses WHERE medication_id = :medicationId " +
            "AND taken_date_local = :date ORDER BY taken_at ASC"
    )
    fun getForMedOnDate(medicationId: Long, date: String): Flow<List<MedicationDoseEntity>>

    @Query(
        "SELECT COUNT(*) FROM medication_doses WHERE medication_id = :medicationId " +
            "AND taken_date_local = :date"
    )
    suspend fun countForMedOnDateOnce(medicationId: Long, date: String): Int

    @Query("SELECT COUNT(*) FROM medication_doses WHERE medication_id = :medicationId")
    suspend fun countForMedOnce(medicationId: Long): Int

    @Query("SELECT * FROM medication_doses ORDER BY taken_at ASC")
    suspend fun getAllOnce(): List<MedicationDoseEntity>

    @Query("SELECT * FROM medication_doses WHERE medication_id = :medicationId ORDER BY taken_at ASC")
    suspend fun getAllForMedOnce(medicationId: Long): List<MedicationDoseEntity>

    @Query(
        "SELECT * FROM medication_doses WHERE medication_id = :medicationId " +
            "ORDER BY taken_at DESC LIMIT 1"
    )
    suspend fun getLatestForMedOnce(medicationId: Long): MedicationDoseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(dose: MedicationDoseEntity): Long

    @Update
    suspend fun update(dose: MedicationDoseEntity)

    @Delete
    suspend fun delete(dose: MedicationDoseEntity)

    @Query("DELETE FROM medication_doses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM medication_doses WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): MedicationDoseEntity?

    @Query("UPDATE medication_doses SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)

    @Query("UPDATE medication_doses SET medication_id = :newId WHERE medication_id = :oldId")
    suspend fun reassignMedicationId(oldId: Long, newId: Long)
}
