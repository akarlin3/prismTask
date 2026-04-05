package com.averykarlin.averytask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    }

    fun getThemeMode(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }

    fun getAccentColor(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACCENT_COLOR_KEY] ?: "#2563EB"
    }

    suspend fun setAccentColor(hex: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCENT_COLOR_KEY] = hex
        }
    }
}
