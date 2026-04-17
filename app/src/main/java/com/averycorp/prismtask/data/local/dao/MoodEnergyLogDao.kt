package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MoodEnergyLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [MoodEnergyLogEntity] — the v1.4.0 V7 daily mood/energy check-ins.
 */
@Dao
interface MoodEnergyLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MoodEnergyLogEntity): Long

    @Update
    suspend fun update(log: MoodEnergyLogEntity)

    @Query("SELECT * FROM mood_energy_logs WHERE date = :date ORDER BY time_of_day")
    suspend fun getByDate(date: Long): List<MoodEnergyLogEntity>

    @Query("SELECT * FROM mood_energy_logs WHERE date >= :start AND date <= :end ORDER BY date ASC, time_of_day ASC")
    fun observeRange(start: Long, end: Long): Flow<List<MoodEnergyLogEntity>>

    @Query("SELECT * FROM mood_energy_logs WHERE date >= :start AND date <= :end ORDER BY date ASC, time_of_day ASC")
    suspend fun getRange(start: Long, end: Long): List<MoodEnergyLogEntity>

    @Query("SELECT * FROM mood_energy_logs ORDER BY date DESC")
    suspend fun getAll(): List<MoodEnergyLogEntity>

    @Query("DELETE FROM mood_energy_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM mood_energy_logs")
    suspend fun deleteAll()
}
