package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskDependencyDao
import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.dependency.DependencyCycleGuard
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists "X blocks Y" edges and resolves blocked-state queries used
 * by the streak / Today filters. Cycle prevention runs at the write
 * site via [DependencyCycleGuard]; the unique pair index on
 * `task_dependencies` plus `OnConflictStrategy.IGNORE` in the DAO
 * means duplicate-edge inserts are no-ops rather than errors.
 */
@Singleton
class TaskDependencyRepository
@Inject
constructor(
    private val dependencyDao: TaskDependencyDao,
    private val taskDao: TaskDao,
    private val syncTracker: SyncTracker
) {
    /**
     * Result of attempting to add a dependency edge. [Cycle] is the
     * write-site rejection — the UI displays a "would create a cycle"
     * snackbar rather than letting the DB or the streak engine see
     * a corrupt graph.
     */
    sealed class AddResult {
        data class Ok(val id: Long) : AddResult()
        object Cycle : AddResult()
        object SelfEdge : AddResult()
    }

    suspend fun addDependency(blockerId: Long, blockedId: Long): AddResult {
        if (blockerId == blockedId) return AddResult.SelfEdge
        val cycle = DependencyCycleGuard.wouldCreateCycle(
            blockerId = blockerId,
            blockedId = blockedId,
            getBlockerIds = dependencyDao::getBlockerIds
        )
        if (cycle) return AddResult.Cycle
        val edge = TaskDependencyEntity(
            blockerTaskId = blockerId,
            blockedTaskId = blockedId,
            createdAt = System.currentTimeMillis()
        )
        val id = dependencyDao.insert(edge)
        if (id > 0) syncTracker.trackCreate(id, ENTITY_TYPE)
        return AddResult.Ok(id)
    }

    suspend fun removeDependency(id: Long) {
        syncTracker.trackDelete(id, ENTITY_TYPE)
        dependencyDao.deleteById(id)
    }

    suspend fun getBlockerIds(taskId: Long): List<Long> = dependencyDao.getBlockerIds(taskId)

    suspend fun getBlockedIds(taskId: Long): List<Long> = dependencyDao.getBlockedIds(taskId)

    /**
     * Returns true iff [taskId] is blocked: at least one of its
     * blockers is not yet completed. Used by Today / streak filters
     * to drop the task into the [com.averycorp.prismtask.domain.model.TaskState.BlockedByDependency]
     * pool (audit § P6).
     */
    suspend fun isBlocked(taskId: Long): Boolean {
        val blockerIds = dependencyDao.getBlockerIds(taskId)
        if (blockerIds.isEmpty()) return false
        for (blockerId in blockerIds) {
            val blocker = taskDao.getTaskByIdOnce(blockerId) ?: continue
            if (!blocker.isCompleted) return true
        }
        return false
    }

    companion object {
        const val ENTITY_TYPE = "task_dependency"
    }
}
