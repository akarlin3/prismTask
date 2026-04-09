package com.averycorp.averytask.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.local.dao.TaskDao
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.preferences.SortPreferences
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
    private val sortPreferences: SortPreferences
) : ViewModel() {

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
}
