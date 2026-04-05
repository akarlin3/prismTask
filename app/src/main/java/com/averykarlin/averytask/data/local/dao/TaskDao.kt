package com.averykarlin.averytask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averykarlin.averytask.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY due_date ASC, priority DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE project_id = :projectId")
    fun getTasksByProject(projectId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parent_task_id = :parentTaskId ORDER BY created_at ASC")
    fun getSubtasks(parentTaskId: Long): Flow<List<TaskEntity>>

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

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND reminder_offset IS NOT NULL AND due_date IS NOT NULL")
    suspend fun getIncompleteTasksWithReminders(): List<TaskEntity>

    @Query("UPDATE tasks SET is_completed = 1, completed_at = :completedAt, updated_at = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long)

    @Query("UPDATE tasks SET is_completed = 0, completed_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun markIncomplete(id: Long, now: Long = System.currentTimeMillis())
}
