package com.averycorp.averytask.ui.screens.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.preferences.TimerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isRunning: Boolean = false
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerPreferences: TimerPreferences
) : ViewModel() {

    val workDurationSeconds: StateFlow<Int> = timerPreferences.getWorkDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_WORK_SECONDS)

    val breakDurationSeconds: StateFlow<Int> = timerPreferences.getBreakDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_BREAK_SECONDS)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    init {
        // Sync state from preferences. When the active mode's duration changes,
        // update the total; if the timer is idle at full, also update remaining.
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
                if (current.mode == TimerMode.BREAK) {
                    val isIdleAtFull = !current.isRunning &&
                        current.remainingSeconds == current.totalSeconds
                    _uiState.value = current.copy(
                        totalSeconds = brk,
                        remainingSeconds = if (isIdleAtFull) brk else current.remainingSeconds
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
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value
                if (!current.isRunning) break
                val next = current.remainingSeconds - 1
                if (next <= 0) {
                    _uiState.value = current.copy(remainingSeconds = 0, isRunning = false)
                    break
                } else {
                    _uiState.value = current.copy(remainingSeconds = next)
                }
            }
        }
    }

    private fun pause() {
        tickJob?.cancel()
        tickJob = null
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun reset() {
        tickJob?.cancel()
        tickJob = null
        val state = _uiState.value
        val total = when (state.mode) {
            TimerMode.WORK -> workDurationSeconds.value
            TimerMode.BREAK -> breakDurationSeconds.value
        }
        _uiState.value = TimerUiState(
            mode = state.mode,
            remainingSeconds = total,
            totalSeconds = total,
            isRunning = false
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
        _uiState.value = TimerUiState(
            mode = mode,
            remainingSeconds = total,
            totalSeconds = total,
            isRunning = false
        )
    }

    fun setWorkDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            timerPreferences.setWorkDurationSeconds(minutes * 60)
        }
    }

    fun setBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            timerPreferences.setBreakDurationSeconds(minutes * 60)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }
}
