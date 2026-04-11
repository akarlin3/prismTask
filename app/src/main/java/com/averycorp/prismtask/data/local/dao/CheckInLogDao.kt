package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.CheckInLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: CheckInLogEntity): Long

    @Query("SELECT * FROM check_in_logs WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: Long): CheckInLogEntity?

    @Query("SELECT * FROM check_in_logs ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecent(): CheckInLogEntity?

    @Query("SELECT * FROM check_in_logs ORDER BY date DESC")
    fun observeAll(): Flow<List<CheckInLogEntity>>

    @Query("SELECT * FROM check_in_logs WHERE date >= :start ORDER BY date DESC")
    suspend fun getSince(start: Long): List<CheckInLogEntity>
}
