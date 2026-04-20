package com.averycorp.prismtask

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.BuiltInHabitReconciler
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.data.repository.SchoolworkRepository
import com.averycorp.prismtask.data.seed.TemplateSeeder
import com.averycorp.prismtask.notifications.NotificationWorkerScheduler
import com.averycorp.prismtask.widget.WidgetRefreshWorker
import com.averycorp.prismtask.workers.AutoArchiveWorker
import com.averycorp.prismtask.workers.CalendarSyncScheduler
import com.averycorp.prismtask.workers.DailyResetWorker
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class PrismTaskApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var schoolworkRepository: SchoolworkRepository

    @Inject
    lateinit var leisureRepository: LeisureRepository

    @Inject
    lateinit var taskBehaviorPreferences: TaskBehaviorPreferences

    @Inject
    lateinit var templateSeeder: TemplateSeeder

    @Inject
    lateinit var builtInHabitReconciler: BuiltInHabitReconciler

    @Inject
    lateinit var calendarSyncScheduler: CalendarSyncScheduler

    @Inject
    lateinit var notificationWorkerScheduler: NotificationWorkerScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration
            .Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        configureCrashlytics()
        try {
            scheduleAutoArchive()
            scheduleDailyReset()
            scheduleNotificationWorkers()
            scheduleWidgetRefresh()
            scheduleCalendarSync()
        } catch (e: Exception) {
            android.util.Log.e("PrismTaskApp", "Worker scheduling failed", e)
            try {
                FirebaseCrashlytics.getInstance().recordException(e)
            } catch (_: Exception) {
                // Firebase not available
            }
        }
        try {
            seedStructuralHabits()
            seedBuiltInTemplates()
            runBuiltInBackfill()
            runDriftCleanup()
        } catch (e: Exception) {
            android.util.Log.e("PrismTaskApp", "Seeding kickoff failed", e)
            try {
                FirebaseCrashlytics.getInstance().recordException(e)
            } catch (_: Exception) {
                // Firebase not available
            }
        }
    }

    private fun configureCrashlytics() {
        try {
            FirebaseCrashlytics
                .getInstance()
                .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        } catch (e: Exception) {
            android.util.Log.e("PrismTaskApp", "Crashlytics init failed — Firebase may not be configured", e)
        }
    }

    /**
     * Applies the user's summary-worker toggles to WorkManager on cold
     * start. Covers daily briefing, evening summary, weekly summary,
     * overload check, and re-engagement — each gated on its own
     * [NotificationPreferences] flag. Uses UPDATE policy inside the
     * scheduler so a hot toggle or schedule change doesn't duplicate jobs.
     */
    private fun scheduleNotificationWorkers() {
        appScope.launch {
            try {
                notificationWorkerScheduler.applyAll()
            } catch (e: Exception) {
                android.util.Log.e("PrismTaskApp", "Notification worker scheduling failed", e)
            }
        }
    }

    /**
     * Inserts the six built-in task templates on first launch. Gated by a
     * `templates_seeded` flag in [com.averycorp.prismtask.data.preferences.TemplatePreferences]
     * so it runs exactly once per install and never resurrects deleted defaults.
     */
    private fun seedBuiltInTemplates() {
        appScope.launch {
            templateSeeder.seedIfNeeded()
        }
    }

    /**
     * Schedules the daily reset worker to fire at the configured day-start
     * hour, and re-schedules whenever the user changes the setting so the
     * change takes effect immediately.
     */
    private fun scheduleDailyReset() {
        appScope.launch {
            taskBehaviorPreferences.getDayStartHour().collectLatest { hour ->
                DailyResetWorker.schedule(this@PrismTaskApplication, hour)
            }
        }
    }

    /**
     * Collapses any duplicate built-in habits that exist locally (e.g. from
     * a prior partial sync). Runs once per install, gated by a DataStore flag.
     * The post-sync reconciliation in [BuiltInHabitReconciler] handles the
     * cloud-vs-local case after the first successful sign-in sync.
     */
    private fun runBuiltInBackfill() {
        appScope.launch {
            builtInHabitReconciler.runBackfillIfNeeded()
        }
    }

    private fun runDriftCleanup() {
        appScope.launch {
            builtInHabitReconciler.runDriftCleanupIfNeeded()
        }
    }

    /**
     * Ensures the schoolwork and leisure habit "shells" exist on app start.
     * Self-care / housework / medication habits are no longer auto-created —
     * users opt into them via the onboarding template picker or the Browse
     * Templates entry in Settings. Existing installs keep their pre-seeded
     * self-care habits because the self-care repository's habit creation is
     * idempotent and still runs the next time the user actively picks a
     * self-care template.
     */
    private fun seedStructuralHabits() {
        appScope.launch {
            schoolworkRepository.ensureHabitExists()
            leisureRepository.ensureHabitExists()
        }
    }

    private fun scheduleWidgetRefresh() {
        // Widgets disabled for v1.0 — cancel periodic refresh worker instead of scheduling.
        // Re-enable in v1.2: replace cancelUniqueWork with WidgetRefreshWorker.schedule(...)
        WorkManager.getInstance(this).cancelUniqueWork("widget_refresh_periodic")
    }

    /**
     * Applies calendar-sync preferences on startup. Uses a unique-periodic
     * work request with UPDATE policy inside [CalendarSyncScheduler] so
     * restarts don't pile up duplicate jobs.
     *
     * The underlying scheduler uses runBlocking to read DataStore, which can
     * ANR if DataStore is slow on cold start. Dispatch off Main to be safe.
     */
    private fun scheduleCalendarSync() {
        appScope.launch {
            try {
                calendarSyncScheduler.applyPreferences()
            } catch (e: Exception) {
                android.util.Log.e("PrismTaskApp", "Calendar sync scheduling failed", e)
            }
        }
    }

    private fun scheduleAutoArchive() {
        val workRequest = PeriodicWorkRequestBuilder<AutoArchiveWorker>(
            24,
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "auto_archive",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
