package com.averycorp.prismtask.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Lightweight DataStore that the timer ViewModel writes to when state
 * changes, and the TimerWidget reads from in provideGlance.
 *
 * ViewModels can't be accessed from widgets directly, so this acts as the
 * shared communication layer between the in-app timer and the widget.
 */
private val Context.timerStateDataStore by preferencesDataStore(name = "timer_widget_state")

data class TimerWidgetState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentTaskTitle: String? = null,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val sessionType: String = "work", // "work" or "break"
    val currentSession: Int = 0,
    val totalSessions: Int = 4
)

object TimerStateDataStore {

    private val IS_RUNNING = booleanPreferencesKey("timer_is_running")
    private val IS_PAUSED = booleanPreferencesKey("timer_is_paused")
    private val TASK_TITLE = stringPreferencesKey("timer_task_title")
    private val REMAINING_SECONDS = intPreferencesKey("timer_remaining_seconds")
    private val TOTAL_SECONDS = intPreferencesKey("timer_total_seconds")
    private val SESSION_TYPE = stringPreferencesKey("timer_session_type")
    private val CURRENT_SESSION = intPreferencesKey("timer_current_session")
    private val TOTAL_SESSIONS = intPreferencesKey("timer_total_sessions")

    suspend fun write(context: Context, state: TimerWidgetState) {
        context.timerStateDataStore.edit { prefs ->
            prefs[IS_RUNNING] = state.isRunning
            prefs[IS_PAUSED] = state.isPaused
            if (state.currentTaskTitle != null) {
                prefs[TASK_TITLE] = state.currentTaskTitle
            } else {
                prefs.remove(TASK_TITLE)
            }
            prefs[REMAINING_SECONDS] = state.remainingSeconds
            prefs[TOTAL_SECONDS] = state.totalSeconds
            prefs[SESSION_TYPE] = state.sessionType
            prefs[CURRENT_SESSION] = state.currentSession
            prefs[TOTAL_SESSIONS] = state.totalSessions
        }
    }

    suspend fun read(context: Context): TimerWidgetState {
        val prefs = context.timerStateDataStore.data.first()
        return TimerWidgetState(
            isRunning = prefs[IS_RUNNING] ?: false,
            isPaused = prefs[IS_PAUSED] ?: false,
            currentTaskTitle = prefs[TASK_TITLE],
            remainingSeconds = prefs[REMAINING_SECONDS] ?: 0,
            totalSeconds = prefs[TOTAL_SECONDS] ?: 0,
            sessionType = prefs[SESSION_TYPE] ?: "work",
            currentSession = prefs[CURRENT_SESSION] ?: 0,
            totalSessions = prefs[TOTAL_SESSIONS] ?: 4
        )
    }

    suspend fun clear(context: Context) {
        context.timerStateDataStore.edit { it.clear() }
    }
}
