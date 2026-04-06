package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.converter.RecurrenceConverter
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.remote.CalendarSyncService
import com.averykarlin.averytask.data.remote.SyncTracker
import com.averykarlin.averytask.domain.usecase.RecurrenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val syncTracker: SyncTracker,
    private val calendarSyncService: CalendarSyncService
) {
    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getTasksByProject(projectId: Long): Flow<List<TaskEntity>> =
        taskDao.getTasksByProject(projectId)

    fun getSubtasks(parentTaskId: Long): Flow<List<TaskEntity>> =
        taskDao.getSubtasks(parentTaskId)

    suspend fun deleteTasksByProjectId(projectId: Long) =
        taskDao.deleteTasksByProjectId(projectId)

    fun getIncompleteTasks(): Flow<List<TaskEntity>> = taskDao.getIncompleteTasks()

    fun getIncompleteRootTasks(): Flow<List<TaskEntity>> = taskDao.getIncompleteRootTasks()

    fun getTasksDueOnDate(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>> =
        taskDao.getTasksDueOnDate(startOfDay, endOfDay)

    fun getOverdueTasks(now: Long): Flow<List<TaskEntity>> = taskDao.getOverdueTasks(now)

    suspend fun addSubtask(title: String, parentTaskId: Long, priority: Int = 0): Long {
        val now = System.currentTimeMillis()
        val parent = taskDao.getTaskById(parentTaskId).firstOrNull()
        val task = TaskEntity(
            title = title,
            parentTaskId = parentTaskId,
            projectId = parent?.projectId,
            dueDate = parent?.dueDate,
            priority = priority,
            createdAt = now,
            updatedAt = now
        )
        val id = taskDao.insert(task)
        syncTracker.trackCreate(id, "task")
        calendarSyncService.syncTaskToCalendar(task.copy(id = id))
        return id
    }

    suspend fun getAllTasksOnce(): List<TaskEntity> = taskDao.getAllTasksOnce()

    fun getTaskById(id: Long): Flow<TaskEntity?> = taskDao.getTaskById(id)

    suspend fun getTaskByIdOnce(id: Long): TaskEntity? = taskDao.getTaskByIdOnce(id)

    suspend fun insertTask(task: TaskEntity): Long {
        val id = taskDao.insert(task)
        syncTracker.trackCreate(id, "task")
        calendarSyncService.syncTaskToCalendar(task.copy(id = id))
        return id
    }

    suspend fun addTask(
        title: String,
        description: String? = null,
        dueDate: Long? = null,
        dueTime: Long? = null,
        priority: Int = 0,
        projectId: Long? = null,
        parentTaskId: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            title = title,
            description = description,
            dueDate = dueDate,
            dueTime = dueTime,
            priority = priority,
            projectId = projectId,
            parentTaskId = parentTaskId,
            createdAt = now,
            updatedAt = now
        )
        val id = taskDao.insert(task)
        syncTracker.trackCreate(id, "task")
        calendarSyncService.syncTaskToCalendar(task.copy(id = id))
        return id
    }

    suspend fun updateTask(task: TaskEntity) {
        val updated = task.copy(updatedAt = System.currentTimeMillis())
        taskDao.update(updated)
        syncTracker.trackUpdate(task.id, "task")
        calendarSyncService.syncTaskToCalendar(updated)
    }

    suspend fun completeTask(id: Long) {
        val now = System.currentTimeMillis()
        val task = taskDao.getTaskById(id).firstOrNull()
        if (task?.recurrenceRule != null && task.dueDate != null) {
            val rule = RecurrenceConverter.fromJson(task.recurrenceRule)
            if (rule != null) {
                val nextDueDate = RecurrenceEngine.calculateNextDueDate(task.dueDate, rule)
                if (nextDueDate != null) {
                    val updatedRule = rule.copy(occurrenceCount = rule.occurrenceCount + 1)
                    val nextTask = task.copy(
                        id = 0,
                        isCompleted = false,
                        dueDate = nextDueDate,
                        recurrenceRule = RecurrenceConverter.toJson(updatedRule),
                        completedAt = null,
                        createdAt = now,
                        updatedAt = now
                    )
                    val nextId = taskDao.insert(nextTask)
                    syncTracker.trackCreate(nextId, "task")
                    calendarSyncService.syncTaskToCalendar(nextTask.copy(id = nextId))
                }
            }
        }
        taskDao.markCompleted(id, now)
        syncTracker.trackUpdate(id, "task")
        calendarSyncService.removeEventForTask(id)
    }

    suspend fun uncompleteTask(id: Long) {
        taskDao.markIncomplete(id, System.currentTimeMillis())
        syncTracker.trackUpdate(id, "task")
        val task = taskDao.getTaskByIdOnce(id)
        if (task != null) calendarSyncService.syncTaskToCalendar(task)
    }

    suspend fun deleteTask(id: Long) {
        calendarSyncService.removeEventForTask(id)
        syncTracker.trackDelete(id, "task")
        taskDao.deleteById(id)
    }

    fun searchTasks(query: String): Flow<List<TaskEntity>> = taskDao.searchTasks(query)

    fun getArchivedTasks(): Flow<List<TaskEntity>> = taskDao.getArchivedTasks()

    suspend fun archiveTask(id: Long) {
        taskDao.archiveTask(id, System.currentTimeMillis())
        syncTracker.trackUpdate(id, "task")
    }

    suspend fun unarchiveTask(id: Long) {
        taskDao.unarchiveTask(id, System.currentTimeMillis())
        syncTracker.trackUpdate(id, "task")
    }

    suspend fun permanentlyDeleteTask(id: Long) {
        calendarSyncService.removeEventForTask(id)
        syncTracker.trackDelete(id, "task")
        taskDao.permanentlyDelete(id)
    }

    fun searchArchivedTasks(query: String): Flow<List<TaskEntity>> =
        taskDao.searchArchivedTasks(query)

    suspend fun autoArchiveOldCompleted(daysOld: Int) {
        val cutoff = System.currentTimeMillis() - (daysOld.toLong() * 24 * 60 * 60 * 1000)
        taskDao.archiveCompletedBefore(cutoff, System.currentTimeMillis())
    }

    fun getArchivedCount(): Flow<Int> = taskDao.getArchivedCount()

    fun getTasksGroupedByDate(): Flow<Map<String, List<TaskEntity>>> =
        taskDao.getIncompleteRootTasks().map { tasks -> groupByDate(tasks) }

    private fun groupByDate(tasks: List<TaskEntity>): Map<String, List<TaskEntity>> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startOfTomorrow = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startOfDayAfterTomorrow = calendar.timeInMillis

        calendar.timeInMillis = startOfToday
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val endOfThisWeek = calendar.timeInMillis

        val grouped = linkedMapOf<String, MutableList<TaskEntity>>()

        for (task in tasks) {
            val bucket = when {
                task.dueDate == null -> "Later"
                task.dueDate < startOfToday -> "Overdue"
                task.dueDate < startOfTomorrow -> "Today"
                task.dueDate < startOfDayAfterTomorrow -> "Tomorrow"
                task.dueDate < endOfThisWeek -> "This Week"
                else -> "Later"
            }
            grouped.getOrPut(bucket) { mutableListOf() }.add(task)
        }

        val order = listOf("Overdue", "Today", "Tomorrow", "This Week", "Later")
        return order.filter { it in grouped }.associateWith { grouped[it]!! }
    }
}
