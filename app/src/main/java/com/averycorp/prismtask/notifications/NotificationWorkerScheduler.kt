package com.averycorp.prismtask.notifications

import android.content.Context
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the user's notification-worker toggle state to WorkManager.
 *
 * Each of the five summary workers (daily briefing, evening summary,
 * weekly summary, overload check, re-engagement) is keyed by a
 * corresponding flag in [NotificationPreferences]. Toggling OFF in
 * Settings cancels the periodic work; toggling ON re-enqueues it with
 * [androidx.work.ExistingPeriodicWorkPolicy.UPDATE] so the schedule
 * tracks the latest user preference without duplicating jobs.
 *
 * [applyAll] runs once on app startup so a user who enabled a worker on
 * a previous launch keeps it scheduled across app restarts. The
 * per-worker [applyBriefing], [applyEveningSummary], etc. helpers are
 * called from `SettingsViewModel` whenever the user flips a toggle so
 * the change takes effect immediately.
 *
 * Weekly habit summary is deliberately not listed: `WeeklyHabitSummary`
 * is a helper class that `WeeklySummaryWorker` delegates to, not a
 * separate worker. Scheduling [WeeklySummaryWorker] covers both.
 */
@Singleton
class NotificationWorkerScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val notificationPreferences: NotificationPreferences
) {
    suspend fun applyAll() {
        applyBriefing(notificationPreferences.dailyBriefingEnabled.first())
        applyEveningSummary(notificationPreferences.eveningSummaryEnabled.first())
        applyWeeklySummary(notificationPreferences.weeklySummaryEnabled.first())
        applyOverloadCheck(notificationPreferences.overloadAlertsEnabled.first())
        applyReengagement(notificationPreferences.reengagementEnabled.first())
    }

    suspend fun applyBriefing(enabled: Boolean) {
        if (enabled) {
            val hour = notificationPreferences.briefingMorningHour.first()
            BriefingNotificationWorker.schedule(context, hourOfDay = hour, minute = 0)
        } else {
            BriefingNotificationWorker.cancel(context)
        }
    }

    suspend fun applyEveningSummary(enabled: Boolean) {
        if (enabled) {
            val hour = notificationPreferences.briefingEveningHour.first()
            EveningSummaryWorker.schedule(context, hourOfDay = hour, minute = 0)
        } else {
            EveningSummaryWorker.cancel(context)
        }
    }

    fun applyWeeklySummary(enabled: Boolean) {
        if (enabled) {
            WeeklySummaryWorker.schedule(context)
        } else {
            WeeklySummaryWorker.cancel(context)
        }
    }

    fun applyOverloadCheck(enabled: Boolean) {
        if (enabled) {
            OverloadCheckWorker.schedule(context)
        } else {
            OverloadCheckWorker.cancel(context)
        }
    }

    fun applyReengagement(enabled: Boolean) {
        if (enabled) {
            ReengagementWorker.schedule(context)
        } else {
            ReengagementWorker.cancel(context)
        }
    }
}
