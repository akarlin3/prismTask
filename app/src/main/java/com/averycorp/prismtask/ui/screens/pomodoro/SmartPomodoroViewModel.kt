package com.averycorp.prismtask.ui.screens.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.preferences.TimerPreferences
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.PomodoroRequest
import com.averycorp.prismtask.data.remote.api.PomodoroResponse
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.repository.MoodEnergyRepository
import com.averycorp.prismtask.domain.usecase.DefaultPomodoroConfig
import com.averycorp.prismtask.domain.usecase.EnergyAwarePomodoro
import com.averycorp.prismtask.domain.usecase.PomodoroSessionConfig
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import com.averycorp.prismtask.notifications.PomodoroTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    @ApplicationContext private val appContext: Context,
    private val taskDao: TaskDao,
    private val api: PrismTaskApi,
    private val proFeatureGate: ProFeatureGate,
    private val moodEnergyRepository: MoodEnergyRepository,
    private val timerPreferences: TimerPreferences
) : ViewModel() {

    private val energyAwarePomodoro = EnergyAwarePomodoro()

    private val _energyAwareConfig = MutableStateFlow<PomodoroSessionConfig?>(null)
    val energyAwareConfig: StateFlow<PomodoroSessionConfig?> = _energyAwareConfig

    /**
     * True when a Pomodoro session has just completed and we should prompt
     * the user for a quick energy self-report. v1.4.0 V11: this passively
     * builds an energy-by-hour profile without requiring an explicit
     * check-in.
     */
    private val _showPostSessionEnergyPrompt = MutableStateFlow(false)
    val showPostSessionEnergyPrompt: StateFlow<Boolean> = _showPostSessionEnergyPrompt

    fun dismissPostSessionEnergyPrompt() { _showPostSessionEnergyPrompt.value = false }

    fun logPostSessionEnergy(energy: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            moodEnergyRepository.upsertForDate(
                date = now - (now % (24L * 60 * 60 * 1000)),
                mood = 3,
                energy = energy,
                notes = "post-pomodoro",
                timeOfDay = "afternoon"
            )
            _showPostSessionEnergyPrompt.value = false
        }
    }

    val userTier: StateFlow<UserTier> = proFeatureGate.userTier

    private val _screenState = MutableStateFlow(PomodoroState.PLANNING)
    val screenState: StateFlow<PomodoroState> = _screenState

    // Pomodoro config is now sourced entirely from persisted user settings.
    val config: StateFlow<PomodoroConfig> = combine(
        timerPreferences.getPomodoroAvailableMinutes(),
        timerPreferences.getWorkDurationSeconds(),
        timerPreferences.getBreakDurationSeconds(),
        timerPreferences.getLongBreakDurationSeconds(),
        timerPreferences.getPomodoroFocusPreference()
    ) { available, workSec, breakSec, longBreakSec, focus ->
        PomodoroConfig(
            availableMinutes = available,
            sessionLength = workSec / 60,
            breakLength = breakSec / 60,
            longBreakLength = longBreakSec / 60,
            focusPreference = focus
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PomodoroConfig())

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

    /**
     * Listens for tick + completion broadcasts emitted by
     * [PomodoroTimerService]. The service runs independently of the app
     * process lifecycle so this is how we pipe the countdown back into the
     * UI while the user is in the app.
     */
    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PomodoroTimerService.ACTION_TICK -> {
                    val seconds = intent.getIntExtra(
                        PomodoroTimerService.EXTRA_SECONDS_REMAINING, -1
                    )
                    if (seconds >= 0) {
                        _timerSecondsRemaining.value = seconds
                    }
                }
                PomodoroTimerService.ACTION_COMPLETE -> onTimerComplete()
            }
        }
    }

    private var receiverRegistered = false

    init {
        viewModelScope.launch {
            taskDao.getIncompleteRootTasks().collect { tasks ->
                _incompleteTaskCount.value = tasks.size
            }
        }
        // v1.4.0 V11: on first load, look at today's mood/energy logs and
        // pre-fill the planner's session/break lengths with an energy-aware
        // config. Users who haven't opted into mood tracking get the
        // classic 25/5 defaults so the feature is invisible to them.
        viewModelScope.launch {
            val todayStart = System.currentTimeMillis() - 12L * 60 * 60 * 1000
            val logs = moodEnergyRepository.getRange(todayStart, System.currentTimeMillis())
            val current = config.value
            val planned = energyAwarePomodoro.planFromLogs(
                logs,
                DefaultPomodoroConfig(
                    workMinutes = current.sessionLength,
                    breakMinutes = current.breakLength,
                    longBreakMinutes = current.longBreakLength
                )
            )
            _energyAwareConfig.value = planned
        }
        registerTimerReceiver()
    }

    private fun registerTimerReceiver() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(PomodoroTimerService.ACTION_TICK)
                addAction(PomodoroTimerService.ACTION_COMPLETE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(timerReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {
            // Test contexts and edge cases (e.g. early in Application lifecycle)
            // may reject receiver registration; the service still delivers
            // the completion notification via the system notification manager
            // so UI sync is best-effort.
        }
    }

    private fun unregisterTimerReceiver() {
        if (!receiverRegistered) return
        try {
            appContext.unregisterReceiver(timerReceiver)
        } catch (_: Exception) {
            // Already unregistered or context invalid.
        }
        receiverRegistered = false
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
                val cfg = config.value
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
        val durationSeconds = config.value.sessionLength * 60
        _timerSecondsRemaining.value = durationSeconds
        startTimer(durationSeconds, PomodoroTimerService.SESSION_TYPE_WORK)
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        // Stopping the service dismisses its ongoing notification; the
        // remaining seconds are preserved in _timerSecondsRemaining so
        // resumeTimer() can pick up where we left off.
        PomodoroTimerService.stop(appContext)
    }

    fun resumeTimer() {
        val remaining = _timerSecondsRemaining.value
        if (remaining <= 0) return
        val sessionType = if (_screenState.value == PomodoroState.ON_BREAK) {
            val isLongBreak = (_currentSessionIndex.value + 1) % 4 == 0
            if (isLongBreak) PomodoroTimerService.SESSION_TYPE_LONG_BREAK
            else PomodoroTimerService.SESSION_TYPE_BREAK
        } else {
            PomodoroTimerService.SESSION_TYPE_WORK
        }
        startTimer(remaining, sessionType)
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
        PomodoroTimerService.stop(appContext)
        _isTimerRunning.value = false
        _screenState.value = PomodoroState.COMPLETE
        val totalSeconds = config.value.sessionLength * 60 * (_currentSessionIndex.value + 1) -
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
        val durationSeconds = config.value.sessionLength * 60
        _timerSecondsRemaining.value = durationSeconds
        startTimer(durationSeconds, PomodoroTimerService.SESSION_TYPE_WORK)
    }

    fun resetToPlanning() {
        PomodoroTimerService.stop(appContext)
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

    private fun startTimer(durationSeconds: Int, sessionType: String) {
        if (durationSeconds <= 0) return
        _isTimerRunning.value = true
        PomodoroTimerService.start(
            context = appContext,
            durationSeconds = durationSeconds,
            sessionIndex = _currentSessionIndex.value,
            sessionType = sessionType
        )
    }

    private fun onTimerComplete() {
        _isTimerRunning.value = false
        val plan = _plan.value ?: return
        val sessionIndex = _currentSessionIndex.value
        _stats.value = _stats.value.copy(
            sessionsCompleted = sessionIndex + 1,
            totalFocusSeconds = _stats.value.totalFocusSeconds + config.value.sessionLength * 60
        )

        // v1.4.0 V11: trigger the post-session energy prompt so the
        // planner can learn what hours are actually productive.
        _showPostSessionEnergyPrompt.value = true

        // Check if there are more sessions
        if (sessionIndex + 1 >= plan.sessions.size) {
            _screenState.value = PomodoroState.COMPLETE
        } else {
            // Start break
            _screenState.value = PomodoroState.ON_BREAK
            val isLongBreak = (sessionIndex + 1) % 4 == 0
            val durationSeconds =
                if (isLongBreak) config.value.longBreakLength * 60
                else config.value.breakLength * 60
            _timerSecondsRemaining.value = durationSeconds
            val sessionType =
                if (isLongBreak) PomodoroTimerService.SESSION_TYPE_LONG_BREAK
                else PomodoroTimerService.SESSION_TYPE_BREAK
            startTimer(durationSeconds, sessionType)
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterTimerReceiver()
        PomodoroTimerService.stop(appContext)
    }
}
