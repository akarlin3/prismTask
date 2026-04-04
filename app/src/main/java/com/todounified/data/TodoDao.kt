package com.todounified.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    // ── Lists ──
    @Query("SELECT * FROM task_lists ORDER BY sortOrder ASC")
    fun getAllLists(): Flow<List<TaskList>>

    @Transaction
    @Query("SELECT * FROM task_lists ORDER BY sortOrder ASC")
    fun getAllListsWithTasks(): Flow<List<TaskListWithTasks>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: TaskList)

    @Update
    suspend fun updateList(list: TaskList)

    @Delete
    suspend fun deleteList(list: TaskList)

    @Query("SELECT COUNT(*) FROM task_lists")
    suspend fun getListCount(): Int

    // ── Tasks ──
    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY createdAt DESC")
    fun getTasksForList(listId: String): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE listId = :listId AND done = 1")
    suspend fun clearDoneTasks(listId: String)

    // ── Imported Tabs ──
    @Query("SELECT * FROM imported_tabs ORDER BY importedAt DESC")
    fun getAllImportedTabs(): Flow<List<ImportedTab>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedTab(tab: ImportedTab)

    @Update
    suspend fun updateImportedTab(tab: ImportedTab)

    @Delete
    suspend fun deleteImportedTab(tab: ImportedTab)
}
