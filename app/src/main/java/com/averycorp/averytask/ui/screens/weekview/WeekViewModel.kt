package com.averycorp.averytask.ui.screens.weekview

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.local.dao.TaskDao
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.preferences.SortPreferences
import com.averycorp.averytask.data.repository.TaskRepository
import com.averycorp.averytask.ui.components.QuickRescheduleFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val taskRepository: TaskRepository,
    private val sortPreferences: SortPreferences
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    private val zone = ZoneId.systemDefault()

    private val _currentWeekStart = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart

    val currentSort: StateFlow<String> =
        sortPreferences.observeSortMode(SortPreferences.ScreenKeys.WEEK_VIEW)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortPreferences.SortModes.DEFAULT)

    fun onChangeSort(sortMode: String) {
        viewModelScope.launch {
            sortPreferences.setSortMode(SortPreferences.ScreenKeys.WEEK_VIEW, sortMode)
        }
    }

    val weekDays: StateFlow<List<LocalDate>> = _currentWeekStart.map { start ->
        (0L..6L).map { start.plusDays(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekTasks: StateFlow<Map<LocalDate, List<TaskEntity>>> = combine(
        _currentWeekStart.flatMapLatest { start ->
            val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = start.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            taskDao.getTasksDueOnDate(startMillis, endMillis).map { tasks -> start to tasks }
        },
        currentSort
    ) { (start, tasks), sort ->
        val map = mutableMapOf<LocalDate, MutableList<TaskEntity>>()
        for (day in 0L..6L) {
            map[start.plusDays(day)] = mutableListOf()
        }
        for (task in tasks.filter { it.parentTaskId == null && it.archivedAt == null }) {
            val date = task.dueDate?.let {
                java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            }
            if (date != null && map.containsKey(date)) {
                map[date]!!.add(task)
            }
        }
        map.mapValues { (_, dayTasks) -> sortTasks(dayTasks, sort) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private fun sortTasks(tasks: List<TaskEntity>, sort: String): List<TaskEntity> = when (sort) {
        SortPreferences.SortModes.DUE_DATE -> tasks.sortedWith(
            compareBy<TaskEntity> { it.dueDate == null }.thenBy { it.dueDate }.thenByDescending { it.priority }
        )
        SortPreferences.SortModes.PRIORITY -> tasks.sortedByDescending { it.priority }
        SortPreferences.SortModes.ALPHABETICAL -> tasks.sortedBy { it.title.lowercase() }
        SortPreferences.SortModes.DATE_CREATED -> tasks.sortedByDescending { it.createdAt }
        else -> tasks.sortedByDescending { it.priority }
    }

    fun onPreviousWeek() {
        _currentWeekStart.value = _currentWeekStart.value.minusWeeks(1)
    }

    fun onNextWeek() {
        _currentWeekStart.value = _currentWeekStart.value.plusWeeks(1)
    }

    fun onGoToToday() {
        _currentWeekStart.value = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun onMoveTask(taskId: Long, newDate: LocalDate) {
        val millis = newDate.atStartOfDay(zone).toInstant().toEpochMilli()
        viewModelScope.launch {
            taskDao.updateDueDate(taskId, millis)
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
                Log.e("WeekViewVM", "Failed to reschedule", e)
            }
        }
    }

    fun onPlanTaskForToday(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.planTaskForToday(taskId)
                snackbarHostState.showSnackbar("Planned for today", duration = SnackbarDuration.Short)
            } catch (e: Exception) {
                Log.e("WeekViewVM", "Failed to plan for today", e)
            }
        }
    }
}
