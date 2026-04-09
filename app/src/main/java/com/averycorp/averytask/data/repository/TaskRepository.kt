package com.averycorp.averytask.data.repository

import com.averycorp.averytask.data.local.converter.RecurrenceConverter
import com.averycorp.averytask.data.local.dao.TagDao
import com.averycorp.averytask.data.local.dao.TaskDao
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.local.entity.TaskTagCrossRef
import com.averycorp.averytask.data.remote.CalendarSyncService
import com.averycorp.averytask.data.remote.SyncTracker
import com.averycorp.averytask.domain.usecase.RecurrenceEngine
import com.averycorp.averytask.notifications.ReminderScheduler
import com.averycorp.averytask.util.DayBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val tagDao: TagDao,
    private val syncTracker: SyncTracker,
    private val calendarSyncService: CalendarSyncService,
    private val reminderScheduler: ReminderScheduler
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
        val nextSortOrder = taskDao.getMaxSubtaskSortOrder(parentTaskId) + 1
        val task = TaskEntity(
            title = title,
            parentTaskId = parentTaskId,
            projectId = parent?.projectId,
            dueDate = parent?.dueDate,
            priority = priority,
            sortOrder = nextSortOrder,
            createdAt = now,
            updatedAt = now
        )
        val id = taskDao.insert(task)
        syncTracker.trackCreate(id, "task")
        calendarSyncService.syncTaskToCalendar(task.copy(id = id))
        return id
    }

    suspend fun reorderSubtasks(parentTaskId: Long, orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            taskDao.updateSortOrder(id, index)
            syncTracker.trackUpdate(id, "task")
        }
    }

    /**
     * Batch-update the sort order for a set of root tasks in a single Room
     * transaction. Used by the drag-to-reorder flow on the task list: after a
     * drop, the UI passes in `(taskId, newSortOrder)` pairs for every task
     * whose position changed within the visible (filtered/grouped) slice.
     */
    suspend fun updateTaskOrder(taskOrders: List<Pair<Long, Int>>) {
        if (taskOrders.isEmpty()) return
        taskDao.updateSortOrders(taskOrders)
        taskOrders.forEach { (id, _) ->
            syncTracker.trackUpdate(id, "task")
        }
    }

    /**
     * Returns the next sort order for a newly-created root task so that new
     * tasks land at the end of a custom-ordered list instead of clobbering an
     * existing position.
     */
    suspend fun getNextRootSortOrder(): Int = taskDao.getMaxRootSortOrder() + 1

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
        // New root tasks go to the end of any custom-ordered list. Subtasks
        // are still handled by addSubtask() which uses its own per-parent
        // max-lookup.
        val nextSortOrder =
            if (parentTaskId == null) taskDao.getMaxRootSortOrder() + 1 else 0
        val task = TaskEntity(
            title = title,
            description = description,
            dueDate = dueDate,
            dueTime = dueTime,
            priority = priority,
            projectId = projectId,
            parentTaskId = parentTaskId,
            sortOrder = nextSortOrder,
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

    /**
     * Changes a task's due date without touching any other field. Used by the
     * quick-reschedule popup. Also cancels/reschedules any existing reminder
     * so it tracks the new due date, and pushes the change to Google Calendar
     * via [CalendarSyncService] which handles the event mapping internally.
     */
    suspend fun rescheduleTask(taskId: Long, newDueDate: Long?) {
        val task = taskDao.getTaskByIdOnce(taskId) ?: return
        val updated = task.copy(
            dueDate = newDueDate,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.update(updated)
        syncTracker.trackUpdate(taskId, "task")

        // Reschedule (or cancel) the reminder to track the new due date. Any
        // previously scheduled alarm with the same requestCode is overwritten
        // by PendingIntent.FLAG_UPDATE_CURRENT in the scheduler.
        val offset = updated.reminderOffset
        if (offset != null && newDueDate != null) {
            reminderScheduler.scheduleReminder(
                taskId = updated.id,
                taskTitle = updated.title,
                taskDescription = updated.description,
                dueDate = newDueDate,
                reminderOffset = offset
            )
        } else {
            reminderScheduler.cancelReminder(taskId)
        }

        // CalendarSyncService handles both create/update/remove based on
        // whether dueDate is null and whether a mapping already exists.
        calendarSyncService.syncTaskToCalendar(updated)
    }

    /**
     * Pins a task to today's dashboard without changing its dueDate. Mirrors
     * the existing [TaskDao.setPlanDate] shortcut used by TodayViewModel.
     */
    suspend fun planTaskForToday(taskId: Long) {
        val startOfToday = DayBoundary.startOfCurrentDay(0)
        taskDao.setPlanDate(taskId, startOfToday)
        syncTracker.trackUpdate(taskId, "task")
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

    /**
     * Duplicates an existing task. Creates a copy that shares the original's
     * content (title prefixed with "Copy of ", description, notes, priority,
     * project, recurrence rule, estimated duration, and tag assignments) but
     * resets all scheduling / completion / sync state so the duplicate starts
     * life as a fresh, unplanned, incomplete task at the end of the list.
     *
     * When [includeSubtasks] is true, every subtask of the original is copied
     * too, with its completion status reset and a fresh parent pointer to the
     * new root task. Subtask titles are copied as-is (no "Copy of " prefix).
     *
     * @return the id of the newly created task, or -1 if the original doesn't
     *   exist.
     */
    suspend fun duplicateTask(taskId: Long, includeSubtasks: Boolean = false): Long {
        val original = taskDao.getTaskByIdOnce(taskId) ?: return -1L
        val now = System.currentTimeMillis()

        val nextSortOrder = if (original.parentTaskId != null) {
            taskDao.getMaxSubtaskSortOrder(original.parentTaskId) + 1
        } else {
            taskDao.getMaxRootSortOrder() + 1
        }

        val duplicate = buildDuplicateEntity(original, nextSortOrder, now)
        val newId = taskDao.insert(duplicate)
        syncTracker.trackCreate(newId, "task")

        // Copy tag cross-references so the duplicate inherits the same tags.
        val tagIds = tagDao.getTagIdsForTaskOnce(taskId)
        for (crossRef in buildTagCrossRefs(tagIds, newId)) {
            tagDao.addTagToTask(crossRef)
        }

        if (includeSubtasks) {
            val originalSubtasks = taskDao.getSubtasksOnce(taskId)
            originalSubtasks.forEachIndexed { index, sub ->
                val subCopy = buildSubtaskDuplicate(sub, newId, index, now)
                val newSubId = taskDao.insert(subCopy)
                syncTracker.trackCreate(newSubId, "task")
            }
        }

        calendarSyncService.syncTaskToCalendar(duplicate.copy(id = newId))
        return newId
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

    companion object {
        /**
         * Pure transformation: build a fresh [TaskEntity] that duplicates the
         * content of [original] with a "Copy of " title prefix and all
         * scheduling / completion / sync state reset. Does not touch the
         * database — callers are responsible for inserting the result.
         */
        fun buildDuplicateEntity(
            original: TaskEntity,
            nextSortOrder: Int,
            now: Long
        ): TaskEntity {
            return original.copy(
                id = 0,
                title = "Copy of ${original.title}",
                dueDate = null,
                dueTime = null,
                plannedDate = null,
                isCompleted = false,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
                reminderOffset = null,
                archivedAt = null,
                scheduledStartTime = null,
                sortOrder = nextSortOrder
            )
        }

        /**
         * Pure transformation: build a duplicate subtask that points at
         * [newParentId] with a fresh completion / scheduling state. Subtask
         * titles are copied verbatim (no "Copy of " prefix) since the parent
         * already carries the duplication marker.
         */
        fun buildSubtaskDuplicate(
            originalSubtask: TaskEntity,
            newParentId: Long,
            sortOrder: Int,
            now: Long
        ): TaskEntity {
            return originalSubtask.copy(
                id = 0,
                parentTaskId = newParentId,
                dueDate = null,
                dueTime = null,
                plannedDate = null,
                isCompleted = false,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
                reminderOffset = null,
                archivedAt = null,
                scheduledStartTime = null,
                sortOrder = sortOrder
            )
        }

        /**
         * Pure transformation: produce the list of [TaskTagCrossRef] rows
         * needed to copy [tagIds] onto [newTaskId].
         */
        fun buildTagCrossRefs(tagIds: List<Long>, newTaskId: Long): List<TaskTagCrossRef> {
            return tagIds.map { tagId -> TaskTagCrossRef(taskId = newTaskId, tagId = tagId) }
        }
    }
}
