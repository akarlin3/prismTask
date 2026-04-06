package com.averykarlin.averytask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.medicationDataStore: DataStore<Preferences> by preferencesDataStore(name = "medication_prefs")

@Singleton
class MedicationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val REMINDER_INTERVAL_MINUTES = intPreferencesKey("reminder_interval_minutes")
        const val DEFAULT_INTERVAL = 0 // 0 = disabled
    }

    fun getReminderIntervalMinutes(): Flow<Int> = context.medicationDataStore.data.map { prefs ->
        prefs[REMINDER_INTERVAL_MINUTES] ?: DEFAULT_INTERVAL
    }

    suspend fun getReminderIntervalMinutesOnce(): Int =
        getReminderIntervalMinutes().first()

    suspend fun setReminderIntervalMinutes(minutes: Int) {
        context.medicationDataStore.edit { prefs ->
            prefs[REMINDER_INTERVAL_MINUTES] = minutes
        }
    }
}
