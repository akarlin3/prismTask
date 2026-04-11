package com.averycorp.prismtask.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Per-instance configuration for home screen widgets.
 *
 * Keys are namespaced as `widget_{appWidgetId}_{key}` so each placed widget
 * has its own isolated settings. When a widget is removed, call
 * [clearForWidget] from the widget receiver's onDeleted override to avoid
 * leaking orphaned config.
 *
 * Added in v1.3.0 (P10).
 */
private val Context.widgetConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_config")

object WidgetConfigDataStore {

    // ---- Today widget ----
    data class TodayConfig(
        val showProgress: Boolean = true,
        val showTaskList: Boolean = true,
        val showHabitSummary: Boolean = true,
        val maxTasks: Int = 5,
        val showOverdueBadge: Boolean = true,
        val backgroundOpacityPercent: Int = 100
    )

    fun todayConfigFlow(context: Context, appWidgetId: Int): Flow<TodayConfig> =
        context.widgetConfigDataStore.data.map { prefs ->
            TodayConfig(
                showProgress = prefs[booleanPreferencesKey("widget_${appWidgetId}_show_progress")] ?: true,
                showTaskList = prefs[booleanPreferencesKey("widget_${appWidgetId}_show_task_list")] ?: true,
                showHabitSummary = prefs[booleanPreferencesKey("widget_${appWidgetId}_show_habit_summary")] ?: true,
                maxTasks = prefs[intPreferencesKey("widget_${appWidgetId}_max_tasks")]?.coerceIn(MAX_TASKS_RANGE) ?: 5,
                showOverdueBadge = prefs[booleanPreferencesKey("widget_${appWidgetId}_show_overdue_badge")] ?: true,
                backgroundOpacityPercent = prefs[intPreferencesKey("widget_${appWidgetId}_bg_opacity")]
                    ?.coerceIn(60, 100) ?: 100
            )
        }

    suspend fun setTodayConfig(context: Context, appWidgetId: Int, config: TodayConfig) {
        context.widgetConfigDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("widget_${appWidgetId}_show_progress")] = config.showProgress
            prefs[booleanPreferencesKey("widget_${appWidgetId}_show_task_list")] = config.showTaskList
            prefs[booleanPreferencesKey("widget_${appWidgetId}_show_habit_summary")] = config.showHabitSummary
            prefs[intPreferencesKey("widget_${appWidgetId}_max_tasks")] = config.maxTasks.coerceIn(MAX_TASKS_RANGE)
            prefs[booleanPreferencesKey("widget_${appWidgetId}_show_overdue_badge")] = config.showOverdueBadge
            prefs[intPreferencesKey("widget_${appWidgetId}_bg_opacity")] = config.backgroundOpacityPercent.coerceIn(60, 100)
        }
    }

    // ---- Habit streak widget ----
    data class HabitStreakConfig(
        val selectedHabitIds: List<Long> = emptyList(),
        val showStreakCount: Boolean = true,
        val layoutGrid: Boolean = false
    )

    fun habitStreakConfigFlow(context: Context, appWidgetId: Int): Flow<HabitStreakConfig> =
        context.widgetConfigDataStore.data.map { prefs ->
            val csv = prefs[stringPreferencesKey("widget_${appWidgetId}_habit_ids")] ?: ""
            HabitStreakConfig(
                selectedHabitIds = csv.split(",").mapNotNull { it.trim().toLongOrNull() }.take(6),
                showStreakCount = prefs[booleanPreferencesKey("widget_${appWidgetId}_show_streak_count")] ?: true,
                layoutGrid = prefs[booleanPreferencesKey("widget_${appWidgetId}_layout_grid")] ?: false
            )
        }

    suspend fun setHabitStreakConfig(context: Context, appWidgetId: Int, config: HabitStreakConfig) {
        context.widgetConfigDataStore.edit { prefs ->
            prefs[stringPreferencesKey("widget_${appWidgetId}_habit_ids")] =
                config.selectedHabitIds.take(6).joinToString(",")
            prefs[booleanPreferencesKey("widget_${appWidgetId}_show_streak_count")] = config.showStreakCount
            prefs[booleanPreferencesKey("widget_${appWidgetId}_layout_grid")] = config.layoutGrid
        }
    }

    // ---- Quick add widget ----
    data class QuickAddConfig(
        val placeholder: String = "Add a task...",
        val defaultProjectId: Long? = null
    )

    fun quickAddConfigFlow(context: Context, appWidgetId: Int): Flow<QuickAddConfig> =
        context.widgetConfigDataStore.data.map { prefs ->
            QuickAddConfig(
                placeholder = prefs[stringPreferencesKey("widget_${appWidgetId}_placeholder")] ?: "Add a task...",
                defaultProjectId = prefs[longPreferencesKey("widget_${appWidgetId}_default_project")]
                    ?.takeIf { it >= 0 }
            )
        }

    suspend fun setQuickAddConfig(context: Context, appWidgetId: Int, config: QuickAddConfig) {
        context.widgetConfigDataStore.edit { prefs ->
            prefs[stringPreferencesKey("widget_${appWidgetId}_placeholder")] = config.placeholder
            prefs[longPreferencesKey("widget_${appWidgetId}_default_project")] = config.defaultProjectId ?: -1L
        }
    }

    /** Removes every key whose name references the given appWidgetId. */
    suspend fun clearForWidget(context: Context, appWidgetId: Int) {
        context.widgetConfigDataStore.edit { prefs ->
            val prefix = "widget_${appWidgetId}_"
            val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith(prefix) }
            keysToRemove.forEach { prefs.remove(it) }
        }
    }

    /** Snapshot helper used by the widget update path. */
    suspend fun snapshotTodayConfig(context: Context, appWidgetId: Int): TodayConfig =
        todayConfigFlow(context, appWidgetId).first()

    suspend fun snapshotHabitStreakConfig(context: Context, appWidgetId: Int): HabitStreakConfig =
        habitStreakConfigFlow(context, appWidgetId).first()

    suspend fun snapshotQuickAddConfig(context: Context, appWidgetId: Int): QuickAddConfig =
        quickAddConfigFlow(context, appWidgetId).first()

    private val MAX_TASKS_RANGE = 1..20
}
