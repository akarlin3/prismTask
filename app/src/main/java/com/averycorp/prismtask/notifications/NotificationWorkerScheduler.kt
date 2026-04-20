package com.averycorp.prismtask.notifications

import android.content.Context
import androidx.work.WorkManager
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the user's notification-worker toggle state to WorkManager.
 *
 * Each of the five summary workers (daily briefing, evening summary,
 * weekly habit summary, overload check, re-engagement) is keyed by a
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
 * [WeeklyHabitSummaryWorker] is the worker formerly named
 * `WeeklySummaryWorker`, promoted out of the `WeeklyHabitSummary`
 * helper-delegation pattern in v1.4.0. The unique work name and
 * preference key are preserved so existing scheduled work and user
 * toggles survive the rename; [applyAll] also performs a one-time
 * cleanup of the stale pre-rename registration via
 * [WeeklyHabitSummaryMigration].
 */
@Singleton
class NotificationWorkerScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val notificationPreferences: NotificationPreferences
) {
    suspend fun applyAll() {
        // Defensive one-time cleanup for the WeeklySummaryWorker ->
        // WeeklyHabitSummaryWorker rename: cancel the pre-rename unique
        // work so WorkManager's stored FQN doesn't point at a deleted
        // class. The applyWeeklyHabitSummary call below re-enqueues it
        // against the new class name under the same unique name.
        WeeklyHabitSummaryMigration.runIfNeeded(context, notificationPreferences)

        applyBriefing(notificationPreferences.dailyBriefingEnabled.first())
        applyEveningSummary(notificationPreferences.eveningSummaryEnabled.first())
        applyWeeklyHabitSummary(notificationPreferences.weeklySummaryEnabled.first())
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

    fun applyWeeklyHabitSummary(enabled: Boolean) {
        if (enabled) {
            WeeklyHabitSummaryWorker.schedule(context)
        } else {
            WeeklyHabitSummaryWorker.cancel(context)
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

/**
 * One-time migration to heal the WeeklySummaryWorker ->
 * WeeklyHabitSummaryWorker class rename. WorkManager persists the
 * worker class FQN in its internal DB; after the rename, the stale row
 * would point at a class that no longer exists and its next scheduled
 * trigger would silently drop.
 *
 * The cleanup cancels the existing unique work (which wipes the stale
 * row). [NotificationWorkerScheduler.applyAll] then re-enqueues under
 * the same unique name via [WeeklyHabitSummaryWorker.schedule], binding
 * the new class. Gated by a one-shot preference flag so it never fires
 * twice on the same install.
 */
private object WeeklyHabitSummaryMigration {
    suspend fun runIfNeeded(context: Context, prefs: NotificationPreferences) {
        if (prefs.getWeeklyHabitSummaryMigrationRunOnce()) return
        try {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WeeklyHabitSummaryWorker.WORK_NAME)
        } catch (_: Exception) {
            // Best-effort cleanup — a missing WorkManager instance or a
            // never-scheduled work name both fall through to the flag
            // set below so the cleanup doesn't retry every launch.
        }
        prefs.setWeeklyHabitSummaryMigrationRun()
    }
}
