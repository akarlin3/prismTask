package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.MilestoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones WHERE project_id = :projectId ORDER BY order_index ASC, id ASC")
    fun observeMilestones(projectId: Long): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE project_id = :projectId ORDER BY order_index ASC, id ASC")
    suspend fun getMilestonesOnce(projectId: Long): List<MilestoneEntity>

    /**
     * Unfiltered snapshot across all projects. Used by
     * [com.averycorp.prismtask.data.remote.CloudIdOrphanHealer] for the
     * post-sync orphan scan — the healer needs to enumerate every local
     * row with a cloud_id without pre-partitioning by parent project.
     */
    @Query("SELECT * FROM milestones ORDER BY project_id ASC, order_index ASC, id ASC")
    suspend fun getAllMilestonesOnce(): List<MilestoneEntity>

    @Query("SELECT * FROM milestones WHERE id = :id")
    suspend fun getByIdOnce(id: Long): MilestoneEntity?

    @Query("SELECT completed_at FROM milestones WHERE project_id = :projectId AND completed_at IS NOT NULL")
    suspend fun getCompletedTimestamps(projectId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM milestones WHERE project_id = :projectId")
    suspend fun getMaxOrderIndex(projectId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(milestone: MilestoneEntity): Long

    @Update
    suspend fun update(milestone: MilestoneEntity)

    @Update
    suspend fun updateAll(milestones: List<MilestoneEntity>)

    @Delete
    suspend fun delete(milestone: MilestoneEntity)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM milestones WHERE project_id = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
