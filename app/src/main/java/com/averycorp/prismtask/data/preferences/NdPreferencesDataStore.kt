package com.averycorp.prismtask.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed persistence for Neurodivergent Mode preferences.
 *
 * Takes a [DataStore] in its constructor so it can be unit-tested without an Android
 * Context; production wiring lives in [com.averycorp.prismtask.di.PreferencesModule].
 *
 * ## Mode activation logic
 * - When [setAdhdMode] is called with `true`: all ADHD sub-settings flip ON.
 * - When [setCalmMode] is called with `true`: all Calm sub-settings flip ON.
 * - When a mode is toggled OFF: all its sub-settings flip OFF. The two modes have
 *   zero overlap so toggling one off never affects the other.
 * - Individual sub-setting changes do NOT auto-disable the parent mode toggle.
 */
class NdPreferencesDataStore(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // Top-level mode toggles
        val KEY_ADHD_MODE = booleanPreferencesKey("nd_adhd_mode_enabled")
        val KEY_CALM_MODE = booleanPreferencesKey("nd_calm_mode_enabled")

        // Calm Mode sub-settings
        val KEY_REDUCE_ANIMATIONS = booleanPreferencesKey("nd_reduce_animations")
        val KEY_MUTED_COLOR_PALETTE = booleanPreferencesKey("nd_muted_color_palette")
        val KEY_QUIET_MODE = booleanPreferencesKey("nd_quiet_mode")
        val KEY_REDUCE_HAPTICS = booleanPreferencesKey("nd_reduce_haptics")
        val KEY_SOFT_CONTRAST = booleanPreferencesKey("nd_soft_contrast")

        // ADHD Mode sub-settings
        val KEY_TASK_DECOMPOSITION = booleanPreferencesKey("nd_task_decomposition")
        val KEY_FOCUS_GUARD = booleanPreferencesKey("nd_focus_guard")
        val KEY_BODY_DOUBLING = booleanPreferencesKey("nd_body_doubling")
        val KEY_CHECK_IN_INTERVAL = intPreferencesKey("nd_check_in_interval_minutes")
        val KEY_COMPLETION_ANIMATIONS = booleanPreferencesKey("nd_completion_animations")
        val KEY_STREAK_CELEBRATIONS = booleanPreferencesKey("nd_streak_celebrations")
        val KEY_SHOW_PROGRESS_BARS = booleanPreferencesKey("nd_show_progress_bars")
        val KEY_FORGIVENESS_STREAKS = booleanPreferencesKey("nd_forgiveness_streaks")
    }

    // region Flow ---------------------------------------------------------------

    val ndPreferencesFlow: Flow<NdPreferences> = dataStore.data.map { prefs ->
        NdPreferences(
            adhdModeEnabled = prefs[KEY_ADHD_MODE] ?: false,
            calmModeEnabled = prefs[KEY_CALM_MODE] ?: false,
            reduceAnimations = prefs[KEY_REDUCE_ANIMATIONS] ?: false,
            mutedColorPalette = prefs[KEY_MUTED_COLOR_PALETTE] ?: false,
            quietMode = prefs[KEY_QUIET_MODE] ?: false,
            reduceHaptics = prefs[KEY_REDUCE_HAPTICS] ?: false,
            softContrast = prefs[KEY_SOFT_CONTRAST] ?: false,
            taskDecompositionEnabled = prefs[KEY_TASK_DECOMPOSITION] ?: false,
            focusGuardEnabled = prefs[KEY_FOCUS_GUARD] ?: false,
            bodyDoublingEnabled = prefs[KEY_BODY_DOUBLING] ?: false,
            checkInIntervalMinutes = (prefs[KEY_CHECK_IN_INTERVAL] ?: 25).coerceIn(10, 60),
            completionAnimations = prefs[KEY_COMPLETION_ANIMATIONS] ?: false,
            streakCelebrations = prefs[KEY_STREAK_CELEBRATIONS] ?: false,
            showProgressBars = prefs[KEY_SHOW_PROGRESS_BARS] ?: false,
            forgivenessStreaks = prefs[KEY_FORGIVENESS_STREAKS] ?: false
        )
    }

    // endregion

    // region Mode toggles -------------------------------------------------------

    /**
     * Enables or disables ADHD Mode. When enabled, flips ALL ADHD sub-settings to
     * true. When disabled, flips ALL ADHD sub-settings to false. Does not affect
     * Calm Mode settings.
     */
    suspend fun setAdhdMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ADHD_MODE] = enabled
            prefs[KEY_TASK_DECOMPOSITION] = enabled
            prefs[KEY_FOCUS_GUARD] = enabled
            prefs[KEY_BODY_DOUBLING] = enabled
            prefs[KEY_COMPLETION_ANIMATIONS] = enabled
            prefs[KEY_STREAK_CELEBRATIONS] = enabled
            prefs[KEY_SHOW_PROGRESS_BARS] = enabled
            prefs[KEY_FORGIVENESS_STREAKS] = enabled
        }
    }

    /**
     * Enables or disables Calm Mode. When enabled, flips ALL Calm sub-settings to
     * true. When disabled, flips ALL Calm sub-settings to false. Does not affect
     * ADHD Mode settings.
     */
    suspend fun setCalmMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CALM_MODE] = enabled
            prefs[KEY_REDUCE_ANIMATIONS] = enabled
            prefs[KEY_MUTED_COLOR_PALETTE] = enabled
            prefs[KEY_QUIET_MODE] = enabled
            prefs[KEY_REDUCE_HAPTICS] = enabled
            prefs[KEY_SOFT_CONTRAST] = enabled
        }
    }

    // endregion

    // region Individual sub-setting setters -------------------------------------

    suspend fun setReduceAnimations(enabled: Boolean) {
        dataStore.edit { it[KEY_REDUCE_ANIMATIONS] = enabled }
    }

    suspend fun setMutedColorPalette(enabled: Boolean) {
        dataStore.edit { it[KEY_MUTED_COLOR_PALETTE] = enabled }
    }

    suspend fun setQuietMode(enabled: Boolean) {
        dataStore.edit { it[KEY_QUIET_MODE] = enabled }
    }

    suspend fun setReduceHaptics(enabled: Boolean) {
        dataStore.edit { it[KEY_REDUCE_HAPTICS] = enabled }
    }

    suspend fun setSoftContrast(enabled: Boolean) {
        dataStore.edit { it[KEY_SOFT_CONTRAST] = enabled }
    }

    suspend fun setTaskDecomposition(enabled: Boolean) {
        dataStore.edit { it[KEY_TASK_DECOMPOSITION] = enabled }
    }

    suspend fun setFocusGuard(enabled: Boolean) {
        dataStore.edit { it[KEY_FOCUS_GUARD] = enabled }
    }

    suspend fun setBodyDoubling(enabled: Boolean) {
        dataStore.edit { it[KEY_BODY_DOUBLING] = enabled }
    }

    suspend fun setCheckInIntervalMinutes(minutes: Int) {
        dataStore.edit { it[KEY_CHECK_IN_INTERVAL] = minutes.coerceIn(10, 60) }
    }

    suspend fun setCompletionAnimations(enabled: Boolean) {
        dataStore.edit { it[KEY_COMPLETION_ANIMATIONS] = enabled }
    }

    suspend fun setStreakCelebrations(enabled: Boolean) {
        dataStore.edit { it[KEY_STREAK_CELEBRATIONS] = enabled }
    }

    suspend fun setShowProgressBars(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_PROGRESS_BARS] = enabled }
    }

    suspend fun setForgivenessStreaks(enabled: Boolean) {
        dataStore.edit { it[KEY_FORGIVENESS_STREAKS] = enabled }
    }

    // endregion

    // region Generic setter -----------------------------------------------------

    /**
     * Updates a single ND preference by key name. Intended for use by the Settings
     * UI where toggle keys are passed dynamically.
     *
     * @throws IllegalArgumentException if [key] is not a recognized ND preference key.
     */
    suspend fun updateNdPreference(key: String, value: Any) {
        when (key) {
            "adhd_mode_enabled" -> setAdhdMode(value as Boolean)
            "calm_mode_enabled" -> setCalmMode(value as Boolean)
            "reduce_animations" -> setReduceAnimations(value as Boolean)
            "muted_color_palette" -> setMutedColorPalette(value as Boolean)
            "quiet_mode" -> setQuietMode(value as Boolean)
            "reduce_haptics" -> setReduceHaptics(value as Boolean)
            "soft_contrast" -> setSoftContrast(value as Boolean)
            "task_decomposition" -> setTaskDecomposition(value as Boolean)
            "focus_guard" -> setFocusGuard(value as Boolean)
            "body_doubling" -> setBodyDoubling(value as Boolean)
            "check_in_interval_minutes" -> setCheckInIntervalMinutes(value as Int)
            "completion_animations" -> setCompletionAnimations(value as Boolean)
            "streak_celebrations" -> setStreakCelebrations(value as Boolean)
            "show_progress_bars" -> setShowProgressBars(value as Boolean)
            "forgiveness_streaks" -> setForgivenessStreaks(value as Boolean)
            else -> throw IllegalArgumentException("Unknown ND preference key: $key")
        }
    }

    // endregion
}
