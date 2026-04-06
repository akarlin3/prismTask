package com.averykarlin.averytask.ui.screens.weekview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.TaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val taskDao: TaskDao
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val _currentWeekStart = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart

    val weekDays: StateFlow<List<LocalDate>> = _currentWeekStart.map { start ->
        (0L..6L).map { start.plusDays(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekTasks: StateFlow<Map<LocalDate, List<TaskEntity>>> = _currentWeekStart.flatMapLatest { start ->
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = start.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        taskDao.getTasksDueOnDate(startMillis, endMillis).map { tasks ->
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
            map.mapValues { (_, tasks) -> tasks.sortedByDescending { it.priority } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
}
