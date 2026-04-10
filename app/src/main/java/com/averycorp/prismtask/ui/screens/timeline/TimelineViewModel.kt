package com.averycorp.prismtask.ui.screens.timeline

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.ui.components.QuickRescheduleFormatter
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TimeBlock(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val taskId: Long?,
    val priority: Int,
    val color: String
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val sortPreferences: SortPreferences
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskCountByProject: StateFlow<Map<Long, Int>> = taskDao.getIncompleteRootTasks()
        .map { tasks ->
            tasks.groupingBy { it.projectId }
                .eachCount()
                .mapNotNull { (id, count) -> id?.let { it to count } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val zone = ZoneId.systemDefault()

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate

    val currentSort: StateFlow<String> =
        sortPreferences.observeSortMode(SortPreferences.ScreenKeys.TIMELINE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortPreferences.SortModes.DEFAULT)

    fun onChangeSort(sortMode: String) {
        viewModelScope.launch {
            sortPreferences.setSortMode(SortPreferences.ScreenKeys.TIMELINE, sortMode)
        }
    }

    private val dayTasks = _currentDate.flatMapLatest { date ->
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        taskDao.getTasksDueOnDate(start, end).map { tasks ->
            tasks.filter { it.parentTaskId == null && it.archivedAt == null && !it.isCompleted }
        }
    }

    val scheduledBlocks: StateFlow<List<TimeBlock>> = dayTasks.map { tasks ->
        tasks.filter { it.scheduledStartTime != null }.map { task ->
            val duration = (task.estimatedDuration ?: 30) * 60 * 1000L
            TimeBlock(
                id = "task_${task.id}",
                title = task.title,
                startTime = task.scheduledStartTime!!,
                endTime = task.scheduledStartTime + duration,
                taskId = task.id,
                priority = task.priority,
                color = ""
            )
        }.sortedBy { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unscheduledTasks: StateFlow<List<TaskEntity>> = combine(dayTasks, currentSort) { tasks, sort ->
        val unscheduled = tasks.filter { it.scheduledStartTime == null }
        when (sort) {
            SortPreferences.SortModes.DUE_DATE -> unscheduled.sortedWith(
                compareBy<TaskEntity> { it.dueDate == null }.thenBy { it.dueDate }.thenByDescending { it.priority }
            )
            SortPreferences.SortModes.PRIORITY -> unscheduled.sortedByDescending { it.priority }
            SortPreferences.SortModes.ALPHABETICAL -> unscheduled.sortedBy { it.title.lowercase() }
            SortPreferences.SortModes.DATE_CREATED -> unscheduled.sortedByDescending { it.createdAt }
            else -> unscheduled
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onNavigateDate(date: LocalDate) { _currentDate.value = date }
    fun onPreviousDay() { _currentDate.value = _currentDate.value.minusDays(1) }
    fun onNextDay() { _currentDate.value = _currentDate.value.plusDays(1) }
    fun onGoToToday() { _currentDate.value = LocalDate.now() }

    fun onScheduleTask(taskId: Long, startTimeMillis: Long) {
        viewModelScope.launch {
            val task = taskDao.getTaskByIdOnce(taskId) ?: return@launch
            taskDao.update(task.copy(scheduledStartTime = startTimeMillis, updatedAt = System.currentTimeMillis()))
        }
    }

    fun onUnscheduleTask(taskId: Long) {
        viewModelScope.launch {
            val task = taskDao.getTaskByIdOnce(taskId) ?: return@launch
            taskDao.update(task.copy(scheduledStartTime = null, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Helper for the quick-reschedule popup, which needs the full [TaskEntity]
     * (for the `hasDueDate` flag) but the Timeline screen only has a taskId
     * inside its scheduled blocks. Runs in [viewModelScope] so the caller
     * receives the result on the main dispatcher.
     */
    fun loadTaskForPopup(taskId: Long, onLoaded: (TaskEntity?) -> Unit) {
        viewModelScope.launch {
            onLoaded(taskDao.getTaskByIdOnce(taskId))
        }
    }

    fun onRescheduleTask(taskId: Long, newDueDate: Long?) {
        viewModelScope.launch {
            try {
                val previous = taskRepository.getTaskByIdOnce(taskId)?.dueDate
                taskRepository.rescheduleTask(taskId, newDueDate)
                val label = QuickRescheduleFormatter.describe(newDueDate)
                val result = snackbarHostState.showSnackbar(
                    message = "Rescheduled to $label",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.rescheduleTask(taskId, previous)
                }
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to reschedule", e)
            }
        }
    }

    fun onPlanTaskForToday(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.planTaskForToday(taskId)
                snackbarHostState.showSnackbar("Planned for today", duration = SnackbarDuration.Short)
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to plan for today", e)
            }
        }
    }

    fun onMoveToProject(
        taskId: Long,
        newProjectId: Long?,
        cascadeSubtasks: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val task = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
                val previousParentProjectId = task.projectId
                val previousSubtaskProjects: Map<Long, Long?> = if (cascadeSubtasks) {
                    taskRepository.getSubtasks(taskId).first().associate { it.id to it.projectId }
                } else emptyMap()

                taskRepository.moveToProject(taskId, newProjectId, cascadeSubtasks)
                val projectName = newProjectId?.let { id ->
                    projects.value.find { it.id == id }?.name
                } ?: "No Project"
                val result = snackbarHostState.showSnackbar(
                    message = "Moved '${task.title}' to $projectName",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.moveToProject(taskId, previousParentProjectId, false)
                    previousSubtaskProjects.entries.groupBy { it.value }
                        .forEach { (origProjectId, entries) ->
                            taskRepository.batchMoveToProject(entries.map { it.key }, origProjectId)
                        }
                }
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to move task to project", e)
            }
        }
    }

    fun onCreateProjectAndMoveTask(
        taskId: Long,
        name: String,
        cascadeSubtasks: Boolean = false
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newId = projectRepository.addProject(name.trim())
                onMoveToProject(taskId, newId, cascadeSubtasks)
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to create project", e)
            }
        }
    }

    fun onDeleteTaskWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                val savedTask = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
                taskRepository.deleteTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task deleted",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.insertTask(savedTask)
                }
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to delete task", e)
            }
        }
    }

    fun onDuplicateTask(taskId: Long) {
        viewModelScope.launch {
            try {
                val newId = taskRepository.duplicateTask(taskId, includeSubtasks = false)
                if (newId <= 0L) {
                    snackbarHostState.showSnackbar("Something went wrong")
                    return@launch
                }
                snackbarHostState.showSnackbar(
                    message = "Task Duplicated",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to duplicate task", e)
            }
        }
    }

    suspend fun getSubtaskCount(taskId: Long): Int =
        taskRepository.getSubtasks(taskId).first().size
}
