package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY due_date ASC, priority DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY sort_order ASC, id ASC")
    fun getAllTasksByCustomOrder(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE project_id = :projectId")
    fun getTasksByProject(projectId: Long): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE project_id = :projectId")
    suspend fun deleteTasksByProjectId(projectId: Long)

    @Query("SELECT * FROM tasks WHERE parent_task_id = :parentTaskId ORDER BY sort_order ASC, created_at ASC")
    fun getSubtasks(parentTaskId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parent_task_id = :parentTaskId ORDER BY sort_order ASC, created_at ASC")
    suspend fun getSubtasksOnce(parentTaskId: Long): List<TaskEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM tasks WHERE parent_task_id = :parentTaskId")
    suspend fun getMaxSubtaskSortOrder(parentTaskId: Long): Int

    @Query("UPDATE tasks SET sort_order = :sortOrder, updated_at = :now WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET planned_date = :plannedDate, sort_order = :sortOrder, updated_at = :now WHERE id = :id")
    suspend fun updatePlannedDateAndSortOrder(id: Long, plannedDate: Long, sortOrder: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM tasks WHERE parent_task_id IS NULL")
    suspend fun getMaxRootSortOrder(): Int

    @Transaction
    suspend fun updateSortOrders(taskOrders: List<Pair<Long, Int>>) {
        val now = System.currentTimeMillis()
        taskOrders.forEach { (id, order) ->
            updateSortOrder(id, order, now)
        }
    }

    @Query("SELECT * FROM tasks WHERE is_completed = 0")
    fun getIncompleteTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND parent_task_id IS NULL ORDER BY due_date ASC, priority DESC")
    fun getIncompleteRootTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE due_date >= :startOfDay AND due_date < :endOfDay")
    fun getTasksDueOnDate(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE due_date < :now AND is_completed = 0")
    fun getOverdueTasks(now: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskById(id: Long): Flow<TaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskByIdOnce(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND reminder_offset IS NOT NULL AND due_date IS NOT NULL")
    suspend fun getIncompleteTasksWithReminders(): List<TaskEntity>

    @Query("UPDATE tasks SET is_completed = 1, completed_at = :completedAt, updated_at = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long)

    @Query("UPDATE tasks SET is_completed = 0, completed_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun markIncomplete(id: Long, now: Long = System.currentTimeMillis())

    @Transaction
    @Query("SELECT * FROM tasks")
    fun getTasksWithTags(): Flow<List<TaskWithTags>>

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY updated_at DESC")
    fun searchTasks(query: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE archived_at IS NOT NULL ORDER BY archived_at DESC")
    fun getArchivedTasks(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET archived_at = :archivedAt, updated_at = :archivedAt WHERE id = :id")
    suspend fun archiveTask(id: Long, archivedAt: Long)

    @Query("UPDATE tasks SET archived_at = NULL, updated_at = :updatedAt WHERE id = :id")
    suspend fun unarchiveTask(id: Long, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun permanentlyDelete(id: Long)

    @Query("UPDATE tasks SET archived_at = :now WHERE is_completed = 1 AND completed_at < :cutoffDate AND archived_at IS NULL")
    suspend fun archiveCompletedBefore(cutoffDate: Long, now: Long)

    @Query("SELECT COUNT(*) FROM tasks WHERE archived_at IS NOT NULL")
    fun getArchivedCount(): Flow<Int>

    @Query("SELECT * FROM tasks WHERE archived_at IS NOT NULL AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')")
    fun searchArchivedTasks(query: String): Flow<List<TaskEntity>>

    // Today screen queries
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date < :startOfToday AND archived_at IS NULL AND parent_task_id IS NULL ORDER BY priority DESC")
    fun getOverdueRootTasks(startOfToday: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date >= :startOfToday AND due_date < :endOfToday AND archived_at IS NULL AND parent_task_id IS NULL ORDER BY priority DESC")
    fun getTodayTasks(startOfToday: Long, endOfToday: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND planned_date >= :startOfToday AND planned_date < :endOfToday AND (due_date IS NULL OR due_date >= :endOfToday OR due_date < :startOfToday) AND archived_at IS NULL AND parent_task_id IS NULL ORDER BY priority DESC")
    fun getPlannedForToday(startOfToday: Long, endOfToday: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 1 AND completed_at >= :startOfToday AND archived_at IS NULL AND parent_task_id IS NULL ORDER BY completed_at DESC")
    fun getCompletedToday(startOfToday: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date < :startOfToday AND archived_at IS NULL AND parent_task_id IS NULL ORDER BY priority DESC")
    suspend fun getOverdueRootTasksOnce(startOfToday: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date >= :startOfToday AND due_date < :endOfToday AND archived_at IS NULL AND parent_task_id IS NULL ORDER BY priority DESC")
    suspend fun getTodayTasksOnce(startOfToday: Long, endOfToday: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE is_completed = 1 AND completed_at >= :startOfToday AND archived_at IS NULL AND parent_task_id IS NULL ORDER BY completed_at DESC")
    suspend fun getCompletedTodayOnce(startOfToday: Long): List<TaskEntity>

    @Query("UPDATE tasks SET planned_date = :plannedDate, updated_at = :now WHERE id = :id")
    suspend fun setPlanDate(id: Long, plannedDate: Long?, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND archived_at IS NULL AND parent_task_id IS NULL AND (due_date IS NULL OR due_date >= :endOfToday) AND (planned_date IS NULL OR planned_date < :startOfToday OR planned_date >= :endOfToday) ORDER BY due_date ASC, priority DESC")
    fun getTasksNotInToday(startOfToday: Long, endOfToday: Long): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET planned_date = NULL, updated_at = :now WHERE planned_date < :startOfToday AND is_completed = 0")
    suspend fun clearExpiredPlans(startOfToday: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET due_date = :newDate, updated_at = :now WHERE id = :id")
    suspend fun updateDueDate(id: Long, newDate: Long?, now: Long = System.currentTimeMillis())

    // Tasks booked against a habit whose due_date or planned_date falls in the given range.
    @Query("SELECT * FROM tasks WHERE source_habit_id = :habitId AND archived_at IS NULL AND ((due_date IS NOT NULL AND due_date >= :startDate AND due_date <= :endDate) OR (planned_date IS NOT NULL AND planned_date >= :startDate AND planned_date <= :endDate))")
    suspend fun getTasksForHabitInRangeOnce(habitId: Long, startDate: Long, endDate: Long): List<TaskEntity>

    // --- Eisenhower quadrant ---

    @Query("UPDATE tasks SET eisenhower_quadrant = :quadrant, eisenhower_updated_at = :updatedAt, eisenhower_reason = :reason, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateEisenhowerQuadrant(id: Long, quadrant: String?, reason: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND archived_at IS NULL AND parent_task_id IS NULL AND eisenhower_quadrant IS NOT NULL ORDER BY priority DESC")
    fun getCategorizedTasks(): Flow<List<TaskEntity>>

    // --- Batch edit operations (multi-select bulk editing) ---

    @Query("UPDATE tasks SET priority = :priority, updated_at = :now WHERE id IN (:taskIds)")
    suspend fun batchUpdatePriorityQuery(taskIds: List<Long>, priority: Int, now: Long)

    @Query("UPDATE tasks SET due_date = :newDueDate, updated_at = :now WHERE id IN (:taskIds)")
    suspend fun batchUpdateDueDateQuery(taskIds: List<Long>, newDueDate: Long?, now: Long)

    @Query("UPDATE tasks SET project_id = :newProjectId, updated_at = :now WHERE id IN (:taskIds)")
    suspend fun batchUpdateProjectQuery(taskIds: List<Long>, newProjectId: Long?, now: Long)

    @Query("UPDATE tasks SET updated_at = :now WHERE id IN (:taskIds)")
    suspend fun batchTouchTasksQuery(taskIds: List<Long>, now: Long)

    @Query("INSERT OR REPLACE INTO task_tags (taskId, tagId) SELECT id, :tagId FROM tasks WHERE id IN (:taskIds)")
    suspend fun batchInsertTaskTagsQuery(taskIds: List<Long>, tagId: Long)

    @Query("DELETE FROM task_tags WHERE taskId IN (:taskIds) AND tagId = :tagId")
    suspend fun batchDeleteTaskTagsQuery(taskIds: List<Long>, tagId: Long)

    /**
     * Atomically updates priority on every task in [taskIds]. Wraps a single
     * UPDATE statement in a transaction so all touched tasks land in the same
     * consistent state — if the statement fails, no task is changed.
     */
    @Transaction
    suspend fun batchUpdatePriority(taskIds: List<Long>, priority: Int) {
        if (taskIds.isEmpty()) return
        batchUpdatePriorityQuery(taskIds, priority, System.currentTimeMillis())
    }

    /**
     * Atomically rescheduling: sets [newDueDate] on every task in [taskIds].
     * Passing null clears the due date.
     */
    @Transaction
    suspend fun batchReschedule(taskIds: List<Long>, newDueDate: Long?) {
        if (taskIds.isEmpty()) return
        batchUpdateDueDateQuery(taskIds, newDueDate, System.currentTimeMillis())
    }

    /**
     * Atomically moves every task in [taskIds] into [newProjectId]. Passing
     * null removes the task from its current project.
     */
    @Transaction
    suspend fun batchMoveToProject(taskIds: List<Long>, newProjectId: Long?) {
        if (taskIds.isEmpty()) return
        batchUpdateProjectQuery(taskIds, newProjectId, System.currentTimeMillis())
    }

    /**
     * Atomically adds [tagId] to every task in [taskIds]. Uses INSERT OR
     * REPLACE so re-adding an existing tag is a no-op. Also bumps updated_at
     * on the tasks so sync tracking picks up the change.
     */
    @Transaction
    suspend fun batchAddTag(taskIds: List<Long>, tagId: Long) {
        if (taskIds.isEmpty()) return
        val now = System.currentTimeMillis()
        batchInsertTaskTagsQuery(taskIds, tagId)
        batchTouchTasksQuery(taskIds, now)
    }

    /**
     * Atomically removes [tagId] from every task in [taskIds]. Tasks that
     * don't have the tag are silently skipped.
     */
    @Transaction
    suspend fun batchRemoveTag(taskIds: List<Long>, tagId: Long) {
        if (taskIds.isEmpty()) return
        val now = System.currentTimeMillis()
        batchDeleteTaskTagsQuery(taskIds, tagId)
        batchTouchTasksQuery(taskIds, now)
    }

    // --- Anti-shame notification queries ---

    @Query("SELECT * FROM tasks WHERE is_completed = 1 AND updated_at >= :startOfDay AND updated_at < :endOfDay AND parent_task_id IS NULL ORDER BY updated_at DESC")
    suspend fun getCompletedTasksInRange(startOfDay: Long, endOfDay: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND archived_at IS NULL AND parent_task_id IS NULL AND due_date IS NOT NULL AND due_date < :endOfDay")
    suspend fun getIncompleteTodayCount(endOfDay: Long): Int

    @Query("SELECT * FROM tasks WHERE is_completed = 1 AND parent_task_id IS NULL ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLastCompletedTask(): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND archived_at IS NULL AND parent_task_id IS NULL")
    suspend fun getIncompleteTaskCount(): Int
}
