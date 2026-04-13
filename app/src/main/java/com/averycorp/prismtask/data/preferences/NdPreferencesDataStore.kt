package com.averycorp.prismtask.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * - When [setFocusReleaseMode] is called with `true`: all F&R sub-settings flip ON.
 * - When a mode is toggled OFF: all its sub-settings flip OFF. The three modes have
 *   zero overlap so toggling one off never affects the others.
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

        // Focus & Release Mode toggle
        val KEY_FOCUS_RELEASE_MODE = booleanPreferencesKey("nd_focus_release_mode_enabled")

        // Good Enough Timers
        val KEY_GOOD_ENOUGH_TIMERS = booleanPreferencesKey("nd_good_enough_timers_enabled")
        val KEY_DEFAULT_GOOD_ENOUGH_MINUTES = intPreferencesKey("nd_default_good_enough_minutes")
        val KEY_GOOD_ENOUGH_ESCALATION = stringPreferencesKey("nd_good_enough_escalation")

        // Anti-Rework Guards
        val KEY_ANTI_REWORK = booleanPreferencesKey("nd_anti_rework_enabled")
        val KEY_SOFT_WARNING = booleanPreferencesKey("nd_soft_warning_enabled")
        val KEY_COOLING_OFF = booleanPreferencesKey("nd_cooling_off_enabled")
        val KEY_COOLING_OFF_MINUTES = intPreferencesKey("nd_cooling_off_minutes")
        val KEY_REVISION_COUNTER = booleanPreferencesKey("nd_revision_counter_enabled")
        val KEY_MAX_REVISIONS = intPreferencesKey("nd_max_revisions")

        // Ship-It Celebrations
        val KEY_SHIP_IT_CELEBRATIONS = booleanPreferencesKey("nd_ship_it_celebrations_enabled")
        val KEY_CELEBRATION_INTENSITY = stringPreferencesKey("nd_celebration_intensity")

        // Decision Paralysis Breakers
        val KEY_PARALYSIS_BREAKERS = booleanPreferencesKey("nd_paralysis_breakers_enabled")
        val KEY_AUTO_SUGGEST = booleanPreferencesKey("nd_auto_suggest_enabled")
        val KEY_SIMPLIFY_CHOICES = booleanPreferencesKey("nd_simplify_choices_enabled")
        val KEY_STUCK_DETECTION_MINUTES = intPreferencesKey("nd_stuck_detection_minutes")
    }

    // region Flow ---------------------------------------------------------------

    val ndPreferencesFlow: Flow<NdPreferences> = dataStore.data.map { prefs ->
        NdPreferences(
            adhdModeEnabled = prefs[KEY_ADHD_MODE] ?: false,
            calmModeEnabled = prefs[KEY_CALM_MODE] ?: false,
            focusReleaseModeEnabled = prefs[KEY_FOCUS_RELEASE_MODE] ?: false,
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
            forgivenessStreaks = prefs[KEY_FORGIVENESS_STREAKS] ?: false,
            goodEnoughTimersEnabled = prefs[KEY_GOOD_ENOUGH_TIMERS] ?: true,
            defaultGoodEnoughMinutes = (prefs[KEY_DEFAULT_GOOD_ENOUGH_MINUTES] ?: 30).coerceIn(5, 120),
            goodEnoughEscalation = prefs[KEY_GOOD_ENOUGH_ESCALATION]
                ?.let { runCatching { GoodEnoughEscalation.valueOf(it) }.getOrNull() }
                ?: GoodEnoughEscalation.NUDGE,
            antiReworkEnabled = prefs[KEY_ANTI_REWORK] ?: true,
            softWarningEnabled = prefs[KEY_SOFT_WARNING] ?: true,
            coolingOffEnabled = prefs[KEY_COOLING_OFF] ?: false,
            coolingOffMinutes = (prefs[KEY_COOLING_OFF_MINUTES] ?: 30).coerceIn(15, 120),
            revisionCounterEnabled = prefs[KEY_REVISION_COUNTER] ?: false,
            maxRevisions = (prefs[KEY_MAX_REVISIONS] ?: 3).coerceIn(1, 10),
            shipItCelebrationsEnabled = prefs[KEY_SHIP_IT_CELEBRATIONS] ?: true,
            celebrationIntensity = prefs[KEY_CELEBRATION_INTENSITY]
                ?.let { runCatching { CelebrationIntensity.valueOf(it) }.getOrNull() }
                ?: CelebrationIntensity.MEDIUM,
            paralysisBreakersEnabled = prefs[KEY_PARALYSIS_BREAKERS] ?: true,
            autoSuggestEnabled = prefs[KEY_AUTO_SUGGEST] ?: true,
            simplifyChoicesEnabled = prefs[KEY_SIMPLIFY_CHOICES] ?: true,
            stuckDetectionMinutes = (prefs[KEY_STUCK_DETECTION_MINUTES] ?: 5).coerceIn(1, 15)
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
     * ADHD Mode or Focus & Release Mode settings.
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

    /**
     * Enables or disables Focus & Release Mode. When enabled, flips ALL F&R
     * sub-settings to their default-on values. When disabled, flips all F&R
     * sub-settings off. Does not affect ADHD Mode or Calm Mode settings.
     */
    suspend fun setFocusReleaseMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FOCUS_RELEASE_MODE] = enabled
            prefs[KEY_GOOD_ENOUGH_TIMERS] = enabled
            prefs[KEY_ANTI_REWORK] = enabled
            prefs[KEY_SOFT_WARNING] = enabled
            prefs[KEY_COOLING_OFF] = false // off by default even when F&R enabled
            prefs[KEY_REVISION_COUNTER] = false // off by default even when F&R enabled
            prefs[KEY_SHIP_IT_CELEBRATIONS] = enabled
            prefs[KEY_PARALYSIS_BREAKERS] = enabled
            prefs[KEY_AUTO_SUGGEST] = enabled
            prefs[KEY_SIMPLIFY_CHOICES] = enabled
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

    // Focus & Release Mode individual sub-setting setters

    suspend fun setGoodEnoughTimersEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_GOOD_ENOUGH_TIMERS] = enabled }
    }

    suspend fun setDefaultGoodEnoughMinutes(minutes: Int) {
        dataStore.edit { it[KEY_DEFAULT_GOOD_ENOUGH_MINUTES] = minutes.coerceIn(5, 120) }
    }

    suspend fun setGoodEnoughEscalation(escalation: GoodEnoughEscalation) {
        dataStore.edit { it[KEY_GOOD_ENOUGH_ESCALATION] = escalation.name }
    }

    suspend fun setAntiReworkEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ANTI_REWORK] = enabled }
    }

    suspend fun setSoftWarningEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SOFT_WARNING] = enabled }
    }

    suspend fun setCoolingOffEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_COOLING_OFF] = enabled }
    }

    suspend fun setCoolingOffMinutes(minutes: Int) {
        dataStore.edit { it[KEY_COOLING_OFF_MINUTES] = minutes.coerceIn(15, 120) }
    }

    suspend fun setRevisionCounterEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_REVISION_COUNTER] = enabled }
    }

    suspend fun setMaxRevisions(max: Int) {
        dataStore.edit { it[KEY_MAX_REVISIONS] = max.coerceIn(1, 10) }
    }

    suspend fun setShipItCelebrationsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SHIP_IT_CELEBRATIONS] = enabled }
    }

    suspend fun setCelebrationIntensity(intensity: CelebrationIntensity) {
        dataStore.edit { it[KEY_CELEBRATION_INTENSITY] = intensity.name }
    }

    suspend fun setParalysisBreakersEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PARALYSIS_BREAKERS] = enabled }
    }

    suspend fun setAutoSuggestEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_SUGGEST] = enabled }
    }

    suspend fun setSimplifyChoicesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SIMPLIFY_CHOICES] = enabled }
    }

    suspend fun setStuckDetectionMinutes(minutes: Int) {
        dataStore.edit { it[KEY_STUCK_DETECTION_MINUTES] = minutes.coerceIn(1, 15) }
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
            "focus_release_mode_enabled" -> setFocusReleaseMode(value as Boolean)
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
            "good_enough_timers_enabled" -> setGoodEnoughTimersEnabled(value as Boolean)
            "default_good_enough_minutes" -> setDefaultGoodEnoughMinutes(value as Int)
            "good_enough_escalation" -> setGoodEnoughEscalation(GoodEnoughEscalation.valueOf(value as String))
            "anti_rework_enabled" -> setAntiReworkEnabled(value as Boolean)
            "soft_warning_enabled" -> setSoftWarningEnabled(value as Boolean)
            "cooling_off_enabled" -> setCoolingOffEnabled(value as Boolean)
            "cooling_off_minutes" -> setCoolingOffMinutes(value as Int)
            "revision_counter_enabled" -> setRevisionCounterEnabled(value as Boolean)
            "max_revisions" -> setMaxRevisions(value as Int)
            "ship_it_celebrations_enabled" -> setShipItCelebrationsEnabled(value as Boolean)
            "celebration_intensity" -> setCelebrationIntensity(CelebrationIntensity.valueOf(value as String))
            "paralysis_breakers_enabled" -> setParalysisBreakersEnabled(value as Boolean)
            "auto_suggest_enabled" -> setAutoSuggestEnabled(value as Boolean)
            "simplify_choices_enabled" -> setSimplifyChoicesEnabled(value as Boolean)
            "stuck_detection_minutes" -> setStuckDetectionMinutes(value as Int)
            else -> throw IllegalArgumentException("Unknown ND preference key: $key")
        }
    }

    // endregion
}
