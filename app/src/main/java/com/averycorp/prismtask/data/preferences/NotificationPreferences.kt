package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_prefs"
)

/**
 * Persists user-controlled notification settings:
 *  - per-type enable/disable flags for every notification surface,
 *  - a global importance/intrusiveness setting that maps to channel
 *    importance + builder priority,
 *  - a default reminder lead-time used to pre-fill `reminderOffset` on
 *    newly-created tasks.
 *
 * Everything is read/written via [Flow]/`suspend` setters — callers
 * (workers, the notification helper, ViewModels) read with `.first()`
 * before posting, or collect the flow when they need live updates.
 */
@Singleton
class NotificationPreferences
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Per-type enable flags (default true)
        private val TASK_REMINDERS_ENABLED = booleanPreferencesKey("task_reminders_enabled")
        private val TIMER_ALERTS_ENABLED = booleanPreferencesKey("timer_alerts_enabled")
        private val MEDICATION_REMINDERS_ENABLED = booleanPreferencesKey("medication_reminders_enabled")
        private val DAILY_BRIEFING_ENABLED = booleanPreferencesKey("daily_briefing_enabled")
        private val EVENING_SUMMARY_ENABLED = booleanPreferencesKey("evening_summary_enabled")
        private val WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("weekly_summary_enabled")
        private val OVERLOAD_ALERTS_ENABLED = booleanPreferencesKey("overload_alerts_enabled")
        private val REENGAGEMENT_ENABLED = booleanPreferencesKey("reengagement_enabled")

        // Importance / intrusiveness
        private val NOTIFICATION_IMPORTANCE = stringPreferencesKey("notification_importance")

        /**
         * Tracks the importance level the channel was *last* created with so
         * the next change can delete the stale channel before creating the
         * new one. Channel importance is immutable after creation, so we use
         * a per-importance suffix on the channel ID.
         */
        private val PREVIOUS_IMPORTANCE = stringPreferencesKey("previous_importance")

        // Default reminder lead time (millis before due date)
        private val DEFAULT_REMINDER_OFFSET = longPreferencesKey("default_reminder_offset")

        const val IMPORTANCE_MINIMAL = "minimal"
        const val IMPORTANCE_STANDARD = "standard"
        const val IMPORTANCE_URGENT = "urgent"

        const val DEFAULT_IMPORTANCE = IMPORTANCE_STANDARD

        /** Default reminder offset = 15 minutes before the task is due. */
        const val DEFAULT_REMINDER_OFFSET_MS = 900_000L

        /** Sentinel meaning "user has opted out of any default offset". */
        const val OFFSET_NONE = -1L

        val ALL_IMPORTANCES = listOf(IMPORTANCE_MINIMAL, IMPORTANCE_STANDARD, IMPORTANCE_URGENT)

        val ALL_REMINDER_OFFSETS = listOf(
            0L,
            300_000L,
            900_000L,
            1_800_000L,
            3_600_000L,
            86_400_000L,
            OFFSET_NONE
        )
    }

    // region Per-type enable flags

    val taskRemindersEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[TASK_REMINDERS_ENABLED] ?: true }

    suspend fun setTaskRemindersEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[TASK_REMINDERS_ENABLED] = enabled }
    }

    val timerAlertsEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[TIMER_ALERTS_ENABLED] ?: true }

    suspend fun setTimerAlertsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[TIMER_ALERTS_ENABLED] = enabled }
    }

    val medicationRemindersEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[MEDICATION_REMINDERS_ENABLED] ?: true }

    suspend fun setMedicationRemindersEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[MEDICATION_REMINDERS_ENABLED] = enabled }
    }

    val dailyBriefingEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[DAILY_BRIEFING_ENABLED] ?: true }

    suspend fun setDailyBriefingEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[DAILY_BRIEFING_ENABLED] = enabled }
    }

    val eveningSummaryEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[EVENING_SUMMARY_ENABLED] ?: true }

    suspend fun setEveningSummaryEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[EVENING_SUMMARY_ENABLED] = enabled }
    }

    val weeklySummaryEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[WEEKLY_SUMMARY_ENABLED] ?: true }

    suspend fun setWeeklySummaryEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[WEEKLY_SUMMARY_ENABLED] = enabled }
    }

    val overloadAlertsEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[OVERLOAD_ALERTS_ENABLED] ?: true }

    suspend fun setOverloadAlertsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[OVERLOAD_ALERTS_ENABLED] = enabled }
    }

    val reengagementEnabled: Flow<Boolean> = context.notificationDataStore.data
        .map { it[REENGAGEMENT_ENABLED] ?: true }

    suspend fun setReengagementEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[REENGAGEMENT_ENABLED] = enabled }
    }

    // endregion

    // region Importance

    val importance: Flow<String> = context.notificationDataStore.data.map {
        val stored = it[NOTIFICATION_IMPORTANCE] ?: DEFAULT_IMPORTANCE
        if (stored in ALL_IMPORTANCES) stored else DEFAULT_IMPORTANCE
    }

    suspend fun setImportance(level: String) {
        val normalized = if (level in ALL_IMPORTANCES) level else DEFAULT_IMPORTANCE
        context.notificationDataStore.edit { it[NOTIFICATION_IMPORTANCE] = normalized }
    }

    suspend fun getImportanceOnce(): String = importance.first()

    val previousImportance: Flow<String?> = context.notificationDataStore.data.map {
        it[PREVIOUS_IMPORTANCE]
    }

    suspend fun getPreviousImportanceOnce(): String? = context.notificationDataStore.data
        .first()[PREVIOUS_IMPORTANCE]

    suspend fun setPreviousImportance(level: String) {
        context.notificationDataStore.edit { it[PREVIOUS_IMPORTANCE] = level }
    }

    // endregion

    // region Default reminder offset

    val defaultReminderOffset: Flow<Long> = context.notificationDataStore.data.map {
        it[DEFAULT_REMINDER_OFFSET] ?: DEFAULT_REMINDER_OFFSET_MS
    }

    suspend fun setDefaultReminderOffset(offset: Long) {
        context.notificationDataStore.edit { it[DEFAULT_REMINDER_OFFSET] = offset }
    }

    suspend fun getDefaultReminderOffsetOnce(): Long = defaultReminderOffset.first()

    // endregion
}
