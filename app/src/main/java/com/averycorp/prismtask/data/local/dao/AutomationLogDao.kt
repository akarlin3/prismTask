package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.AutomationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationLogDao {
    @Query("SELECT * FROM automation_logs ORDER BY fired_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AutomationLogEntity>>

    @Query("SELECT * FROM automation_logs WHERE rule_id = :ruleId ORDER BY fired_at DESC LIMIT :limit")
    fun observeForRule(ruleId: Long, limit: Int = 100): Flow<List<AutomationLogEntity>>

    @Query("SELECT COUNT(*) FROM automation_logs WHERE fired_at >= :since")
    suspend fun countSince(since: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM automation_logs
        WHERE fired_at >= :since
          AND actions_executed_json LIKE '%"type":"ai.%'
        """
    )
    suspend fun countAiSince(since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AutomationLogEntity): Long

    @Query("DELETE FROM automation_logs WHERE fired_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM automation_logs WHERE rule_id = :ruleId")
    suspend fun deleteForRule(ruleId: Long)
}
