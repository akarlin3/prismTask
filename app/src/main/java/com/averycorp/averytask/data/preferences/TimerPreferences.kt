package com.averycorp.averytask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_prefs")

@Singleton
class TimerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val WORK_DURATION_SECONDS = intPreferencesKey("work_duration_seconds")
        private val BREAK_DURATION_SECONDS = intPreferencesKey("break_duration_seconds")
        private val LONG_BREAK_DURATION_SECONDS = intPreferencesKey("long_break_duration_seconds")
        private val POMODORO_ENABLED = intPreferencesKey("pomodoro_enabled")
        private val SESSIONS_UNTIL_LONG_BREAK = intPreferencesKey("sessions_until_long_break")
        private val AUTO_START_BREAKS = intPreferencesKey("auto_start_breaks")
        private val AUTO_START_WORK = intPreferencesKey("auto_start_work")

        const val DEFAULT_WORK_SECONDS = 25 * 60
        const val DEFAULT_BREAK_SECONDS = 5 * 60
        const val DEFAULT_LONG_BREAK_SECONDS = 15 * 60
        const val DEFAULT_SESSIONS_UNTIL_LONG_BREAK = 4
        const val MIN_SECONDS = 60
        const val MAX_SECONDS = 180 * 60
    }

    fun getWorkDurationSeconds(): Flow<Int> = context.timerDataStore.data.map { prefs ->
        prefs[WORK_DURATION_SECONDS] ?: DEFAULT_WORK_SECONDS
    }

    fun getBreakDurationSeconds(): Flow<Int> = context.timerDataStore.data.map { prefs ->
        prefs[BREAK_DURATION_SECONDS] ?: DEFAULT_BREAK_SECONDS
    }

    fun getLongBreakDurationSeconds(): Flow<Int> = context.timerDataStore.data.map { prefs ->
        prefs[LONG_BREAK_DURATION_SECONDS] ?: DEFAULT_LONG_BREAK_SECONDS
    }

    fun getPomodoroEnabled(): Flow<Boolean> = context.timerDataStore.data.map { prefs ->
        (prefs[POMODORO_ENABLED] ?: 0) == 1
    }

    fun getSessionsUntilLongBreak(): Flow<Int> = context.timerDataStore.data.map { prefs ->
        prefs[SESSIONS_UNTIL_LONG_BREAK] ?: DEFAULT_SESSIONS_UNTIL_LONG_BREAK
    }

    fun getAutoStartBreaks(): Flow<Boolean> = context.timerDataStore.data.map { prefs ->
        (prefs[AUTO_START_BREAKS] ?: 0) == 1
    }

    fun getAutoStartWork(): Flow<Boolean> = context.timerDataStore.data.map { prefs ->
        (prefs[AUTO_START_WORK] ?: 0) == 1
    }

    suspend fun setWorkDurationSeconds(seconds: Int) {
        context.timerDataStore.edit { prefs ->
            prefs[WORK_DURATION_SECONDS] = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        }
    }

    suspend fun setBreakDurationSeconds(seconds: Int) {
        context.timerDataStore.edit { prefs ->
            prefs[BREAK_DURATION_SECONDS] = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        }
    }

    suspend fun setLongBreakDurationSeconds(seconds: Int) {
        context.timerDataStore.edit { prefs ->
            prefs[LONG_BREAK_DURATION_SECONDS] = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        }
    }

    suspend fun setPomodoroEnabled(enabled: Boolean) {
        context.timerDataStore.edit { prefs ->
            prefs[POMODORO_ENABLED] = if (enabled) 1 else 0
        }
    }

    suspend fun setSessionsUntilLongBreak(sessions: Int) {
        context.timerDataStore.edit { prefs ->
            prefs[SESSIONS_UNTIL_LONG_BREAK] = sessions.coerceIn(2, 10)
        }
    }

    suspend fun setAutoStartBreaks(enabled: Boolean) {
        context.timerDataStore.edit { prefs ->
            prefs[AUTO_START_BREAKS] = if (enabled) 1 else 0
        }
    }

    suspend fun setAutoStartWork(enabled: Boolean) {
        context.timerDataStore.edit { prefs ->
            prefs[AUTO_START_WORK] = if (enabled) 1 else 0
        }
    }
}
