package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ProjectPhaseEntity]. Mirrors the [MilestoneDao] shape — same
 * insert/update/delete semantics + a few query variants for sync, the
 * orphan healer, and order-index management.
 */
@Dao
interface ProjectPhaseDao {
    @Query("SELECT * FROM project_phases WHERE project_id = :projectId ORDER BY order_index ASC, id ASC")
    fun observePhases(projectId: Long): Flow<List<ProjectPhaseEntity>>

    @Query("SELECT * FROM project_phases WHERE project_id = :projectId ORDER BY order_index ASC, id ASC")
    suspend fun getPhasesOnce(projectId: Long): List<ProjectPhaseEntity>

    /**
     * Unfiltered snapshot across all projects. Used by
     * [com.averycorp.prismtask.data.remote.CloudIdOrphanHealer] to
     * enumerate every phase row carrying a cloud_id without partitioning
     * by parent project (mirrors [MilestoneDao.getAllMilestonesOnce]).
     */
    @Query("SELECT * FROM project_phases ORDER BY project_id ASC, order_index ASC, id ASC")
    suspend fun getAllPhasesOnce(): List<ProjectPhaseEntity>

    @Query("SELECT * FROM project_phases WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ProjectPhaseEntity?

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM project_phases WHERE project_id = :projectId")
    suspend fun getMaxOrderIndex(projectId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(phase: ProjectPhaseEntity): Long

    @Update
    suspend fun update(phase: ProjectPhaseEntity)

    @Update
    suspend fun updateAll(phases: List<ProjectPhaseEntity>)

    @Delete
    suspend fun delete(phase: ProjectPhaseEntity)

    @Query("DELETE FROM project_phases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM project_phases WHERE project_id = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
