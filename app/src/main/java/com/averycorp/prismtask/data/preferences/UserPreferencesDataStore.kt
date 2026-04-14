package com.averycorp.prismtask.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.averycorp.prismtask.domain.model.AutoDueDate
import com.averycorp.prismtask.domain.model.StartOfWeek
import com.averycorp.prismtask.domain.model.SwipeAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Appearance/display preferences used by v1.3.0 customizability features.
 */
data class AppearancePrefs(
    val compactMode: Boolean = false,
    val showTaskCardBorders: Boolean = true,
    val cardCornerRadius: Int = 12
)

/**
 * Swipe gesture preferences for task cards.
 */
data class SwipePrefs(
    val right: SwipeAction = SwipeAction.COMPLETE,
    val left: SwipeAction = SwipeAction.DELETE
)

/**
 * Default values applied when creating a new task.
 */
data class TaskDefaults(
    val defaultPriority: Int = 0,
    val defaultReminderOffset: Long = -1L,
    val defaultProjectId: Long? = null,
    val startOfWeek: StartOfWeek = StartOfWeek.MONDAY,
    val defaultDuration: Int? = null,
    val autoSetDueDate: AutoDueDate = AutoDueDate.NONE,
    val smartDefaultsEnabled: Boolean = false
)

/**
 * Preferences for the quick-add bar.
 */
data class QuickAddPrefs(
    val showConfirmation: Boolean = true,
    val autoAssignProject: Boolean = false
)

/**
 * Forgiveness-first streak preferences (v1.4.0 V5).
 *
 * When [enabled], [StreakCalculator.calculateResilientStreak] tolerates up to
 * [allowedMisses] missed days within a rolling [gracePeriodDays] window before
 * resetting the streak. When disabled, streak calculation reverts to classic
 * strict behavior (a single miss hard-resets the run).
 */
data class ForgivenessPrefs(
    val enabled: Boolean = true,
    val gracePeriodDays: Int = 7,
    val allowedMisses: Int = 1
)

/**
 * Work-Life Balance Engine preferences (v1.4.0 V1).
 *
 * Target ratios are stored as Int percentages (0..100) and should sum to 100.
 * [autoClassifyEnabled] controls whether the [com.averycorp.prismtask.domain.usecase.LifeCategoryClassifier]
 * runs on task creation when the user hasn't picked a category manually.
 * [showBalanceBar] toggles the Today screen balance bar visibility.
 */
data class WorkLifeBalancePrefs(
    val workTarget: Int = 40,
    val personalTarget: Int = 25,
    val selfCareTarget: Int = 20,
    val healthTarget: Int = 15,
    val autoClassifyEnabled: Boolean = true,
    val showBalanceBar: Boolean = true,
    val overloadThresholdPct: Int = 10
) {
    /** Whether the four target percentages sum to 100 (allowing ±1 for rounding). */
    fun isValid(): Boolean {
        val sum = workTarget + personalTarget + selfCareTarget + healthTarget
        return sum in 99..101
    }
}

/**
 * Aggregated snapshot of all user preferences for a single point in time.
 * Used primarily by DataExporter/DataImporter and by the Settings screen.
 */
data class UserPreferencesSnapshot(
    val appearance: AppearancePrefs = AppearancePrefs(),
    val swipe: SwipePrefs = SwipePrefs(),
    val taskDefaults: TaskDefaults = TaskDefaults(),
    val quickAdd: QuickAddPrefs = QuickAddPrefs(),
    val workLifeBalance: WorkLifeBalancePrefs = WorkLifeBalancePrefs()
)

/**
 * Centralized DataStore for v1.3.0 customization preferences. This sits alongside the
 * existing preference classes (ThemePreferences, TaskBehaviorPreferences, etc.) and
 * holds only the new keys introduced by the customizability track.
 *
 * Takes a [DataStore] in its constructor so it can be unit-tested without an Android
 * Context; production wiring lives in
 * [com.averycorp.prismtask.di.PreferencesModule].
 */
