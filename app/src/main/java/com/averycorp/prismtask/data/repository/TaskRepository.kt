package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.converter.RecurrenceConverter
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.remote.CalendarSyncService
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.usecase.RecurrenceEngine
import com.averycorp.prismtask.notifications.ReminderScheduler
import com.averycorp.prismtask.util.DayBoundary
import com.averycorp.prismtask.widget.WidgetUpdateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository
    @Inject
    constructor(
        private val taskDao: TaskDao,
        private val tagDao: TagDao,
        private val syncTracker: SyncTracker,
        private val calendarSyncService: CalendarSyncService,
        private val reminderScheduler: ReminderScheduler,
        private val widgetUpdateManager: WidgetUpdateManager,
        private val taskCompletionRepository: TaskCompletionRepository
    ) {
        fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

        fun getTasksByProject(projectId: Long): Flow<List<TaskEntity>> = taskDao.getTasksByProject(projectId)

        fun getSubtasks(parentTaskId: Long): Flow<List<TaskEntity>> = taskDao.getSubtasks(parentTaskId)

        suspend fun deleteTasksByProjectId(projectId: Long) {
            taskDao.deleteTasksByProjectId(projectId)
            widgetUpdateManager.updateTaskWidgets()
        }

        fun getIncompleteTasks(): Flow<List<TaskEntity>> = taskDao.getIncompleteTasks()

        fun getIncompleteRootTasks(): Flow<List<TaskEntity>> = taskDao.getIncompleteRootTasks()

        fun getTasksDueOnDate(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>> = taskDao.getTasksDueOnDate(startOfDay, endOfDay)

        fun getOverdueTasks(now: Long): Flow<List<TaskEntity>> = taskDao.getOverdueTasks(now)

        suspend fun addSubtask(title: String, parentTaskId: Long, priority: Int = 0): Long {
            val now = System.currentTimeMillis()
            val parent = taskDao.getTaskById(parentTaskId).firstOrNull()
            val nextSortOrder = taskDao.getMaxSubtaskSortOrder(parentTaskId) + 1
            val task =
                TaskEntity(
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
            widgetUpdateManager.updateTaskWidgets()
            return id
        }

        suspend fun reorderSubtasks(parentTaskId: Long, orderedIds: List<Long>) {
            orderedIds.forEachIndexed { index, id ->
                taskDao.updateSortOrder(id, index)
                syncTracker.trackUpdate(id, "task")
            }
        }

        suspend fun updateTaskOrder(taskOrders: List<Pair<Long, Int>>) {
            if (taskOrders.isEmpty()) return
            taskDao.updateSortOrders(taskOrders)
            taskOrders.forEach { (id, _) -> syncTracker.trackUpdate(id, "task") }
        }

        suspend fun getNextRootSortOrder(): Int = taskDao.getMaxRootSortOrder() + 1

        suspend fun getAllTasksOnce(): List<TaskEntity> = taskDao.getAllTasksOnce()

        fun getTaskById(id: Long): Flow<TaskEntity?> = taskDao.getTaskById(id)

        suspend fun getTaskByIdOnce(id: Long): TaskEntity? = taskDao.getTaskByIdOnce(id)

        suspend fun insertTask(task: TaskEntity): Long {
            val id = taskDao.insert(task)
            syncTracker.trackCreate(id, "task")
            calendarSyncService.syncTaskToCalendar(task.copy(id = id))
            widgetUpdateManager.updateTaskWidgets()
            if (task.reminderOffset != null && task.dueDate != null) {
                reminderScheduler.scheduleReminder(
                    taskId = id,
                    taskTitle = task.title,
                    taskDescription = task.description,
                    dueDate = ReminderScheduler.combineDateAndTime(task.dueDate, task.dueTime),
                    reminderOffset = task.reminderOffset
                )
            }
            return id
        }

        suspend fun addTask(
            title: String,
            description: String? = null,
            dueDate: Long? = null,
            dueTime: Long? = null,
            priority: Int = 0,
            projectId: Long? = null,
            parentTaskId: Long? = null,
            lifeCategory: String? = null,
            reminderOffset: Long? = null,
            recurrenceRule: String? = null,
            estimatedDuration: Int? = null
        ): Long {
            val now = System.currentTimeMillis()
            val nextSortOrder = if (parentTaskId == null) taskDao.getMaxRootSortOrder() + 1 else 0
            val task =
                TaskEntity(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    priority = priority,
                    projectId = projectId,
                    parentTaskId = parentTaskId,
                    sortOrder = nextSortOrder,
                    lifeCategory = lifeCategory,
                    reminderOffset = reminderOffset,
                    recurrenceRule = recurrenceRule,
                    estimatedDuration = estimatedDuration,
                    createdAt = now,
                    updatedAt = now
                )
            val id = taskDao.insert(task)
            syncTracker.trackCreate(id, "task")
            calendarSyncService.syncTaskToCalendar(task.copy(id = id))
            widgetUpdateManager.updateTaskWidgets()
            if (reminderOffset != null && dueDate != null) {
                reminderScheduler.scheduleReminder(
                    taskId = id,
                    taskTitle = title,
                    taskDescription = description,
                    dueDate = ReminderScheduler.combineDateAndTime(dueDate, dueTime),
                    reminderOffset = reminderOffset
                )
            }
            return id
        }

        suspend fun updateTask(task: TaskEntity) {
            val updated = task.copy(updatedAt = System.currentTimeMillis())
            taskDao.update(updated)
            syncTracker.trackUpdate(task.id, "task")
            calendarSyncService.syncTaskToCalendar(updated)
            widgetUpdateManager.updateTaskWidgets()
            if (updated.reminderOffset != null && updated.dueDate != null) {
                reminderScheduler.scheduleReminder(
                    taskId = updated.id,
                    taskTitle = updated.title,
                    taskDescription = updated.description,
                    dueDate = ReminderScheduler.combineDateAndTime(updated.dueDate, updated.dueTime),
                    reminderOffset = updated.reminderOffset
                )
            } else {
                reminderScheduler.cancelReminder(updated.id)
            }
        }

        suspend fun rescheduleTask(taskId: Long, newDueDate: Long?) {
            val task = taskDao.getTaskByIdOnce(taskId) ?: return
            val updated = task.copy(dueDate = newDueDate, updatedAt = System.currentTimeMillis())
            taskDao.update(updated)
            syncTracker.trackUpdate(taskId, "task")
            val offset = updated.reminderOffset
            if (offset != null &&
                newDueDate != null
            ) {
                reminderScheduler.scheduleReminder(
                    taskId = updated.id,
                    taskTitle = updated.title,
                    taskDescription = updated.description,
                    dueDate = ReminderScheduler.combineDateAndTime(newDueDate, updated.dueTime),
                    reminderOffset = offset
                )
            } else {
                reminderScheduler.cancelReminder(taskId)
            }
            calendarSyncService.syncTaskToCalendar(updated)
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun planTaskForToday(taskId: Long) {
            val startOfToday = DayBoundary.startOfCurrentDay(0)
            taskDao.setPlanDate(taskId, startOfToday)
            syncTracker.trackUpdate(taskId, "task")
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun completeTask(id: Long) {
            val now = System.currentTimeMillis()
            val task = taskDao.getTaskById(id).firstOrNull()
            if (task != null) {
                val tags = tagDao.getTagsForTask(id).first()
                taskCompletionRepository.recordCompletion(task, tags)
            }
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
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun uncompleteTask(id: Long) {
            taskDao.markIncomplete(id, System.currentTimeMillis())
            syncTracker.trackUpdate(id, "task")
            val task = taskDao.getTaskByIdOnce(id)
            if (task != null) calendarSyncService.syncTaskToCalendar(task)
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun deleteTask(id: Long) {
            calendarSyncService.removeEventForTask(id)
            syncTracker.trackDelete(id, "task")
            taskDao.deleteById(id)
            widgetUpdateManager.updateTaskWidgets()
        }

        fun searchTasks(query: String): Flow<List<TaskEntity>> = taskDao.searchTasks(query)

        fun getArchivedTasks(): Flow<List<TaskEntity>> = taskDao.getArchivedTasks()

        suspend fun archiveTask(id: Long) {
            taskDao.archiveTask(id, System.currentTimeMillis())
            syncTracker.trackUpdate(id, "task")
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun unarchiveTask(id: Long) {
            taskDao.unarchiveTask(id, System.currentTimeMillis())
            syncTracker.trackUpdate(id, "task")
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun permanentlyDeleteTask(id: Long) {
            calendarSyncService.removeEventForTask(id)
            syncTracker.trackDelete(id, "task")
            taskDao.permanentlyDelete(id)
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun toggleFlag(id: Long): Boolean? {
            val task = taskDao.getTaskByIdOnce(id) ?: return null
            val updated = task.copy(isFlagged = !task.isFlagged, updatedAt = System.currentTimeMillis())
            taskDao.update(updated)
            syncTracker.trackUpdate(id, "task")
            return updated.isFlagged
        }

        suspend fun setFlag(id: Long, flagged: Boolean) {
            val task = taskDao.getTaskByIdOnce(id) ?: return
            if (task.isFlagged == flagged) return
            taskDao.update(task.copy(isFlagged = flagged, updatedAt = System.currentTimeMillis()))
            syncTracker.trackUpdate(id, "task")
        }

        fun searchArchivedTasks(query: String): Flow<List<TaskEntity>> = taskDao.searchArchivedTasks(query)

        suspend fun autoArchiveOldCompleted(daysOld: Int) {
            val cutoff =
                System.currentTimeMillis() - (daysOld.toLong() * 24 * 60 * 60 * 1000)
            taskDao.archiveCompletedBefore(cutoff, System.currentTimeMillis())
        }

        fun getArchivedCount(): Flow<Int> = taskDao.getArchivedCount()

        suspend fun duplicateTask(taskId: Long, includeSubtasks: Boolean = false, copyDueDate: Boolean = false): Long {
            val original = taskDao.getTaskByIdOnce(taskId) ?: return -1L
            val now = System.currentTimeMillis()
            val nextSortOrder = if (original.parentTaskId !=
                null
            ) {
                taskDao.getMaxSubtaskSortOrder(original.parentTaskId) + 1
            } else {
                taskDao.getMaxRootSortOrder() + 1
            }
            val duplicate = buildDuplicateEntity(original, nextSortOrder, now, copyDueDate)
            val newId = taskDao.insert(duplicate)
            syncTracker.trackCreate(newId, "task")
            val tagIds = tagDao.getTagIdsForTaskOnce(taskId)
            for (crossRef in buildTagCrossRefs(tagIds, newId)) {
                tagDao.addTagToTask(crossRef)
            }
            if (includeSubtasks) {
                val originalSubtasks = taskDao.getSubtasksOnce(taskId)
                originalSubtasks.forEachIndexed {
                        index,
                        sub
                    ->
                    val subCopy = buildSubtaskDuplicate(sub, newId, index, now)
                    val newSubId = taskDao.insert(subCopy)
                    syncTracker.trackCreate(newSubId, "task")
                }
            }
            calendarSyncService.syncTaskToCalendar(duplicate.copy(id = newId))
            widgetUpdateManager.updateTaskWidgets()
            return newId
        }

        suspend fun batchUpdatePriority(taskIds: List<Long>, priority: Int) {
            if (taskIds.isEmpty()) return
            taskDao.batchUpdatePriority(taskIds, priority)
            taskIds.forEach { syncTracker.trackUpdate(it, "task") }
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun batchReschedule(taskIds: List<Long>, newDueDate: Long?) {
            if (taskIds.isEmpty()) return
            taskDao.batchReschedule(taskIds, newDueDate)
            for (id in taskIds) {
                val updated =
                    taskDao.getTaskByIdOnce(id) ?: continue
                val offset = updated.reminderOffset
                if (offset != null &&
                    newDueDate != null
                ) {
                    reminderScheduler.scheduleReminder(
                        taskId = updated.id,
                        taskTitle = updated.title,
                        taskDescription = updated.description,
                        dueDate = ReminderScheduler.combineDateAndTime(newDueDate, updated.dueTime),
                        reminderOffset = offset
                    )
                } else {
                    reminderScheduler.cancelReminder(id)
                }
                calendarSyncService.syncTaskToCalendar(updated)
                syncTracker.trackUpdate(id, "task")
            }
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun batchMoveToProject(taskIds: List<Long>, newProjectId: Long?) {
            if (taskIds.isEmpty()) return
            taskDao.batchMoveToProject(taskIds, newProjectId)
            taskIds.forEach { syncTracker.trackUpdate(it, "task") }
            widgetUpdateManager.updateTaskWidgets()
        }

        suspend fun moveToProject(taskId: Long, projectId: Long?, cascadeSubtasks: Boolean = false): List<Long> {
            val task = taskDao.getTaskByIdOnce(taskId) ?: return emptyList()
            val subtasks = if (cascadeSubtasks) taskDao.getSubtasksOnce(taskId) else emptyList()
            val idsToMove = buildMoveTargetIds(task, subtasks, cascadeSubtasks)
            taskDao.batchMoveToProject(idsToMove, projectId)
            idsToMove.forEach { syncTracker.trackUpdate(it, "task") }
            widgetUpdateManager.updateTaskWidgets()
            return idsToMove
        }

        suspend fun batchAddTag(taskIds: List<Long>, tagId: Long) {
            if (taskIds.isEmpty()) return
            taskDao.batchAddTag(taskIds, tagId)
            taskIds.forEach { syncTracker.trackUpdate(it, "task") }
        }

        suspend fun batchRemoveTag(taskIds: List<Long>, tagId: Long) {
            if (taskIds.isEmpty()) return
            taskDao.batchRemoveTag(taskIds, tagId)
            taskIds.forEach { syncTracker.trackUpdate(it, "task") }
        }

        fun getTasksGroupedByDate(): Flow<Map<String, List<TaskEntity>>> = taskDao.getIncompleteRootTasks().map { tasks ->
            groupByDate(
                tasks
            )
        }

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
                    task.dueDate <
                        startOfTomorrow -> "Today"
                    task.dueDate < startOfDayAfterTomorrow -> "Tomorrow"
                    task.dueDate < endOfThisWeek -> "This Week"
                    else -> "Later"
                }
                grouped.getOrPut(bucket) { mutableListOf() }.add(task)
            }
            val order = listOf("Overdue", "Today", "Tomorrow", "This Week", "Later")
            return order.filter { it in grouped }.associateWith { grouped.getValue(it) }
        }

        companion object {
            fun buildDuplicateEntity(
                original: TaskEntity,
                nextSortOrder: Int,
                now: Long,
                copyDueDate: Boolean = false
            ): TaskEntity = original.copy(
                id = 0,
                title = "Copy of ${original.title}",
                dueDate = if (copyDueDate) original.dueDate else null,
                dueTime = if (copyDueDate) original.dueTime else null,
                plannedDate = null,
                isCompleted = false,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
                reminderOffset = if (copyDueDate) original.reminderOffset else null,
                archivedAt = null,
                scheduledStartTime = null,
                sortOrder = nextSortOrder
            )

            fun buildSubtaskDuplicate(
                originalSubtask: TaskEntity,
                newParentId: Long,
                sortOrder: Int,
                now: Long
            ): TaskEntity = originalSubtask
                .copy(
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

            fun buildTagCrossRefs(tagIds: List<Long>, newTaskId: Long): List<TaskTagCrossRef> = tagIds.map { tagId ->
                TaskTagCrossRef(
                    taskId = newTaskId,
                    tagId = tagId
                )
            }

            fun buildMoveTargetIds(
                task: TaskEntity,
                subtasks: List<TaskEntity>,
                cascadeSubtasks: Boolean
            ): List<Long> = if (cascadeSubtasks) {
                listOf(task.id) +
                    subtasks.map { it.id }
            } else {
                listOf(task.id)
            }

            fun applyProjectMove(
                task: TaskEntity,
                subtasks: List<TaskEntity>,
                newProjectId: Long?,
                cascadeSubtasks: Boolean,
                now: Long
            ): List<TaskEntity> {
                val updatedParent = task.copy(projectId = newProjectId, updatedAt = now)
                return if (cascadeSubtasks) {
                    listOf(updatedParent) +
                        subtasks.map { it.copy(projectId = newProjectId, updatedAt = now) }
                } else {
                    listOf(updatedParent)
                }
            }
        }
    }
