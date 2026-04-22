package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.DailyEssentialSlotCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEssentialSlotCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DailyEssentialSlotCompletionEntity): Long

    @Query(
        "SELECT * FROM daily_essential_slot_completions " +
            "WHERE date = :date ORDER BY slot_key ASC"
    )
    fun observeForDate(date: Long): Flow<List<DailyEssentialSlotCompletionEntity>>

    @Query(
        "SELECT * FROM daily_essential_slot_completions " +
            "WHERE date = :date ORDER BY slot_key ASC"
    )
    suspend fun getForDateOnce(date: Long): List<DailyEssentialSlotCompletionEntity>

    @Query(
        "SELECT * FROM daily_essential_slot_completions " +
            "WHERE date = :date AND slot_key = :slotKey LIMIT 1"
    )
    suspend fun getBySlotOnce(
        date: Long,
        slotKey: String
    ): DailyEssentialSlotCompletionEntity?

    @Query(
        "SELECT * FROM daily_essential_slot_completions " +
            "WHERE updated_at > :since ORDER BY updated_at ASC"
    )
    suspend fun getChangedSince(since: Long): List<DailyEssentialSlotCompletionEntity>

    @Query("SELECT * FROM daily_essential_slot_completions")
    suspend fun getAllOnce(): List<DailyEssentialSlotCompletionEntity>

    @Query("DELETE FROM daily_essential_slot_completions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM daily_essential_slot_completions WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: Long): DailyEssentialSlotCompletionEntity?

    @Query("SELECT * FROM daily_essential_slot_completions WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): DailyEssentialSlotCompletionEntity?

    @Query("UPDATE daily_essential_slot_completions SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)
}
