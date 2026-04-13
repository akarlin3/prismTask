package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.taskBehaviorDataStore: DataStore<Preferences> by preferencesDataStore(name = "task_behavior_prefs")

data class UrgencyWeights(
    val dueDate: Float = 0.40f,
    val priority: Float = 0.30f,
    val age: Float = 0.15f,
    val subtasks: Float = 0.15f
)

@Singleton
class TaskBehaviorPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val DEFAULT_SORT = stringPreferencesKey("default_sort")
        private val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        private val URGENCY_WEIGHT_DUE_DATE = floatPreferencesKey("urgency_weight_due_date")
        private val URGENCY_WEIGHT_PRIORITY = floatPreferencesKey("urgency_weight_priority")
        private val URGENCY_WEIGHT_AGE = floatPreferencesKey("urgency_weight_age")
        private val URGENCY_WEIGHT_SUBTASKS = floatPreferencesKey("urgency_weight_subtasks")
        private val REMINDER_PRESETS = stringPreferencesKey("reminder_presets")
        private val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        private val DAY_START_HOUR = intPreferencesKey("day_start_hour")
    }

    fun getDefaultSort(): Flow<String> = context.taskBehaviorDataStore.data.map { prefs ->
        prefs[DEFAULT_SORT] ?: "DUE_DATE"
    }

    fun getDefaultViewMode(): Flow<String> = context.taskBehaviorDataStore.data.map { prefs ->
        prefs[DEFAULT_VIEW_MODE] ?: "UPCOMING"
    }

    fun getUrgencyWeights(): Flow<UrgencyWeights> {
        val dueDateFlow = context.taskBehaviorDataStore.data.map { it[URGENCY_WEIGHT_DUE_DATE] ?: 0.40f }
        val priorityFlow = context.taskBehaviorDataStore.data.map { it[URGENCY_WEIGHT_PRIORITY] ?: 0.30f }
        val ageFlow = context.taskBehaviorDataStore.data.map { it[URGENCY_WEIGHT_AGE] ?: 0.15f }
        val subtasksFlow = context.taskBehaviorDataStore.data.map { it[URGENCY_WEIGHT_SUBTASKS] ?: 0.15f }
        return combine(dueDateFlow, priorityFlow, ageFlow, subtasksFlow) { d, p, a, s ->
            UrgencyWeights(d, p, a, s)
        }
    }

    fun getReminderPresets(): Flow<List<Long>> = context.taskBehaviorDataStore.data.map { prefs ->
        prefs[REMINDER_PRESETS]?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            ?: listOf(0L, 900_000L, 1_800_000L, 3_600_000L, 86_400_000L)
    }

    fun getFirstDayOfWeek(): Flow<DayOfWeek> = context.taskBehaviorDataStore.data.map { prefs ->
        prefs[FIRST_DAY_OF_WEEK]?.let { DayOfWeek.valueOf(it) } ?: DayOfWeek.MONDAY
    }

    fun getDayStartHour(): Flow<Int> = context.taskBehaviorDataStore.data.map { prefs ->
        prefs[DAY_START_HOUR] ?: 0
    }

    suspend fun setDefaultSort(sort: String) {
        context.taskBehaviorDataStore.edit { it[DEFAULT_SORT] = sort }
    }

    suspend fun setDefaultViewMode(mode: String) {
        context.taskBehaviorDataStore.edit { it[DEFAULT_VIEW_MODE] = mode }
    }

    suspend fun setUrgencyWeights(weights: UrgencyWeights) {
        context.taskBehaviorDataStore.edit { prefs ->
            prefs[URGENCY_WEIGHT_DUE_DATE] = weights.dueDate
            prefs[URGENCY_WEIGHT_PRIORITY] = weights.priority
            prefs[URGENCY_WEIGHT_AGE] = weights.age
            prefs[URGENCY_WEIGHT_SUBTASKS] = weights.subtasks
        }
    }

    suspend fun setReminderPresets(presets: List<Long>) {
        context.taskBehaviorDataStore.edit { prefs ->
            prefs[REMINDER_PRESETS] = presets.joinToString(",")
        }
    }

    suspend fun setFirstDayOfWeek(day: DayOfWeek) {
        context.taskBehaviorDataStore.edit { it[FIRST_DAY_OF_WEEK] = day.name }
    }

    suspend fun setDayStartHour(hour: Int) {
        context.taskBehaviorDataStore.edit { it[DAY_START_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun resetToDefaults() {
        context.taskBehaviorDataStore.edit { prefs ->
            prefs.remove(DEFAULT_SORT)
            prefs.remove(DEFAULT_VIEW_MODE)
            prefs.remove(URGENCY_WEIGHT_DUE_DATE)
            prefs.remove(URGENCY_WEIGHT_PRIORITY)
            prefs.remove(URGENCY_WEIGHT_AGE)
            prefs.remove(URGENCY_WEIGHT_SUBTASKS)
            prefs.remove(REMINDER_PRESETS)
            prefs.remove(FIRST_DAY_OF_WEEK)
            prefs.remove(DAY_START_HOUR)
        }
    }
}
