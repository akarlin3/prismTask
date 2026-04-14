package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.ReminderProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderProfileDao {
    @Query("SELECT * FROM reminder_profiles ORDER BY is_built_in DESC, name ASC")
    fun getAll(): Flow<List<ReminderProfileEntity>>

    @Query("SELECT * FROM reminder_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ReminderProfileEntity): Long

    @Update
    suspend fun update(profile: ReminderProfileEntity)

    @Delete
    suspend fun delete(profile: ReminderProfileEntity)

    @Query("SELECT COUNT(*) FROM reminder_profiles")
    suspend fun count(): Int
}
