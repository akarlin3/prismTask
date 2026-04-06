package com.averykarlin.averytask.ui.screens.monthview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.TaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class DayInfo(
    val taskCount: Int,
    val completedCount: Int,
    val hasOverdue: Boolean,
    val hasUrgent: Boolean,
    val topPriority: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthViewModel @Inject constructor(
    private val taskDao: TaskDao
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    val monthDayInfos: StateFlow<Map<LocalDate, DayInfo>> = _currentMonth.flatMapLatest { month ->
        val firstDay = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        val startMillis = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = LocalDate.now()

        taskDao.getTasksDueOnDate(startMillis, endMillis).map { tasks ->
            val rootTasks = tasks.filter { it.parentTaskId == null && it.archivedAt == null }
            val grouped = mutableMapOf<LocalDate, MutableList<TaskEntity>>()
            for (task in rootTasks) {
                val date = task.dueDate?.let {
                    java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                } ?: continue
                grouped.getOrPut(date) { mutableListOf() }.add(task)
            }
            grouped.mapValues { (date, dayTasks) ->
                DayInfo(
                    taskCount = dayTasks.size,
                    completedCount = dayTasks.count { it.isCompleted },
                    hasOverdue = date.isBefore(today) && dayTasks.any { !it.isCompleted },
                    hasUrgent = dayTasks.any { it.priority >= 4 },
                    topPriority = dayTasks.maxOfOrNull { it.priority } ?: 0
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedDateTasks: StateFlow<List<TaskEntity>> = combine(_selectedDate, _currentMonth) { date, _ ->
        date
    }.flatMapLatest { date ->
        if (date == null) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            taskDao.getTasksDueOnDate(start, end).map { tasks ->
                tasks.filter { it.parentTaskId == null && it.archivedAt == null }
                    .sortedWith(compareBy<TaskEntity> { it.isCompleted }.thenByDescending { it.priority })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onPreviousMonth() { _currentMonth.value = _currentMonth.value.minusMonths(1) }
    fun onNextMonth() { _currentMonth.value = _currentMonth.value.plusMonths(1) }
    fun onGoToToday() {
        _currentMonth.value = YearMonth.now()
        _selectedDate.value = LocalDate.now()
    }
    fun onSelectDate(date: LocalDate) { _selectedDate.value = date }
}
