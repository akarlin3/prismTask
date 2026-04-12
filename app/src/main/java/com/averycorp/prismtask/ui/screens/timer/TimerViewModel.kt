package com.averycorp.prismtask.ui.screens.timer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.TimerPreferences
import com.averycorp.prismtask.widget.TimerStateDataStore
import com.averycorp.prismtask.widget.TimerWidgetState
import com.averycorp.prismtask.widget.WidgetUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TimerMode { WORK, BREAK }

data class TimerUiState(
    val mode: TimerMode = TimerMode.WORK,
    val remainingSeconds: Int = TimerPreferences.DEFAULT_WORK_SECONDS,
    val totalSeconds: Int = TimerPreferences.DEFAULT_WORK_SECONDS,
    val isRunning: Boolean = false,
    val pomodoroEnabled: Boolean = false,
    val completedSessions: Int = 0,
    val sessionsUntilLongBreak: Int = TimerPreferences.DEFAULT_SESSIONS_UNTIL_LONG_BREAK,
    val isLongBreak: Boolean = false,
    val autoStartBreaks: Boolean = false,
    val autoStartWork: Boolean = false
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val timerPreferences: TimerPreferences,
    private val widgetUpdateManager: WidgetUpdateManager
) : ViewModel() {

    private val workDurationSeconds: StateFlow<Int> = timerPreferences.getWorkDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerPreferences.DEFAULT_WORK_SECONDS)

    private val breakDurationSeconds: StateFlow<Int> = timerPreferences.getBreakDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerPreferences.DEFAULT_BREAK_SECONDS)

    private val longBreakDurationSeconds: StateFlow<Int> = timerPreferences.getLongBreakDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerPreferences.DEFAULT_LONG_BREAK_SECONDS)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    init {
        // Sync pomodoro preferences
        viewModelScope.launch {
            timerPreferences.getPomodoroEnabled().collect { enabled ->
                _uiState.value = _uiState.value.copy(pomodoroEnabled = enabled)
            }
        }
        viewModelScope.launch {
            timerPreferences.getSessionsUntilLongBreak().collect { sessions ->
                _uiState.value = _uiState.value.copy(sessionsUntilLongBreak = sessions)
            }
        }
        viewModelScope.launch {
            timerPreferences.getAutoStartBreaks().collect { auto ->
                _uiState.value = _uiState.value.copy(autoStartBreaks = auto)
            }
        }
        viewModelScope.launch {
            timerPreferences.getAutoStartWork().collect { auto ->
                _uiState.value = _uiState.value.copy(autoStartWork = auto)
            }
        }

        // Sync duration preferences (existing logic)
        viewModelScope.launch {
            timerPreferences.getWorkDurationSeconds().collect { work ->
                val current = _uiState.value
                if (current.mode == TimerMode.WORK) {
                    val isIdleAtFull = !current.isRunning &&
                        current.remainingSeconds == current.totalSeconds
                    _uiState.value = current.copy(
                        totalSeconds = work,
                        remainingSeconds = if (isIdleAtFull) work else current.remainingSeconds
                    )
                }
            }
        }
        viewModelScope.launch {
            timerPreferences.getBreakDurationSeconds().collect { brk ->
                val current = _uiState.value
                if (current.mode == TimerMode.BREAK && !current.isLongBreak) {
                    val isIdleAtFull = !current.isRunning &&
                        current.remainingSeconds == current.totalSeconds
                    _uiState.value = current.copy(
                        totalSeconds = brk,
                        remainingSeconds = if (isIdleAtFull) brk else current.remainingSeconds
                    )
                }
            }
        }
        viewModelScope.launch {
            timerPreferences.getLongBreakDurationSeconds().collect { longBrk ->
                val current = _uiState.value
                if (current.mode == TimerMode.BREAK && current.isLongBreak) {
                    val isIdleAtFull = !current.isRunning &&
                        current.remainingSeconds == current.totalSeconds
                    _uiState.value = current.copy(
                        totalSeconds = longBrk,
                        remainingSeconds = if (isIdleAtFull) longBrk else current.remainingSeconds
                    )
                }
            }
        }
    }

    fun toggleStartPause() {
        val state = _uiState.value
        if (state.isRunning) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        val state = _uiState.value
        if (state.remainingSeconds <= 0) return
        _uiState.value = state.copy(isRunning = true)
        syncWidgetState()
        tickJob?.cancel()
        var ticksSinceWidgetUpdate = 0
        tickJob = viewModelScope.launch {
            widgetUpdateManager.updateTimerWidget()
            while (true) {
                delay(1000L)
                val current = _uiState.value
                if (!current.isRunning) break
                val next = current.remainingSeconds - 1
                ticksSinceWidgetUpdate++
                if (next <= 0) {
                    _uiState.value = current.copy(remainingSeconds = 0, isRunning = false)
                    syncWidgetState()
                    widgetUpdateManager.updateTimerWidget()
                    onTimerCompleted()
                    break
                } else {
                    _uiState.value = current.copy(remainingSeconds = next)
                    // Update widget every 30 seconds for sub-minute accuracy
                    if (ticksSinceWidgetUpdate >= 30) {
                        ticksSinceWidgetUpdate = 0
                        syncWidgetState()
                        widgetUpdateManager.updateTimerWidget()
                    }
                }
            }
        }
    }

    private fun onTimerCompleted() {
        val state = _uiState.value
        if (!state.pomodoroEnabled) return

        if (state.mode == TimerMode.WORK) {
            val newCompleted = state.completedSessions + 1
            val isLongBreak = newCompleted % state.sessionsUntilLongBreak == 0
            val breakDuration = if (isLongBreak) {
                longBreakDurationSeconds.value
            } else {
                breakDurationSeconds.value
            }
            _uiState.value = state.copy(
                mode = TimerMode.BREAK,
                completedSessions = newCompleted,
                isLongBreak = isLongBreak,
                remainingSeconds = breakDuration,
                totalSeconds = breakDuration,
                isRunning = false
            )
            if (state.autoStartBreaks) {
                start()
            }
        } else {
            val workDuration = workDurationSeconds.value
            _uiState.value = state.copy(
                mode = TimerMode.WORK,
                isLongBreak = false,
                remainingSeconds = workDuration,
                totalSeconds = workDuration,
                isRunning = false
            )
            if (state.autoStartWork) {
                start()
            }
        }
    }

    fun skipToNext() {
        val state = _uiState.value
        if (!state.pomodoroEnabled) return
        tickJob?.cancel()
        tickJob = null

        if (state.mode == TimerMode.WORK) {
            val newCompleted = state.completedSessions + 1
            val isLongBreak = newCompleted % state.sessionsUntilLongBreak == 0
            val breakDuration = if (isLongBreak) {
                longBreakDurationSeconds.value
            } else {
                breakDurationSeconds.value
            }
            _uiState.value = state.copy(
                mode = TimerMode.BREAK,
                completedSessions = newCompleted,
                isLongBreak = isLongBreak,
                remainingSeconds = breakDuration,
                totalSeconds = breakDuration,
                isRunning = false
            )
        } else {
            val workDuration = workDurationSeconds.value
            _uiState.value = state.copy(
                mode = TimerMode.WORK,
                isLongBreak = false,
                remainingSeconds = workDuration,
                totalSeconds = workDuration,
                isRunning = false
            )
        }
    }

    private fun pause() {
        tickJob?.cancel()
        tickJob = null
        _uiState.value = _uiState.value.copy(isRunning = false)
        syncWidgetState()
        viewModelScope.launch { widgetUpdateManager.updateTimerWidget() }
    }

    fun reset() {
        tickJob?.cancel()
        tickJob = null
        val state = _uiState.value
        val total = when {
            state.mode == TimerMode.WORK -> workDurationSeconds.value
            state.isLongBreak -> longBreakDurationSeconds.value
            else -> breakDurationSeconds.value
        }
        _uiState.value = state.copy(
            remainingSeconds = total,
            totalSeconds = total,
            isRunning = false
        )
        syncWidgetState()
        viewModelScope.launch { widgetUpdateManager.updateTimerWidget() }
    }

    fun resetPomodoro() {
        tickJob?.cancel()
        tickJob = null
        val workDuration = workDurationSeconds.value
        _uiState.value = _uiState.value.copy(
            mode = TimerMode.WORK,
            remainingSeconds = workDuration,
            totalSeconds = workDuration,
            isRunning = false,
            completedSessions = 0,
            isLongBreak = false
        )
    }

    fun setMode(mode: TimerMode) {
        if (_uiState.value.mode == mode) return
        tickJob?.cancel()
        tickJob = null
        val total = when (mode) {
            TimerMode.WORK -> workDurationSeconds.value
            TimerMode.BREAK -> breakDurationSeconds.value
        }
        _uiState.value = _uiState.value.copy(
            mode = mode,
            remainingSeconds = total,
            totalSeconds = total,
            isRunning = false,
            isLongBreak = false
        )
    }

    fun togglePomodoroEnabled() {
        viewModelScope.launch {
            val newEnabled = !_uiState.value.pomodoroEnabled
            timerPreferences.setPomodoroEnabled(newEnabled)
            if (newEnabled) {
                resetPomodoro()
            }
        }
    }

    fun toggleAutoStartBreaks() {
        viewModelScope.launch {
            timerPreferences.setAutoStartBreaks(!_uiState.value.autoStartBreaks)
        }
    }

    fun toggleAutoStartWork() {
        viewModelScope.launch {
            timerPreferences.setAutoStartWork(!_uiState.value.autoStartWork)
        }
    }

    /** Syncs the current timer UI state to the widget DataStore. */
    private fun syncWidgetState() {
        val s = _uiState.value
        viewModelScope.launch {
            TimerStateDataStore.write(appContext, TimerWidgetState(
                isRunning = s.isRunning,
                isPaused = !s.isRunning && s.remainingSeconds < s.totalSeconds && s.remainingSeconds > 0,
                remainingSeconds = s.remainingSeconds,
                totalSeconds = s.totalSeconds,
                sessionType = if (s.mode == TimerMode.WORK) "work" else "break",
                currentSession = s.completedSessions + if (s.mode == TimerMode.WORK) 1 else 0,
                totalSessions = s.sessionsUntilLongBreak
            ))
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
        // Clear widget state when the timer screen is closed
        viewModelScope.launch {
            val s = _uiState.value
            if (!s.isRunning) {
                TimerStateDataStore.clear(appContext)
                widgetUpdateManager.updateTimerWidget()
            }
        }
    }
}
