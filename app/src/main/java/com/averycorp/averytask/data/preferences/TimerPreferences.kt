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

        const val DEFAULT_WORK_SECONDS = 25 * 60
        const val DEFAULT_BREAK_SECONDS = 5 * 60
        const val MIN_SECONDS = 60
        const val MAX_SECONDS = 180 * 60
    }

    fun getWorkDurationSeconds(): Flow<Int> = context.timerDataStore.data.map { prefs ->
        prefs[WORK_DURATION_SECONDS] ?: DEFAULT_WORK_SECONDS
    }

    fun getBreakDurationSeconds(): Flow<Int> = context.timerDataStore.data.map { prefs ->
        prefs[BREAK_DURATION_SECONDS] ?: DEFAULT_BREAK_SECONDS
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
}
