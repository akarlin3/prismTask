package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    fun getActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY sort_order ASC, name ASC")
    fun getAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeById(id: Long): Flow<MedicationEntity?>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getByIdOnce(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE name = :name LIMIT 1")
    suspend fun getByNameOnce(name: String): MedicationEntity?

    @Query("SELECT * FROM medications WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    suspend fun getActiveOnce(): List<MedicationEntity>

    @Query("SELECT * FROM medications ORDER BY sort_order ASC, name ASC")
    suspend fun getAllOnce(): List<MedicationEntity>

    /**
     * Active medications whose per-medication `reminder_mode` override is
     * explicitly "INTERVAL". Companion to [MedicationSlotDao.getIntervalModeSlotsOnce]
     * for the reactive rescheduler.
     */
    @Query(
        "SELECT * FROM medications WHERE is_archived = 0 AND reminder_mode = 'INTERVAL' " +
            "ORDER BY sort_order ASC, name ASC"
    )
    suspend fun getIntervalModeMedicationsOnce(): List<MedicationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("UPDATE medications SET is_archived = 1, updated_at = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long)

    @Delete
    suspend fun delete(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM medications WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): MedicationEntity?

    @Query("UPDATE medications SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)
}