class UserPreferencesDataStore(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // Appearance
        val KEY_COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val KEY_SHOW_CARD_BORDERS = booleanPreferencesKey("show_card_borders")
        val KEY_CARD_CORNER_RADIUS = intPreferencesKey("card_corner_radius")

        // Swipe actions
        val KEY_SWIPE_RIGHT = stringPreferencesKey("swipe_right_action")
        val KEY_SWIPE_LEFT = stringPreferencesKey("swipe_left_action")

        // Task defaults
        val KEY_DEFAULT_PRIORITY = intPreferencesKey("default_priority")
        val KEY_DEFAULT_REMINDER_OFFSET = longPreferencesKey("default_reminder_offset")
        val KEY_DEFAULT_PROJECT_ID = longPreferencesKey("default_project_id")
        val KEY_START_OF_WEEK = stringPreferencesKey("start_of_week")
        val KEY_DEFAULT_DURATION = intPreferencesKey("default_duration")
        val KEY_AUTO_SET_DUE_DATE = stringPreferencesKey("auto_set_due_date")
        val KEY_SMART_DEFAULTS = booleanPreferencesKey("smart_defaults_enabled")

        // Quick-add
        val KEY_QUICK_ADD_CONFIRM = booleanPreferencesKey("quick_add_show_confirmation")
        val KEY_QUICK_ADD_AUTO_PROJECT = booleanPreferencesKey("quick_add_auto_assign_project")

        // Task menu actions config (JSON-encoded)
        val KEY_TASK_MENU_ACTIONS = stringPreferencesKey("task_menu_actions_json")

        // Task card display config (JSON-encoded)
        val KEY_TASK_CARD_DISPLAY = stringPreferencesKey("task_card_display_json")

        // Forgiveness-first streaks (v1.4.0 V5)
        val KEY_FORGIVENESS_ENABLED = booleanPreferencesKey("forgiveness_enabled")
        val KEY_FORGIVENESS_GRACE_DAYS = intPreferencesKey("forgiveness_grace_days")
        val KEY_FORGIVENESS_ALLOWED_MISSES = intPreferencesKey("forgiveness_allowed_misses")

        // Work-Life Balance (v1.4.0 V1)
        val KEY_WLB_WORK_TARGET = intPreferencesKey("wlb_work_target")
        val KEY_WLB_PERSONAL_TARGET = intPreferencesKey("wlb_personal_target")
        val KEY_WLB_SELFCARE_TARGET = intPreferencesKey("wlb_selfcare_target")
        val KEY_WLB_HEALTH_TARGET = intPreferencesKey("wlb_health_target")
        val KEY_WLB_AUTO_CLASSIFY = booleanPreferencesKey("wlb_auto_classify")
        val KEY_WLB_SHOW_BAR = booleanPreferencesKey("wlb_show_bar")
        val KEY_WLB_OVERLOAD_THRESHOLD = intPreferencesKey("wlb_overload_threshold")

        private const val DEFAULT_PROJECT_NULL_SENTINEL: Long = -1L
    }

    // region Flows ---------------------------------------------------------

    val appearanceFlow: Flow<AppearancePrefs> = dataStore.data.map { prefs ->
        AppearancePrefs(
            compactMode = prefs[KEY_COMPACT_MODE] ?: false,
            showTaskCardBorders = prefs[KEY_SHOW_CARD_BORDERS] ?: true,
            cardCornerRadius = (prefs[KEY_CARD_CORNER_RADIUS] ?: 12).coerceIn(0, 24)
        )
    }

    val swipeFlow: Flow<SwipePrefs> = dataStore.data.map { prefs ->
        SwipePrefs(
            right = SwipeAction.fromName(prefs[KEY_SWIPE_RIGHT]),
            left = SwipeAction.fromName(prefs[KEY_SWIPE_LEFT] ?: SwipeAction.DELETE.name)
        )
    }

    val taskDefaultsFlow: Flow<TaskDefaults> = dataStore.data.map { prefs ->
        val rawProjectId = prefs[KEY_DEFAULT_PROJECT_ID]
        TaskDefaults(
            defaultPriority = (prefs[KEY_DEFAULT_PRIORITY] ?: 0).coerceIn(0, 4),
            defaultReminderOffset = prefs[KEY_DEFAULT_REMINDER_OFFSET] ?: -1L,
            defaultProjectId = if (rawProjectId == null || rawProjectId == DEFAULT_PROJECT_NULL_SENTINEL) null else rawProjectId,
            startOfWeek = StartOfWeek.fromName(prefs[KEY_START_OF_WEEK]),
            defaultDuration = prefs[KEY_DEFAULT_DURATION]?.takeIf { it > 0 },
            autoSetDueDate = AutoDueDate.fromName(prefs[KEY_AUTO_SET_DUE_DATE]),
            smartDefaultsEnabled = prefs[KEY_SMART_DEFAULTS] ?: false
        )
    }

    val quickAddFlow: Flow<QuickAddPrefs> = dataStore.data.map { prefs ->
        QuickAddPrefs(
            showConfirmation = prefs[KEY_QUICK_ADD_CONFIRM] ?: true,
            autoAssignProject = prefs[KEY_QUICK_ADD_AUTO_PROJECT] ?: false
        )
    }

    val taskMenuActionsFlow: Flow<List<com.averycorp.prismtask.domain.model.TaskMenuAction>> =
        dataStore.data.map { prefs ->
            val json = prefs[KEY_TASK_MENU_ACTIONS]
            if (json.isNullOrBlank()) {
                com.averycorp.prismtask.domain.model.TaskMenuAction
                    .defaults()
            } else {
                try {
                    val listType = com.google.gson.reflect.TypeToken
                        .getParameterized(List::class.java, com.averycorp.prismtask.domain.model.TaskMenuAction::class.java)
                        .type
                    val parsed: List<com.averycorp.prismtask.domain.model.TaskMenuAction> =
                        com.google.gson
                            .Gson()
                            .fromJson(json, listType)
                    com.averycorp.prismtask.domain.model.TaskMenuAction
                        .mergeWithDefaults(parsed)
                } catch (_: Exception) {
                    com.averycorp.prismtask.domain.model.TaskMenuAction
                        .defaults()
                }
            }
        }

    suspend fun setTaskMenuActions(actions: List<com.averycorp.prismtask.domain.model.TaskMenuAction>) {
        val json = com.google.gson
            .Gson()
            .toJson(actions)
        dataStore.edit { it[KEY_TASK_MENU_ACTIONS] = json }
    }

    val taskCardDisplayFlow: Flow<com.averycorp.prismtask.domain.model.TaskCardDisplayConfig> =
        dataStore.data.map { prefs ->
            val json = prefs[KEY_TASK_CARD_DISPLAY]
            if (json.isNullOrBlank()) {
                com.averycorp.prismtask.domain.model
                    .TaskCardDisplayConfig()
            } else {
                try {
                    com.google.gson
                        .Gson()
                        .fromJson(json, com.averycorp.prismtask.domain.model.TaskCardDisplayConfig::class.java)
                        ?.withClampedTagLimit()
                        ?: com.averycorp.prismtask.domain.model
                            .TaskCardDisplayConfig()
                } catch (_: Exception) {
                    com.averycorp.prismtask.domain.model
                        .TaskCardDisplayConfig()
                }
            }
        }

    suspend fun setTaskCardDisplay(config: com.averycorp.prismtask.domain.model.TaskCardDisplayConfig) {
        val clamped = config.withClampedTagLimit()
        val json = com.google.gson
            .Gson()
            .toJson(clamped)
        dataStore.edit { it[KEY_TASK_CARD_DISPLAY] = json }
    }

    val forgivenessFlow: Flow<ForgivenessPrefs> = dataStore.data.map { prefs ->
        ForgivenessPrefs(
            enabled = prefs[KEY_FORGIVENESS_ENABLED] ?: true,
            gracePeriodDays = (prefs[KEY_FORGIVENESS_GRACE_DAYS] ?: 7).coerceIn(1, 30),
            allowedMisses = (prefs[KEY_FORGIVENESS_ALLOWED_MISSES] ?: 1).coerceIn(0, 5)
        )
    }

    suspend fun setForgivenessPrefs(prefs: ForgivenessPrefs) {
        dataStore.edit {
            it[KEY_FORGIVENESS_ENABLED] = prefs.enabled
            it[KEY_FORGIVENESS_GRACE_DAYS] = prefs.gracePeriodDays.coerceIn(1, 30)
            it[KEY_FORGIVENESS_ALLOWED_MISSES] = prefs.allowedMisses.coerceIn(0, 5)
        }
    }

    val workLifeBalanceFlow: Flow<WorkLifeBalancePrefs> = dataStore.data.map { prefs ->
        WorkLifeBalancePrefs(
            workTarget = (prefs[KEY_WLB_WORK_TARGET] ?: 40).coerceIn(0, 100),
            personalTarget = (prefs[KEY_WLB_PERSONAL_TARGET] ?: 25).coerceIn(0, 100),
            selfCareTarget = (prefs[KEY_WLB_SELFCARE_TARGET] ?: 20).coerceIn(0, 100),
            healthTarget = (prefs[KEY_WLB_HEALTH_TARGET] ?: 15).coerceIn(0, 100),
            autoClassifyEnabled = prefs[KEY_WLB_AUTO_CLASSIFY] ?: true,
            showBalanceBar = prefs[KEY_WLB_SHOW_BAR] ?: true,
            overloadThresholdPct = (prefs[KEY_WLB_OVERLOAD_THRESHOLD] ?: 10).coerceIn(5, 25)
        )
    }

    /** Combined flow emitting the full preferences bundle. */
    val allFlow: Flow<UserPreferencesSnapshot> = combine(
        appearanceFlow,
        swipeFlow,
        taskDefaultsFlow,
        quickAddFlow,
        workLifeBalanceFlow
    ) { appearance, swipe, defaults, quickAdd, wlb ->
        UserPreferencesSnapshot(appearance, swipe, defaults, quickAdd, wlb)
    }

    // endregion

    // region Setters -------------------------------------------------------

    suspend fun setAppearance(prefs: AppearancePrefs) {
        dataStore.edit {
            it[KEY_COMPACT_MODE] = prefs.compactMode
            it[KEY_SHOW_CARD_BORDERS] = prefs.showTaskCardBorders
            it[KEY_CARD_CORNER_RADIUS] = prefs.cardCornerRadius.coerceIn(0, 24)
        }
    }

    suspend fun setCompactMode(enabled: Boolean) {
        dataStore.edit { it[KEY_COMPACT_MODE] = enabled }
    }

    suspend fun setShowCardBorders(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_CARD_BORDERS] = enabled }
    }

    suspend fun setCardCornerRadius(radius: Int) {
        dataStore.edit { it[KEY_CARD_CORNER_RADIUS] = radius.coerceIn(0, 24) }
    }

    suspend fun setSwipe(prefs: SwipePrefs) {
        dataStore.edit {
            it[KEY_SWIPE_RIGHT] = prefs.right.name
            it[KEY_SWIPE_LEFT] = prefs.left.name
        }
    }

    suspend fun setSwipeRight(action: SwipeAction) {
        dataStore.edit { it[KEY_SWIPE_RIGHT] = action.name }
    }

    suspend fun setSwipeLeft(action: SwipeAction) {
        dataStore.edit { it[KEY_SWIPE_LEFT] = action.name }
    }

    suspend fun setTaskDefaults(defaults: TaskDefaults) {
        dataStore.edit {
            it[KEY_DEFAULT_PRIORITY] = defaults.defaultPriority.coerceIn(0, 4)
            it[KEY_DEFAULT_REMINDER_OFFSET] = defaults.defaultReminderOffset
            it[KEY_DEFAULT_PROJECT_ID] = defaults.defaultProjectId ?: DEFAULT_PROJECT_NULL_SENTINEL
            it[KEY_START_OF_WEEK] = defaults.startOfWeek.name
            it[KEY_DEFAULT_DURATION] = defaults.defaultDuration ?: -1
            it[KEY_AUTO_SET_DUE_DATE] = defaults.autoSetDueDate.name
            it[KEY_SMART_DEFAULTS] = defaults.smartDefaultsEnabled
        }
    }

    suspend fun setSmartDefaultsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SMART_DEFAULTS] = enabled }
    }

    suspend fun setQuickAdd(prefs: QuickAddPrefs) {
        dataStore.edit {
            it[KEY_QUICK_ADD_CONFIRM] = prefs.showConfirmation
            it[KEY_QUICK_ADD_AUTO_PROJECT] = prefs.autoAssignProject
        }
    }

    /**
     * Save a new Work-Life Balance configuration. Values that don't sum to 100
     * are stored as-is; the UI is responsible for validation. The overload
     * threshold is clamped to a sane range.
     */
    suspend fun setWorkLifeBalance(prefs: WorkLifeBalancePrefs) {
        dataStore.edit {
            it[KEY_WLB_WORK_TARGET] = prefs.workTarget.coerceIn(0, 100)
            it[KEY_WLB_PERSONAL_TARGET] = prefs.personalTarget.coerceIn(0, 100)
            it[KEY_WLB_SELFCARE_TARGET] = prefs.selfCareTarget.coerceIn(0, 100)
            it[KEY_WLB_HEALTH_TARGET] = prefs.healthTarget.coerceIn(0, 100)
            it[KEY_WLB_AUTO_CLASSIFY] = prefs.autoClassifyEnabled
            it[KEY_WLB_SHOW_BAR] = prefs.showBalanceBar
            it[KEY_WLB_OVERLOAD_THRESHOLD] = prefs.overloadThresholdPct.coerceIn(5, 25)
        }
    }

    suspend fun setAutoClassifyEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WLB_AUTO_CLASSIFY] = enabled }
    }

    suspend fun setShowBalanceBar(enabled: Boolean) {
        dataStore.edit { it[KEY_WLB_SHOW_BAR] = enabled }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    // endregion
}
