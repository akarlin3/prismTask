package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.shakeDataStore: DataStore<Preferences> by preferencesDataStore(name = "shake_prefs")

/**
 * Preferences that control the shake-to-screenshot/report-a-bug gesture.
 *
 * Users can disable the gesture entirely or adjust its sensitivity when they
 * find accidental triggers too frequent (or too hard to invoke).
 */
@Singleton
class ShakePreferences
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SHAKE_ENABLED = booleanPreferencesKey("shake_enabled")
        private val SHAKE_SENSITIVITY = stringPreferencesKey("shake_sensitivity")

        const val SENSITIVITY_LOW = "low"
        const val SENSITIVITY_MEDIUM = "medium"
        const val SENSITIVITY_HIGH = "high"

        const val DEFAULT_ENABLED = true
        const val DEFAULT_SENSITIVITY = SENSITIVITY_MEDIUM

        val ALL_SENSITIVITIES = listOf(SENSITIVITY_LOW, SENSITIVITY_MEDIUM, SENSITIVITY_HIGH)
    }

    fun getEnabled(): Flow<Boolean> = context.shakeDataStore.data.map { prefs ->
        prefs[SHAKE_ENABLED] ?: DEFAULT_ENABLED
    }

    fun getSensitivity(): Flow<String> = context.shakeDataStore.data.map { prefs ->
        val stored = prefs[SHAKE_SENSITIVITY] ?: DEFAULT_SENSITIVITY
        if (stored in ALL_SENSITIVITIES) stored else DEFAULT_SENSITIVITY
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.shakeDataStore.edit { it[SHAKE_ENABLED] = enabled }
    }

    suspend fun setSensitivity(sensitivity: String) {
        val normalized = if (sensitivity in ALL_SENSITIVITIES) sensitivity else DEFAULT_SENSITIVITY
        context.shakeDataStore.edit { it[SHAKE_SENSITIVITY] = normalized }
    }

    /** Clears every persisted shake preference, reverting to defaults. */
    suspend fun clearAll() {
        context.shakeDataStore.edit { it.clear() }
    }
}
