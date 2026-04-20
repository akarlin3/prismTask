package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.LeisureLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeisureDao {
    @Query("SELECT * FROM leisure_logs WHERE date = :date LIMIT 1")
    fun getLogForDate(date: Long): Flow<LeisureLogEntity?>

    @Query("SELECT * FROM leisure_logs WHERE date = :date LIMIT 1")
    suspend fun getLogForDateOnce(date: Long): LeisureLogEntity?

    @Query("SELECT * FROM leisure_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): LeisureLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LeisureLogEntity): Long

    @Update
    suspend fun updateLog(log: LeisureLogEntity)

    @Query("SELECT * FROM leisure_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<LeisureLogEntity>>

    @Query("SELECT * FROM leisure_logs ORDER BY date DESC")
    suspend fun getAllLogsOnce(): List<LeisureLogEntity>

    @Query("DELETE FROM leisure_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("SELECT COUNT(*) FROM leisure_logs WHERE music_done = 1 AND flex_done = 1")
    fun getCompletedDaysCount(): Flow<Int>
}
