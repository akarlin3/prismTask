package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.ProjectTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectTemplateDao {
    @Query("SELECT * FROM project_templates ORDER BY is_built_in DESC, usage_count DESC, name ASC")
    fun getAll(): Flow<List<ProjectTemplateEntity>>

    @Query("SELECT * FROM project_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProjectTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: ProjectTemplateEntity): Long

    @Update
    suspend fun update(template: ProjectTemplateEntity)

    @Delete
    suspend fun delete(template: ProjectTemplateEntity)

    @Query("UPDATE project_templates SET usage_count = usage_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM project_templates")
    suspend fun count(): Int

    @Query("DELETE FROM project_templates")
    suspend fun deleteAll()

    @Query("SELECT * FROM project_templates")
    suspend fun getAllOnce(): List<ProjectTemplateEntity>

    @Query("SELECT * FROM project_templates WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): ProjectTemplateEntity?

    @Query("UPDATE project_templates SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)

    @Query("DELETE FROM project_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
