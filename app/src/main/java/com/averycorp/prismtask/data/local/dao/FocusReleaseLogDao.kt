package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.FocusReleaseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusReleaseLogDao {
    @Insert
    suspend fun insert(log: FocusReleaseLogEntity): Long

    @Query("SELECT * FROM focus_release_logs ORDER BY created_at DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<FocusReleaseLogEntity>>

    @Query("SELECT COUNT(*) FROM focus_release_logs WHERE event_type = :eventType")
    suspend fun countByEventType(eventType: String): Int

    @Query("DELETE FROM focus_release_logs WHERE created_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
