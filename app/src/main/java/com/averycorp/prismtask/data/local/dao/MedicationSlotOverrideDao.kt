package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MedicationSlotOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationSlotOverrideDao {
    @Query("SELECT * FROM medication_slot_overrides WHERE medication_id = :medicationId")
    fun observeForMedication(medicationId: Long): Flow<List<MedicationSlotOverrideEntity>>

    @Query("SELECT * FROM medication_slot_overrides WHERE medication_id = :medicationId")
    suspend fun getForMedicationOnce(medicationId: Long): List<MedicationSlotOverrideEntity>

    @Query(
        "SELECT * FROM medication_slot_overrides WHERE medication_id = :medicationId AND slot_id = :slotId LIMIT 1"
    )
    suspend fun getForPairOnce(medicationId: Long, slotId: Long): MedicationSlotOverrideEntity?

    @Query("SELECT * FROM medication_slot_overrides WHERE id = :id")
    suspend fun getByIdOnce(id: Long): MedicationSlotOverrideEntity?

    @Query("SELECT * FROM medication_slot_overrides WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): MedicationSlotOverrideEntity?

    @Query("SELECT * FROM medication_slot_overrides")
    suspend fun getAllOnce(): List<MedicationSlotOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(override: MedicationSlotOverrideEntity): Long

    @Update
    suspend fun update(override: MedicationSlotOverrideEntity)

    @Delete
    suspend fun delete(override: MedicationSlotOverrideEntity)

    @Query("DELETE FROM medication_slot_overrides WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM medication_slot_overrides WHERE medication_id = :medicationId AND slot_id = :slotId")
    suspend fun deleteForPair(medicationId: Long, slotId: Long)

    @Query("UPDATE medication_slot_overrides SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)
}
