package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [TaskDependencyEntity]. Read patterns: enumerate the blockers
 * of a given task (to render the "waiting on …" surface) and enumerate
 * the tasks that a completed blocker unblocked (to render the
 * "ready now" surface).
 */
@Dao
interface TaskDependencyDao {
    @Query(
        "SELECT * FROM task_dependencies WHERE blocked_task_id = :taskId " +
            "ORDER BY created_at ASC, id ASC"
    )
    fun observeBlockersOf(taskId: Long): Flow<List<TaskDependencyEntity>>

    @Query(
        "SELECT * FROM task_dependencies WHERE blocked_task_id = :taskId " +
            "ORDER BY created_at ASC, id ASC"
    )
    suspend fun getBlockersOfOnce(taskId: Long): List<TaskDependencyEntity>

    @Query(
        "SELECT * FROM task_dependencies WHERE blocker_task_id = :taskId " +
            "ORDER BY created_at ASC, id ASC"
    )
    suspend fun getBlockedByOnce(taskId: Long): List<TaskDependencyEntity>

    /**
     * Snapshot of every dependency edge in the database. Used by
     * [com.averycorp.prismtask.domain.usecase.DependencyCycleGuard] to
     * walk the full graph at write time, and by
     * [com.averycorp.prismtask.data.remote.CloudIdOrphanHealer] to
     * enumerate cloud-id-bearing rows.
     */
    @Query("SELECT * FROM task_dependencies ORDER BY id ASC")
    suspend fun getAllOnce(): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependencies WHERE id = :id")
    suspend fun getByIdOnce(id: Long): TaskDependencyEntity?

    @Query(
        "SELECT id FROM task_dependencies " +
            "WHERE blocker_task_id = :blocker AND blocked_task_id = :blocked LIMIT 1"
    )
    suspend fun findEdgeIdOnce(blocker: Long, blocked: Long): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dependency: TaskDependencyEntity): Long

    @Query("DELETE FROM task_dependencies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM task_dependencies " +
            "WHERE blocker_task_id = :blocker AND blocked_task_id = :blocked"
    )
    suspend fun deleteEdge(blocker: Long, blocked: Long)
}
