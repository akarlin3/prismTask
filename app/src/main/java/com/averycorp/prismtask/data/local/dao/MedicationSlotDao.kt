package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MedicationSlotCrossRef
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationSlotDao {
    @Query("SELECT * FROM medication_slots WHERE is_active = 1 ORDER BY sort_order ASC, ideal_time ASC, id ASC")
    fun observeActive(): Flow<List<MedicationSlotEntity>>

    @Query("SELECT * FROM medication_slots ORDER BY sort_order ASC, ideal_time ASC, id ASC")
    fun observeAll(): Flow<List<MedicationSlotEntity>>

    @Query("SELECT * FROM medication_slots WHERE id = :id")
    fun observeById(id: Long): Flow<MedicationSlotEntity?>

    @Query("SELECT * FROM medication_slots WHERE id = :id")
    suspend fun getByIdOnce(id: Long): MedicationSlotEntity?

    @Query("SELECT * FROM medication_slots WHERE is_active = 1 ORDER BY sort_order ASC, ideal_time ASC, id ASC")
    suspend fun getActiveOnce(): List<MedicationSlotEntity>

    @Query("SELECT * FROM medication_slots ORDER BY sort_order ASC, ideal_time ASC, id ASC")
    suspend fun getAllOnce(): List<MedicationSlotEntity>

    @Query("SELECT * FROM medication_slots WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): MedicationSlotEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(slot: MedicationSlotEntity): Long

    @Update
    suspend fun update(slot: MedicationSlotEntity)

    @Query("UPDATE medication_slots SET is_active = 0, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE medication_slots SET is_active = 1, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Delete
    suspend fun delete(slot: MedicationSlotEntity)

    @Query("DELETE FROM medication_slots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE medication_slots SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)

    // Junction table access — colocated to mirror the TaskTagCrossRef pattern
    // (TaskDao owns task_tags access). Junction rows are rebuilt on sync
    // pull from the parent medication's embedded slotCloudIds list.

    @Query("SELECT slot_id FROM medication_medication_slots WHERE medication_id = :medicationId")
    suspend fun getSlotIdsForMedicationOnce(medicationId: Long): List<Long>

    @Query(
        """
        SELECT s.* FROM medication_slots s
        INNER JOIN medication_medication_slots x ON x.slot_id = s.id
        WHERE x.medication_id = :medicationId
        ORDER BY s.sort_order ASC, s.ideal_time ASC, s.id ASC
        """
    )
    suspend fun getSlotsForMedicationOnce(medicationId: Long): List<MedicationSlotEntity>

    @Query(
        """
        SELECT s.* FROM medication_slots s
        INNER JOIN medication_medication_slots x ON x.slot_id = s.id
        WHERE x.medication_id = :medicationId
        ORDER BY s.sort_order ASC, s.ideal_time ASC, s.id ASC
        """
    )
    fun observeSlotsForMedication(medicationId: Long): Flow<List<MedicationSlotEntity>>

    @Query("SELECT medication_id FROM medication_medication_slots WHERE slot_id = :slotId")
    suspend fun getMedicationIdsForSlotOnce(slotId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(crossRef: MedicationSlotCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(crossRefs: List<MedicationSlotCrossRef>)

    @Query("DELETE FROM medication_medication_slots WHERE medication_id = :medicationId AND slot_id = :slotId")
    suspend fun deleteLink(medicationId: Long, slotId: Long)

    @Query("DELETE FROM medication_medication_slots WHERE medication_id = :medicationId")
    suspend fun deleteLinksForMedication(medicationId: Long)
}
