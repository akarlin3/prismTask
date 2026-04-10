package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
        private val BACKGROUND_COLOR_KEY = stringPreferencesKey("background_color")
        private val SURFACE_COLOR_KEY = stringPreferencesKey("surface_color")
        private val ERROR_COLOR_KEY = stringPreferencesKey("error_color")
        private val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
        private val PRIORITY_COLOR_NONE_KEY = stringPreferencesKey("priority_color_none")
        private val PRIORITY_COLOR_LOW_KEY = stringPreferencesKey("priority_color_low")
        private val PRIORITY_COLOR_MEDIUM_KEY = stringPreferencesKey("priority_color_medium")
        private val PRIORITY_COLOR_HIGH_KEY = stringPreferencesKey("priority_color_high")
        private val PRIORITY_COLOR_URGENT_KEY = stringPreferencesKey("priority_color_urgent")
    }

    fun getThemeMode(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode }
    }

    fun getAccentColor(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACCENT_COLOR_KEY] ?: "#2563EB"
    }

    suspend fun setAccentColor(hex: String) {
        context.dataStore.edit { prefs -> prefs[ACCENT_COLOR_KEY] = hex }
    }

    fun getBackgroundColor(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[BACKGROUND_COLOR_KEY] ?: ""
    }

    suspend fun setBackgroundColor(hex: String) {
        context.dataStore.edit { prefs -> prefs[BACKGROUND_COLOR_KEY] = hex }
    }

    fun getSurfaceColor(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SURFACE_COLOR_KEY] ?: ""
    }

    suspend fun setSurfaceColor(hex: String) {
        context.dataStore.edit { prefs -> prefs[SURFACE_COLOR_KEY] = hex }
    }

    fun getErrorColor(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ERROR_COLOR_KEY] ?: ""
    }

    suspend fun setErrorColor(hex: String) {
        context.dataStore.edit { prefs -> prefs[ERROR_COLOR_KEY] = hex }
    }

    fun getFontScale(): Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[FONT_SCALE_KEY] ?: 1.0f
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { prefs -> prefs[FONT_SCALE_KEY] = scale }
    }

    fun getPriorityColorNone(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_NONE_KEY] ?: ""
    }

    fun getPriorityColorLow(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_LOW_KEY] ?: ""
    }

    fun getPriorityColorMedium(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_MEDIUM_KEY] ?: ""
    }

    fun getPriorityColorHigh(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_HIGH_KEY] ?: ""
    }

    fun getPriorityColorUrgent(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_URGENT_KEY] ?: ""
    }

    suspend fun setPriorityColor(level: Int, hex: String) {
        val key = when (level) {
            0 -> PRIORITY_COLOR_NONE_KEY
            1 -> PRIORITY_COLOR_LOW_KEY
            2 -> PRIORITY_COLOR_MEDIUM_KEY
            3 -> PRIORITY_COLOR_HIGH_KEY
            4 -> PRIORITY_COLOR_URGENT_KEY
            else -> return
        }
        context.dataStore.edit { prefs -> prefs[key] = hex }
    }

    suspend fun resetColorOverrides() {
        context.dataStore.edit { prefs ->
            prefs.remove(BACKGROUND_COLOR_KEY)
            prefs.remove(SURFACE_COLOR_KEY)
            prefs.remove(ERROR_COLOR_KEY)
            prefs.remove(FONT_SCALE_KEY)
            prefs.remove(PRIORITY_COLOR_NONE_KEY)
            prefs.remove(PRIORITY_COLOR_LOW_KEY)
            prefs.remove(PRIORITY_COLOR_MEDIUM_KEY)
            prefs.remove(PRIORITY_COLOR_HIGH_KEY)
            prefs.remove(PRIORITY_COLOR_URGENT_KEY)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
