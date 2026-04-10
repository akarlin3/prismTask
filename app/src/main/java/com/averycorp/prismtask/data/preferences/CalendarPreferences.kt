package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.calendarDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_prefs")

@Singleton
class CalendarPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ENABLED_KEY = booleanPreferencesKey("calendar_sync_enabled")
        private val CALENDAR_ID_KEY = longPreferencesKey("calendar_id")
        private val CALENDAR_NAME_KEY = stringPreferencesKey("calendar_name")
    }

    fun isEnabled(): Flow<Boolean> = context.calendarDataStore.data.map { prefs ->
        prefs[ENABLED_KEY] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.calendarDataStore.edit { prefs -> prefs[ENABLED_KEY] = enabled }
    }

    fun getCalendarId(): Flow<Long> = context.calendarDataStore.data.map { prefs ->
        prefs[CALENDAR_ID_KEY] ?: -1L
    }

    suspend fun setCalendarId(id: Long) {
        context.calendarDataStore.edit { prefs -> prefs[CALENDAR_ID_KEY] = id }
    }

    fun getCalendarName(): Flow<String> = context.calendarDataStore.data.map { prefs ->
        prefs[CALENDAR_NAME_KEY] ?: ""
    }

    suspend fun setCalendarName(name: String) {
        context.calendarDataStore.edit { prefs -> prefs[CALENDAR_NAME_KEY] = name }
    }

    suspend fun clearAll() {
        context.calendarDataStore.edit { it.clear() }
    }
}
