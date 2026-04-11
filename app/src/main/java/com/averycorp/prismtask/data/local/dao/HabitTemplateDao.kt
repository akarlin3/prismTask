package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.HabitTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitTemplateDao {

    @Query("SELECT * FROM habit_templates ORDER BY is_built_in DESC, usage_count DESC, name ASC")
    fun getAll(): Flow<List<HabitTemplateEntity>>

    @Query("SELECT * FROM habit_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HabitTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: HabitTemplateEntity): Long

    @Update
    suspend fun update(template: HabitTemplateEntity)

    @Delete
    suspend fun delete(template: HabitTemplateEntity)

    @Query("UPDATE habit_templates SET usage_count = usage_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM habit_templates")
    suspend fun count(): Int
}
