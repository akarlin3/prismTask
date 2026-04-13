package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTemplateDao {

    @Query("SELECT * FROM task_templates ORDER BY usage_count DESC, last_used_at DESC")
    fun getAllTemplates(): Flow<List<TaskTemplateEntity>>

    @Query("SELECT * FROM task_templates ORDER BY usage_count DESC, last_used_at DESC")
    suspend fun getAllTemplatesOnce(): List<TaskTemplateEntity>

    @Query("SELECT COUNT(*) FROM task_templates")
    suspend fun countTemplates(): Int

    @Query("SELECT * FROM task_templates WHERE name = :name LIMIT 1")
    suspend fun getTemplateByName(name: String): TaskTemplateEntity?

    @Query("UPDATE task_templates SET category = NULL, updated_at = :now WHERE category = :category")
    suspend fun clearCategory(category: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM task_templates WHERE category = :category ORDER BY usage_count DESC")
    fun getTemplatesByCategory(category: String): Flow<List<TaskTemplateEntity>>

    @Query("SELECT * FROM task_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): TaskTemplateEntity?

    @Query("SELECT DISTINCT category FROM task_templates WHERE category IS NOT NULL ORDER BY category")
    fun getAllCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TaskTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: TaskTemplateEntity)

    @Query("DELETE FROM task_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("UPDATE task_templates SET usage_count = usage_count + 1, last_used_at = :usedAt WHERE id = :id")
    suspend fun incrementUsage(id: Long, usedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM task_templates WHERE name LIKE '%' || :query || '%' OR template_title LIKE '%' || :query || '%'")
    fun searchTemplates(query: String): Flow<List<TaskTemplateEntity>>

    @Query("DELETE FROM task_templates")
    suspend fun deleteAll()
}
