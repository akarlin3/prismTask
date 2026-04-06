package com.averykarlin.averytask.ui.screens.today

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.preferences.DashboardPreferences
import com.averykarlin.averytask.data.repository.HabitRepository
import com.averykarlin.averytask.data.repository.HabitWithStatus
import com.averykarlin.averytask.data.repository.SelfCareRepository
import com.averykarlin.averytask.data.repository.TagRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val taskDao: TaskDao,
    private val habitRepository: HabitRepository,
    private val dashboardPreferences: DashboardPreferences
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    val sectionOrder: StateFlow<List<String>> = dashboardPreferences.getSectionOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardPreferences.DEFAULT_ORDER)

    val hiddenSections: StateFlow<Set<String>> = dashboardPreferences.getHiddenSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val progressStyle: StateFlow<String> = dashboardPreferences.getProgressStyle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ring")

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun endOfToday(): Long = startOfToday() + 24 * 60 * 60 * 1000

    init {
        viewModelScope.launch {
            taskDao.clearExpiredPlans(startOfToday())
        }
    }

    val overdueTasks: StateFlow<List<TaskEntity>> = taskDao.getOverdueRootTasks(startOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTasks: StateFlow<List<TaskEntity>> = taskDao.getTodayTasks(startOfToday(), endOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannedTasks: StateFlow<List<TaskEntity>> = taskDao.getPlannedForToday(startOfToday(), endOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedToday: StateFlow<List<TaskEntity>> = taskDao.getCompletedToday(startOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    private val selfCareNames = setOf(
        SelfCareRepository.MORNING_HABIT_NAME,
        SelfCareRepository.BEDTIME_HABIT_NAME,
        SelfCareRepository.MEDICATION_HABIT_NAME
    )

    val todayHabits: StateFlow<List<HabitWithStatus>> = habitRepository.getHabitsWithTodayStatus()
        .map { habits ->
            habits.filter { it.habit.name !in selfCareNames }
                .sortedBy { it.habit.sortOrder }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    val tasksNotInToday: StateFlow<List<TaskEntity>> = taskDao.getTasksNotInToday(startOfToday(), endOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showPlanSheet = MutableStateFlow(false)
    val showPlanSheet: StateFlow<Boolean> = _showPlanSheet

    fun onShowPlanSheet() { _showPlanSheet.value = true }
    fun onDismissPlanSheet() { _showPlanSheet.value = false }

    fun onPlanForToday(taskId: Long) {
        viewModelScope.launch {
            taskDao.setPlanDate(taskId, startOfToday())
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

    // Rollover
    fun onRolloverToTomorrow(taskIds: List<Long>) {
        val tomorrow = startOfToday() + 24 * 60 * 60 * 1000
        viewModelScope.launch {
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
