package com.averycorp.prismtask.ui.screens.today

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.DashboardPreferences
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.util.DayBoundary
import com.averycorp.prismtask.data.repository.HabitWithStatus
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.SchoolworkRepository
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.data.repository.TaskTemplateRepository
import com.averycorp.prismtask.ui.components.QuickRescheduleFormatter
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
    private val templateRepository: TaskTemplateRepository,
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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            taskDao.clearExpiredPlans(currentStartOfToday())
        }
        viewModelScope.launch {
            // Wait for first emission from a key data flow, then mark loading done
            dayStart.flatMapLatest { start -> taskDao.getTodayTasks(start, start + DayBoundary.DAY_MILLIS) }.first()
            _isLoading.value = false
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

    private val allTodayHabits: StateFlow<List<HabitWithStatus>> = combine(
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

    // Daily habit chips — exclude bookable habits (they have their own sections)
    val todayHabits: StateFlow<List<HabitWithStatus>> = allTodayHabits
        .map { list -> list.filter { !it.habit.isBookable } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookable habits booked for today
    val scheduledTodayHabits: StateFlow<List<HabitWithStatus>> = combine(
        habitRepository.getHabitsWithFullStatus(), dayStart
    ) { habits, start ->
        val endOfDay = start + com.averycorp.prismtask.util.DayBoundary.DAY_MILLIS
        habits.filter { hws ->
            hws.habit.isBookable && hws.habit.isBooked &&
                hws.habit.bookedDate != null &&
                hws.habit.bookedDate in start until endOfDay
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookable habits that are overdue (last log > expected interval)
    val overdueBookableHabits: StateFlow<List<HabitWithStatus>> = habitRepository.getHabitsWithFullStatus()
        .map { habits ->
            habits.filter { hws ->
                if (!hws.habit.isBookable) return@filter false
                val lastDone = hws.lastLogDate ?: return@filter true
                val periodDays = when (hws.habit.frequencyPeriod) {
                    "weekly" -> 7L
                    "fortnightly" -> 14L
                    "monthly" -> 30L
                    "bimonthly" -> 60L
                    "quarterly" -> 90L
                    else -> return@filter false
                }
                val elapsed = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
                    System.currentTimeMillis() - lastDone
                )
                elapsed > periodDays
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitCompletedCount: StateFlow<Int> = todayHabits.map { habits ->
        habits.count { it.isCompletedToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val habitTotalCount: StateFlow<Int> = todayHabits.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allHabitsCompletedToday: StateFlow<Boolean> = todayHabits.map { habits ->
        habits.isEmpty() || habits.all { it.isCompletedToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Combined progress (tasks + habits)
    val combinedTotal: StateFlow<Int> = combine(totalTodayCount, completedTodayCount, todayHabits) { taskTotal, taskDone, habits ->
        taskTotal + taskDone + habits.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val combinedCompleted: StateFlow<Int> = combine(completedTodayCount, habitCompletedCount) { taskDone, habitDone ->
        taskDone + habitDone
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val combinedProgress: StateFlow<Float> = combine(combinedTotal, combinedCompleted) { total, completed ->
        if (total == 0) 0f else completed.toFloat() / total.toFloat()
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
                Log.e("TodayVM", "Failed to duplicate task", e)
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

    /**
     * Top 4 most-used templates for the Plan-for-Today sheet chip row.
     * `getAllTemplates()` already orders by `usage_count DESC`, so a
     * straight `take(4)` gives us the most frequently used ones.
     */
    val topTemplates: StateFlow<List<TaskTemplateEntity>> = templateRepository.getAllTemplates()
        .map { it.take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Creates a task from a template and immediately plans it for today so
     * it shows up on the current day's dashboard. Surfaces a snackbar so
     * users can confirm the chip tap registered.
     */
    fun onCreateTaskFromTemplateForToday(templateId: Long) {
        viewModelScope.launch {
            try {
                val today = currentStartOfToday()
                val newTaskId = templateRepository.createTaskFromTemplate(
                    templateId = templateId,
                    dueDateOverride = today,
                    quickUse = true
                )
                // Pin to today's dashboard via planDate too — if the template
                // has no due date, dueDateOverride still sets it, so this is
                // a safety net for templates that already carry their own
                // recurrence / schedule.
                taskDao.setPlanDate(newTaskId, today)
                val title = taskRepository.getTaskByIdOnce(newTaskId)?.title.orEmpty()
                snackbarHostState.showSnackbar(
                    message = "Added '${title.ifBlank { "task" }}' for today",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to create task from template", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }
}
