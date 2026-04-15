package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.NotificationProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Replaces the earlier `ReminderProfileDao`. The backing table name is
 * still `reminder_profiles` for schema continuity; the rename reflects
 * that a "profile" is now a full notification-delivery bundle, not just
 * reminder offsets.
 */
@Dao
interface NotificationProfileDao {
    @Query("SELECT * FROM reminder_profiles ORDER BY is_built_in DESC, name ASC")
    fun getAll(): Flow<List<NotificationProfileEntity>>

    @Query("SELECT * FROM reminder_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NotificationProfileEntity?

    @Query("SELECT * FROM reminder_profiles WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): NotificationProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: NotificationProfileEntity): Long

    @Update
    suspend fun update(profile: NotificationProfileEntity)

    @Delete
    suspend fun delete(profile: NotificationProfileEntity)

    @Query("SELECT COUNT(*) FROM reminder_profiles")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM reminder_profiles WHERE is_built_in = 0")
    suspend fun countUserCreated(): Int
}
