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

    @Query("SELECT * FROM check_in_logs ORDER BY date DESC")
    suspend fun getAllOnce(): List<CheckInLogEntity>

    @Query("DELETE FROM check_in_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM check_in_logs WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: Long): CheckInLogEntity?

    @Query("SELECT * FROM check_in_logs WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): CheckInLogEntity?

    @Query("UPDATE check_in_logs SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)

    @Query("DELETE FROM check_in_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
