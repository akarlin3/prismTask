package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

data class ProjectWithCount(
    val id: Long,
    val name: String,
    val color: String,
    val icon: String,
    val createdAt: Long,
    val updatedAt: Long,
    val taskCount: Int
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectById(id: Long): Flow<ProjectEntity?>

    @Query(
        """
        SELECT p.id, p.name, p.color, p.icon,
               p.created_at AS createdAt, p.updated_at AS updatedAt,
               COUNT(t.id) AS taskCount
        FROM projects p
        LEFT JOIN tasks t ON t.project_id = p.id
        GROUP BY p.id
    """
    )
    fun getProjectWithTaskCount(): Flow<List<ProjectWithCount>>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectByIdOnce(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%'")
    fun searchProjects(query: String): Flow<List<ProjectEntity>>

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}
