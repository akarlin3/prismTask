package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {

    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId ORDER BY date DESC")
    fun getLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId ORDER BY date DESC LIMIT 1")
    suspend fun getLastLog(habitId: Long): HabitLogEntity?

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habit_id = :habitId")
    fun getLogCount(habitId: Long): Flow<Int>

    @Query("SELECT * FROM habit_logs ORDER BY date DESC")
    suspend fun getAllLogsOnce(): List<HabitLogEntity>

    @Insert
    suspend fun insertLog(log: HabitLogEntity): Long

    @Delete
    suspend fun deleteLog(log: HabitLogEntity)

    @Update
    suspend fun updateLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAll()
}
