package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getTasksByProject(projectId: Long): Flow<List<TaskEntity>> =
        taskDao.getTasksByProject(projectId)

    fun getSubtasks(parentTaskId: Long): Flow<List<TaskEntity>> =
        taskDao.getSubtasks(parentTaskId)

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
        return taskDao.insert(task)
    }

    fun getTaskById(id: Long): Flow<TaskEntity?> = taskDao.getTaskById(id)

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
        return taskDao.insert(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.update(task.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun completeTask(id: Long) {
        val now = System.currentTimeMillis()
        val task = taskDao.getTaskById(id).firstOrNull()
        if (task?.recurrenceRule != null) {
            // TODO: Parse recurrenceRule JSON and create the next occurrence
        }
        taskDao.markCompleted(id, now)
    }

    suspend fun uncompleteTask(id: Long) {
        taskDao.markIncomplete(id, System.currentTimeMillis())
    }

    suspend fun deleteTask(id: Long) {
        taskDao.deleteById(id)
    }

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
