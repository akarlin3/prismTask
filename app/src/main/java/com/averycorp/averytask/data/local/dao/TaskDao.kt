package com.averycorp.averytask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.local.entity.TaskWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY due_date ASC, priority DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

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
}
