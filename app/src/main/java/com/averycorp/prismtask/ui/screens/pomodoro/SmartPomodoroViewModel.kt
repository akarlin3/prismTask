package com.averycorp.prismtask.ui.screens.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.PomodoroRequest
import com.averycorp.prismtask.data.remote.api.PomodoroResponse
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PomodoroState {
    PLANNING,
    SESSION_ACTIVE,
    ON_BREAK,
    COMPLETE
}

data class PomodoroConfig(
    val availableMinutes: Int = 120,
    val sessionLength: Int = 25,
    val breakLength: Int = 5,
    val longBreakLength: Int = 15,
    val focusPreference: String = "balanced"
)

data class SessionTask(
    val taskId: Long,
    val title: String,
    val allocatedMinutes: Int
)

data class PomodoroSession(
    val sessionNumber: Int,
    val tasks: List<SessionTask>,
    val rationale: String
)

data class SkippedTask(
    val taskId: Long,
    val reason: String
)

data class PomodoroPlan(
    val sessions: List<PomodoroSession>,
    val totalWorkMinutes: Int,
    val totalBreakMinutes: Int,
    val skippedTasks: List<SkippedTask>
)

data class FocusStats(
    val sessionsCompleted: Int = 0,
    val tasksCompleted: Int = 0,
    val totalFocusSeconds: Int = 0
)

