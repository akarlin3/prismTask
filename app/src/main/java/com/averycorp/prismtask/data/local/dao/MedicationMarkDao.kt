package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MedicationMarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationMarkDao {
    @Query("SELECT * FROM medication_marks WHERE medication_tier_state_id = :tierStateId")
    fun observeForSlot(tierStateId: Long): Flow<List<MedicationMarkEntity>>

    @Query("SELECT * FROM medication_marks WHERE medication_tier_state_id = :tierStateId")
    suspend fun getForSlotOnce(tierStateId: Long): List<MedicationMarkEntity>

    @Query(
        """
        SELECT * FROM medication_marks
        WHERE medication_id = :medicationId
          AND medication_tier_state_id = :tierStateId
        LIMIT 1
        """
    )
    suspend fun getForPairOnce(medicationId: Long, tierStateId: Long): MedicationMarkEntity?

    @Query("SELECT * FROM medication_marks WHERE id = :id")
    suspend fun getByIdOnce(id: Long): MedicationMarkEntity?

    @Query("SELECT * FROM medication_marks WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): MedicationMarkEntity?

    @Query("SELECT * FROM medication_marks")
    suspend fun getAllOnce(): List<MedicationMarkEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(mark: MedicationMarkEntity): Long

    @Update
    suspend fun update(mark: MedicationMarkEntity)

    @Delete
    suspend fun delete(mark: MedicationMarkEntity)

    @Query("DELETE FROM medication_marks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE medication_marks SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)
}
