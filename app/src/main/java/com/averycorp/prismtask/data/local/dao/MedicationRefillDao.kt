package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MedicationRefillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationRefillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(refill: MedicationRefillEntity): Long

    @Update
    suspend fun update(refill: MedicationRefillEntity)

    @Query("SELECT * FROM medication_refills ORDER BY medication_name ASC")
    fun observeAll(): Flow<List<MedicationRefillEntity>>

    @Query("SELECT * FROM medication_refills WHERE medication_name = :name LIMIT 1")
    suspend fun getByName(name: String): MedicationRefillEntity?

    @Query("SELECT * FROM medication_refills")
    suspend fun getAll(): List<MedicationRefillEntity>

    @Query("DELETE FROM medication_refills WHERE id = :id")
    suspend fun delete(id: Long)
}
