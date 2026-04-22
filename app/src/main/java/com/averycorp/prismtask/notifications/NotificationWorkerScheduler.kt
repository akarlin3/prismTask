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

        // One-shot migration to seed the v1.4.38 WeeklyTaskSummaryWorker
        // unique work for existing installs — WorkManager has no row yet
        // on upgrade, and applyWeeklyTaskSummary below only enqueues when
        // the user explicitly flips the toggle. Running the seed once
        // ensures the default-ON preference actually materializes a
        // schedule without the user having to touch Settings.
        WeeklyTaskSummaryMigration.runIfNeeded(context, notificationPreferences)

        applyBriefing(notificationPreferences.dailyBriefingEnabled.first())
        applyEveningSummary(notificationPreferences.eveningSummaryEnabled.first())
        applyWeeklyHabitSummary(notificationPreferences.weeklySummaryEnabled.first())
        applyWeeklyTaskSummary(notificationPreferences.weeklyTaskSummaryEnabled.first())
        applyOverloadCheck(notificationPreferences.overloadAlertsEnabled.first())
        applyReengagement(notificationPreferences.reengagementEnabled.first())

        // Weekly review worker (A2). Gated by its own preference toggle;
        // the seed migration just guarantees first-launch-after-update
        // gets the periodic work enqueued exactly once before the
        // preference-driven apply below takes over on subsequent boots.
        WeeklyReviewSchedulerMigration.runIfNeeded(context, notificationPreferences)
        applyWeeklyReview(notificationPreferences.weeklyReviewAutoGenerateEnabled.first())
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

    fun applyWeeklyTaskSummary(enabled: Boolean) {
        if (enabled) {
            WeeklyTaskSummaryWorker.schedule(context)
        } else {
            WeeklyTaskSummaryWorker.cancel(context)
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

    fun applyWeeklyReview(enabled: Boolean) {
        if (enabled) {
            WeeklyReviewWorker.schedule(context)
        } else {
            WeeklyReviewWorker.cancel(context)
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

/**
 * One-shot seeding for the v1.4.38 task-summary feature. On upgrade,
 * WorkManager has no entry under [WeeklyTaskSummaryWorker.WORK_NAME];
 * this migration enqueues it once so the default-ON preference takes
 * effect without the user having to touch Settings. Subsequent
 * applyAll calls will re-enqueue with UPDATE policy based on the live
 * preference value.
 *
 * Gated by a persistent flag so it never fires twice on the same
 * install. If scheduling throws (e.g. missing WorkManager at boot-time
 * contexts) the flag is still set so we don't retry in a hot loop.
 */
private object WeeklyTaskSummaryMigration {
    suspend fun runIfNeeded(context: Context, prefs: NotificationPreferences) {
        if (prefs.getHasSeededWeeklyTaskSummaryWorkerOnce()) return
        try {
            if (prefs.weeklyTaskSummaryEnabled.first()) {
                WeeklyTaskSummaryWorker.schedule(context)
            }
        } catch (_: Exception) {
            // Best-effort seed — applyAll's subsequent call will pick up
            // where this left off if the enqueue failed here.
        }
        prefs.setHasSeededWeeklyTaskSummaryWorker()
    }
}

/**
 * One-time seed for the v1.4.39 [WeeklyReviewWorker]. Unlike
 * [WeeklyHabitSummaryMigration] this isn't healing a class rename —
 * there's no stale unique work to cancel — it's just guaranteeing
 * existing users who update past the release get the periodic work
 * enqueued once. After this fires, [NotificationWorkerScheduler.applyAll]
 * keeps the schedule in sync with the user's preference on every
 * subsequent boot.
 *
 * Gated by a one-shot preference flag so re-runs are no-ops.
 */
private object WeeklyReviewSchedulerMigration {
    suspend fun runIfNeeded(context: Context, prefs: NotificationPreferences) {
        if (prefs.getWeeklyReviewWorkerSeededOnce()) return
        if (prefs.weeklyReviewAutoGenerateEnabled.first()) {
            WeeklyReviewWorker.schedule(context)
        }
        prefs.setWeeklyReviewWorkerSeeded()
    }
}
