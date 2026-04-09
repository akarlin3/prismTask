package com.averycorp.averytask.ui.screens.today

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.local.dao.TaskDao
import com.averycorp.averytask.data.local.entity.TagEntity
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.preferences.DashboardPreferences
import com.averycorp.averytask.data.preferences.HabitListPreferences
import com.averycorp.averytask.data.preferences.SortPreferences
import com.averycorp.averytask.data.preferences.TaskBehaviorPreferences
import com.averycorp.averytask.data.repository.HabitRepository
import com.averycorp.averytask.util.DayBoundary
import com.averycorp.averytask.data.repository.HabitWithStatus
import com.averycorp.averytask.data.local.entity.ProjectEntity
import com.averycorp.averytask.data.repository.LeisureRepository
import com.averycorp.averytask.data.repository.ProjectRepository
import com.averycorp.averytask.data.repository.SchoolworkRepository
import com.averycorp.averytask.data.repository.SelfCareRepository
import com.averycorp.averytask.data.repository.TagRepository
import com.averycorp.averytask.data.repository.TaskRepository
import com.averycorp.averytask.ui.components.QuickRescheduleFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val taskDao: TaskDao,
    private val habitRepository: HabitRepository,
    private val projectRepository: ProjectRepository,
    private val dashboardPreferences: DashboardPreferences,
    private val habitListPreferences: HabitListPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val sortPreferences: SortPreferences
) : ViewModel() {

    /**
     * Persisted sort mode for the Today screen. Screens that don't yet have
     * their own sort selector still expose this so future UI can read/write
     * the same key without a second migration.
     */
    val currentSort: StateFlow<String> =
        sortPreferences.observeSortMode(SortPreferences.ScreenKeys.TODAY)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortPreferences.SortModes.DEFAULT)

    fun onChangeSort(sortMode: String) {
        viewModelScope.launch {
            sortPreferences.setSortMode(SortPreferences.ScreenKeys.TODAY, sortMode)
        }
    }

    val snackbarHostState = SnackbarHostState()

    /**
     * Reactive "start of current day" timestamp. Re-emits whenever the user
     * changes their day-start hour in settings, so that downstream task/habit
     * queries automatically refresh and reset to the new day's window.
     */
    private val dayStart: StateFlow<Long> = taskBehaviorPreferences.getDayStartHour()
        .map { DayBoundary.startOfCurrentDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayBoundary.startOfCurrentDay(0))

    val sectionOrder: StateFlow<List<String>> = dashboardPreferences.getSectionOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardPreferences.DEFAULT_ORDER)

    val hiddenSections: StateFlow<Set<String>> = dashboardPreferences.getHiddenSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val progressStyle: StateFlow<String> = dashboardPreferences.getProgressStyle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ring")

    val collapsedSections: StateFlow<Set<String>> = dashboardPreferences.getCollapsedSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardPreferences.DEFAULT_COLLAPSED)

    fun onToggleSectionCollapsed(sectionKey: String) {
        viewModelScope.launch {
            val isCollapsed = collapsedSections.value.contains(sectionKey)
            dashboardPreferences.setSectionCollapsed(sectionKey, !isCollapsed)
        }
    }

    private suspend fun currentStartOfToday(): Long =
        DayBoundary.startOfCurrentDay(taskBehaviorPreferences.getDayStartHour().first())

    init {
        viewModelScope.launch {
            taskDao.clearExpiredPlans(currentStartOfToday())
        }
    }

    val overdueTasks: StateFlow<List<TaskEntity>> = dayStart.flatMapLatest { start ->
        taskDao.getOverdueRootTasks(start)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTasks: StateFlow<List<TaskEntity>> = dayStart.flatMapLatest { start ->
        taskDao.getTodayTasks(start, start + DayBoundary.DAY_MILLIS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannedTasks: StateFlow<List<TaskEntity>> = dayStart.flatMapLatest { start ->
        taskDao.getPlannedForToday(start, start + DayBoundary.DAY_MILLIS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedToday: StateFlow<List<TaskEntity>> = dayStart.flatMapLatest { start ->
        taskDao.getCompletedToday(start)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTodayItems: StateFlow<List<TaskEntity>> =
        combine(todayTasks, plannedTasks) { today, planned ->
            today + planned
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTodayCount: StateFlow<Int> = allTodayItems.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedTodayCount: StateFlow<Int> = completedToday.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val progressPercent: StateFlow<Float> =
        combine(totalTodayCount, completedTodayCount) { total, completed ->
            if (total + completed == 0) 0f else completed.toFloat() / (total + completed).toFloat()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Tag map for task items
    private val allDisplayTasks = combine(overdueTasks, todayTasks, plannedTasks, completedToday) { o, t, p, c ->
        o + t + p + c
    }

    val taskTagsMap: StateFlow<Map<Long, List<TagEntity>>> = allDisplayTasks.flatMapLatest { tasks ->
        val ids = tasks.map { it.id }
        if (ids.isEmpty()) flowOf(emptyMap())
        else {
            val flows = ids.map { id -> tagRepository.getTagsForTask(id).map { tags -> id to tags } }
            combine(flows) { pairs -> pairs.toMap() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Habits
    private val selfCareEnabled: StateFlow<Boolean> = habitListPreferences.isSelfCareEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val medicationEnabled: StateFlow<Boolean> = habitListPreferences.isMedicationEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val schoolEnabled: StateFlow<Boolean> = habitListPreferences.isSchoolEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val leisureEnabled: StateFlow<Boolean> = habitListPreferences.isLeisureEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val houseworkEnabled: StateFlow<Boolean> = habitListPreferences.isHouseworkEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val todayHabits: StateFlow<List<HabitWithStatus>> = combine(
        habitRepository.getHabitsWithTodayStatus(),
        selfCareEnabled, medicationEnabled, schoolEnabled, leisureEnabled, houseworkEnabled
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val habits = values[0] as List<HabitWithStatus>
        val selfCareOn = values[1] as Boolean
        val medicationOn = values[2] as Boolean
        val schoolOn = values[3] as Boolean
        val leisureOn = values[4] as Boolean
        val houseworkOn = values[5] as Boolean
        val disabledNames = mutableSetOf<String>()
        if (!selfCareOn) {
            disabledNames.add(SelfCareRepository.MORNING_HABIT_NAME)
            disabledNames.add(SelfCareRepository.BEDTIME_HABIT_NAME)
        }
        if (!medicationOn) disabledNames.add(SelfCareRepository.MEDICATION_HABIT_NAME)
        if (!houseworkOn) disabledNames.add(SelfCareRepository.HOUSEWORK_HABIT_NAME)
        if (!schoolOn) disabledNames.add(SchoolworkRepository.SCHOOL_HABIT_NAME)
        if (!leisureOn) disabledNames.add(LeisureRepository.LEISURE_HABIT_NAME)
        habits
            .filter { it.habit.name !in disabledNames }
            .sortedBy { it.habit.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitCompletedCount: StateFlow<Int> = todayHabits.map { habits ->
        habits.count { it.isCompletedToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Combined progress (tasks + habits)
    val combinedTotal: StateFlow<Int> = combine(totalTodayCount, todayHabits) { taskTotal, habits ->
        taskTotal + habits.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val combinedCompleted: StateFlow<Int> = combine(completedTodayCount, habitCompletedCount) { taskDone, habitDone ->
        taskDone + habitDone
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val combinedProgress: StateFlow<Float> = combine(combinedTotal, combinedCompleted) { total, completed ->
        if (total + completed == 0) 0f else completed.toFloat() / (total + completed).toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun onToggleHabitCompletion(habitId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyCompleted) {
                habitRepository.uncompleteHabit(habitId, System.currentTimeMillis())
            } else {
                habitRepository.completeHabit(habitId, System.currentTimeMillis())
            }
        }
    }

    // Plan for Today
    val tasksNotInToday: StateFlow<List<TaskEntity>> = dayStart.flatMapLatest { start ->
        taskDao.getTasksNotInToday(start, start + DayBoundary.DAY_MILLIS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive "start of today" exposed for UI (e.g. quick-add plannedDate override).
    val startOfToday: StateFlow<Long> = dayStart

    private val _showPlanSheet = MutableStateFlow(false)
    val showPlanSheet: StateFlow<Boolean> = _showPlanSheet

    fun onShowPlanSheet() { _showPlanSheet.value = true }
    fun onDismissPlanSheet() { _showPlanSheet.value = false }

    fun onPlanForToday(taskId: Long) {
        viewModelScope.launch {
            taskDao.setPlanDate(taskId, currentStartOfToday())
        }
    }

    fun onPlanForToday(taskIds: List<Long>) {
        viewModelScope.launch {
            val start = currentStartOfToday()
            taskIds.forEach { id -> taskDao.setPlanDate(id, start) }
        }
    }

    fun onPlanAllOverdue() {
        viewModelScope.launch {
            val start = currentStartOfToday()
            overdueTasks.value.forEach { task -> taskDao.setPlanDate(task.id, start) }
        }
    }

    fun onRemoveFromToday(taskId: Long) {
        viewModelScope.launch {
            taskDao.setPlanDate(taskId, null)
        }
    }

    fun onToggleComplete(taskId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isCurrentlyCompleted) {
                    taskRepository.uncompleteTask(taskId)
                } else {
                    taskRepository.completeTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to toggle complete", e)
            }
        }
    }

    fun onCompleteWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.completeTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task completed",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.uncompleteTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to complete task", e)
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
                Log.e("TodayVM", "Failed to delete task", e)
            }
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
                Log.e("TodayVM", "Failed to reschedule", e)
            }
        }
    }

    fun onPlanTaskForToday(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.planTaskForToday(taskId)
                snackbarHostState.showSnackbar(
                    message = "Planned for today",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to plan for today", e)
            }
        }
    }

    /**
     * Reactive task-count map (projectId -> root-task count) that backs the
     * move-to-project sheet on the Today screen. Uses all tasks currently
     * on screen (overdue + today + planned) so the counts reflect the
     * scope the user is actually interacting with.
     */
    val taskCountByProject: StateFlow<Map<Long, Int>> = allDisplayTasks
        .map { tasks ->
            tasks.groupingBy { it.projectId }
                .eachCount()
                .mapNotNull { (id, count) -> id?.let { it to count } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Moves a single task into [newProjectId] (or null to clear the
     * project). Mirrors the task-list version with a per-task undo
     * snackbar. [cascadeSubtasks] propagates to subtasks when true.
     */
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
                    previousSubtaskProjects
                        .entries
                        .groupBy { it.value }
                        .forEach { (origProjectId, entries) ->
                            taskRepository.batchMoveToProject(
                                entries.map { it.key },
                                origProjectId
                            )
                        }
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to move task to project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Creates a new project on-the-fly from the move sheet and then moves
     * the given task into it.
     */
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
                Log.e("TodayVM", "Failed to create project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    suspend fun getSubtaskCount(taskId: Long): Int =
        taskRepository.getSubtasks(taskId).first().size

    // Rollover
    fun onRolloverToTomorrow(taskIds: List<Long>) {
        viewModelScope.launch {
            val tomorrow = currentStartOfToday() + DayBoundary.DAY_MILLIS
            taskIds.forEach { id ->
                taskDao.updateDueDate(id, tomorrow)
            }
        }
    }

    fun onClearDueDates(taskIds: List<Long>) {
        viewModelScope.launch {
            taskIds.forEach { id ->
                taskDao.updateDueDate(id, null)
            }
        }
    }
}
