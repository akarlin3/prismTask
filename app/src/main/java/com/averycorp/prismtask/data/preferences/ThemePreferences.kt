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

internal val Context.themePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

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
        private val RECENT_CUSTOM_COLORS_KEY = stringPreferencesKey("recent_custom_colors")

        private const val MAX_RECENT_CUSTOM_COLORS = 5

        /** Returns true iff [hex] is a valid 6- or 8-digit hex color string. */
        fun isValidHex(hex: String): Boolean {
            val trimmed = hex.trim()
            if (!trimmed.startsWith("#")) return false
            val body = trimmed.drop(1)
            if (body.length != 6 && body.length != 8) return false
            return body.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        }

        /** Inserts [hex] at the head of [existing], capped at [MAX_RECENT_CUSTOM_COLORS]. */
        fun addToRecentColors(existing: List<String>, hex: String): List<String> {
            val upper = hex.uppercase()
            val dedup = existing.filterNot { it.equals(upper, ignoreCase = true) }
            return (listOf(upper) + dedup).take(MAX_RECENT_CUSTOM_COLORS)
        }
    }

    fun getThemeMode(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.themePrefsDataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode }
    }

    fun getAccentColor(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[ACCENT_COLOR_KEY] ?: "#2563EB"
    }

    suspend fun setAccentColor(hex: String) {
        context.themePrefsDataStore.edit { prefs -> prefs[ACCENT_COLOR_KEY] = hex }
    }

    fun getBackgroundColor(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[BACKGROUND_COLOR_KEY] ?: ""
    }

    suspend fun setBackgroundColor(hex: String) {
        context.themePrefsDataStore.edit { prefs -> prefs[BACKGROUND_COLOR_KEY] = hex }
    }

    fun getSurfaceColor(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[SURFACE_COLOR_KEY] ?: ""
    }

    suspend fun setSurfaceColor(hex: String) {
        context.themePrefsDataStore.edit { prefs -> prefs[SURFACE_COLOR_KEY] = hex }
    }

    fun getErrorColor(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[ERROR_COLOR_KEY] ?: ""
    }

    suspend fun setErrorColor(hex: String) {
        context.themePrefsDataStore.edit { prefs -> prefs[ERROR_COLOR_KEY] = hex }
    }

    fun getFontScale(): Flow<Float> = context.themePrefsDataStore.data.map { prefs ->
        prefs[FONT_SCALE_KEY] ?: 1.0f
    }

    suspend fun setFontScale(scale: Float) {
        context.themePrefsDataStore.edit { prefs -> prefs[FONT_SCALE_KEY] = scale }
    }

    fun getPriorityColorNone(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_NONE_KEY] ?: ""
    }

    fun getPriorityColorLow(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_LOW_KEY] ?: ""
    }

    fun getPriorityColorMedium(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_MEDIUM_KEY] ?: ""
    }

    fun getPriorityColorHigh(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
        prefs[PRIORITY_COLOR_HIGH_KEY] ?: ""
    }

    fun getPriorityColorUrgent(): Flow<String> = context.themePrefsDataStore.data.map { prefs ->
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
        context.themePrefsDataStore.edit { prefs -> prefs[key] = hex }
    }

    fun getRecentCustomColors(): Flow<List<String>> = context.themePrefsDataStore.data.map { prefs ->
        prefs[RECENT_CUSTOM_COLORS_KEY]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && isValidHex(it) }
            ?: emptyList()
    }

    suspend fun addRecentCustomColor(hex: String) {
        if (!isValidHex(hex)) return
        context.themePrefsDataStore.edit { prefs ->
            val current = prefs[RECENT_CUSTOM_COLORS_KEY]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val updated = addToRecentColors(current, hex)
            prefs[RECENT_CUSTOM_COLORS_KEY] = updated.joinToString(",")
        }
    }

    suspend fun resetColorOverrides() {
        context.themePrefsDataStore.edit { prefs ->
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
        context.themePrefsDataStore.edit { it.clear() }
    }
}