@HiltViewModel
class SmartPomodoroViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val api: PrismTaskApi,
    private val proFeatureGate: ProFeatureGate
) : ViewModel() {

    val userTier: StateFlow<UserTier> = proFeatureGate.userTier

    private val _screenState = MutableStateFlow(PomodoroState.PLANNING)
    val screenState: StateFlow<PomodoroState> = _screenState

    private val _config = MutableStateFlow(PomodoroConfig())
    val config: StateFlow<PomodoroConfig> = _config

    private val _plan = MutableStateFlow<PomodoroPlan?>(null)
    val plan: StateFlow<PomodoroPlan?> = _plan

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentSessionIndex = MutableStateFlow(0)
    val currentSessionIndex: StateFlow<Int> = _currentSessionIndex

    private val _timerSecondsRemaining = MutableStateFlow(0)
    val timerSecondsRemaining: StateFlow<Int> = _timerSecondsRemaining

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    private val _completedTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    val completedTaskIds: StateFlow<Set<Long>> = _completedTaskIds

    private val _stats = MutableStateFlow(FocusStats())
    val stats: StateFlow<FocusStats> = _stats

    private val _incompleteTaskCount = MutableStateFlow(0)
    val incompleteTaskCount: StateFlow<Int> = _incompleteTaskCount

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            taskDao.getIncompleteRootTasks().collect { tasks ->
                _incompleteTaskCount.value = tasks.size
            }
        }
    }

    fun updateConfig(config: PomodoroConfig) {
        _config.value = config
    }

    fun updateAvailableMinutes(minutes: Int) {
        _config.value = _config.value.copy(availableMinutes = minutes)
    }

    fun updateSessionLength(minutes: Int) {
        _config.value = _config.value.copy(sessionLength = minutes)
    }

    fun updateBreakLength(minutes: Int) {
        _config.value = _config.value.copy(breakLength = minutes)
    }

    fun updateFocusPreference(pref: String) {
        _config.value = _config.value.copy(focusPreference = pref)
    }

    private val _showUpgradePrompt = MutableStateFlow(false)
    val showUpgradePrompt: StateFlow<Boolean> = _showUpgradePrompt

    fun dismissUpgradePrompt() {
        _showUpgradePrompt.value = false
    }

    fun generatePlan() {
        if (!proFeatureGate.hasAccess(ProFeatureGate.AI_POMODORO)) {
            _showUpgradePrompt.value = true
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val cfg = _config.value
                val response = api.planPomodoro(
                    PomodoroRequest(
                        availableMinutes = cfg.availableMinutes,
                        sessionLength = cfg.sessionLength,
                        breakLength = cfg.breakLength,
                        longBreakLength = cfg.longBreakLength,
                        focusPreference = cfg.focusPreference
                    )
                )
                _plan.value = PomodoroPlan(
                    sessions = response.sessions.map { s ->
                        PomodoroSession(
                            sessionNumber = s.sessionNumber,
                            tasks = s.tasks.map { t ->
                                SessionTask(t.taskId, t.title, t.allocatedMinutes)
                            },
                            rationale = s.rationale
                        )
                    },
                    totalWorkMinutes = response.totalWorkMinutes,
                    totalBreakMinutes = response.totalBreakMinutes,
                    skippedTasks = response.skippedTasks.map { SkippedTask(it.taskId, it.reason) }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to generate plan"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startSession() {
        _screenState.value = PomodoroState.SESSION_ACTIVE
        _currentSessionIndex.value = 0
        _timerSecondsRemaining.value = _config.value.sessionLength * 60
        startTimer()
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resumeTimer() {
        startTimer()
    }

    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            taskDao.markCompleted(taskId, System.currentTimeMillis())
            _completedTaskIds.value = _completedTaskIds.value + taskId
            _stats.value = _stats.value.copy(tasksCompleted = _stats.value.tasksCompleted + 1)
        }
    }

    fun skipTask(taskId: Long) {
        // Just visual — doesn't affect task state
    }

    fun endEarly() {
        timerJob?.cancel()
        _isTimerRunning.value = false
        _screenState.value = PomodoroState.COMPLETE
        val totalSeconds = _config.value.sessionLength * 60 * (_currentSessionIndex.value + 1) -
                _timerSecondsRemaining.value
        _stats.value = _stats.value.copy(
            sessionsCompleted = _currentSessionIndex.value + 1,
            totalFocusSeconds = totalSeconds
        )
    }

    fun nextSession() {
        val plan = _plan.value ?: return
        val nextIndex = _currentSessionIndex.value + 1
        if (nextIndex >= plan.sessions.size) {
            // All done
            _screenState.value = PomodoroState.COMPLETE
            _stats.value = _stats.value.copy(
                sessionsCompleted = plan.sessions.size,
                totalFocusSeconds = plan.totalWorkMinutes * 60
            )
            return
        }
        _currentSessionIndex.value = nextIndex
        _screenState.value = PomodoroState.SESSION_ACTIVE
        _timerSecondsRemaining.value = _config.value.sessionLength * 60
        startTimer()
    }

    fun resetToPlanning() {
        timerJob?.cancel()
        _screenState.value = PomodoroState.PLANNING
        _plan.value = null
        _currentSessionIndex.value = 0
        _completedTaskIds.value = emptySet()
        _stats.value = FocusStats()
        _isTimerRunning.value = false
    }

    fun clearError() {
        _error.value = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (_timerSecondsRemaining.value > 0 && _isTimerRunning.value) {
                delay(1000)
                if (_isTimerRunning.value) {
                    _timerSecondsRemaining.value -= 1
                }
            }
            if (_timerSecondsRemaining.value <= 0) {
                onTimerComplete()
            }
        }
    }

    private fun onTimerComplete() {
        _isTimerRunning.value = false
        val plan = _plan.value ?: return
        val sessionIndex = _currentSessionIndex.value
        _stats.value = _stats.value.copy(
            sessionsCompleted = sessionIndex + 1,
            totalFocusSeconds = _stats.value.totalFocusSeconds + _config.value.sessionLength * 60
        )

        // Check if there are more sessions
        if (sessionIndex + 1 >= plan.sessions.size) {
            _screenState.value = PomodoroState.COMPLETE
        } else {
            // Start break
            _screenState.value = PomodoroState.ON_BREAK
            val isLongBreak = (sessionIndex + 1) % 4 == 0
            _timerSecondsRemaining.value =
                if (isLongBreak) _config.value.longBreakLength * 60
                else _config.value.breakLength * 60
            startTimer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
