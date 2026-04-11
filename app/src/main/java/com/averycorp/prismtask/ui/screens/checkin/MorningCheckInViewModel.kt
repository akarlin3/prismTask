package com.averycorp.prismtask.ui.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.CheckInLogRepository
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.data.repository.HabitWithStatus
import com.averycorp.prismtask.data.repository.MoodEnergyRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.CheckInStep
import com.averycorp.prismtask.domain.usecase.MorningCheckInConfig
import com.averycorp.prismtask.domain.usecase.MorningCheckInResolver
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Morning Check-In screen (v1.4.0 V4).
 *
 * Holds the resolved [com.averycorp.prismtask.domain.usecase.CheckInPlan],
 * tracks which steps the user actually walked through this session, and
 * writes a [com.averycorp.prismtask.data.local.entity.CheckInLogEntity]
 * row on completion so the Today screen stops prompting for the day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MorningCheckInViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val moodEnergyRepository: MoodEnergyRepository,
    private val checkInLogRepository: CheckInLogRepository,
    private val taskBehaviorPreferences: TaskBehaviorPreferences
) : ViewModel() {

    private val resolver = MorningCheckInResolver()

    private val _completedSteps = MutableStateFlow<Set<CheckInStep>>(emptySet())
    val completedSteps: StateFlow<Set<CheckInStep>> = _completedSteps

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished

    private val _plan = MutableStateFlow(CheckInScreenState())
    val plan: StateFlow<CheckInScreenState> = _plan

    init {
        viewModelScope.launch {
            val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
            val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)
            val tasks = taskRepository.getAllTasks().first()
            val habits = habitRepository.getHabitsWithTodayStatus().first()
            val plan = resolver.plan(
                tasks = tasks,
                habits = habits,
                config = MorningCheckInConfig(),
                lastCompletedDate = null,
                todayStart = todayStart,
                now = System.currentTimeMillis()
            )
            _plan.value = CheckInScreenState(
                steps = plan.steps,
                topTasks = plan.topTasks,
                habits = plan.todayHabits
            )
        }
    }

    fun markStepComplete(step: CheckInStep) {
        _completedSteps.value = _completedSteps.value + step
    }

    fun logMoodEnergy(mood: Int, energy: Int, notes: String? = null) {
        viewModelScope.launch {
            val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
            val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)
            moodEnergyRepository.upsertForDate(
                date = todayStart,
                mood = mood,
                energy = energy,
                notes = notes,
                timeOfDay = "morning"
            )
            markStepComplete(CheckInStep.MOOD_ENERGY)
        }
    }

    /** Finalizes the check-in: persists a CheckInLog row and flips [isFinished]. */
    fun finalize() {
        viewModelScope.launch {
            val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
            val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)
            checkInLogRepository.record(
                date = todayStart,
                stepsCompleted = _completedSteps.value.toList(),
                tasksReviewed = _plan.value.topTasks.size,
                habitsCompleted = _plan.value.habits.count { it.isCompletedToday }
            )
            _isFinished.value = true
        }
    }
}

data class CheckInScreenState(
    val steps: List<CheckInStep> = emptyList(),
    val topTasks: List<TaskEntity> = emptyList(),
    val habits: List<HabitWithStatus> = emptyList()
)
