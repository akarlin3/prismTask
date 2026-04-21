package com.averycorp.prismtask

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.BuiltInHabitReconciler
import com.averycorp.prismtask.data.remote.LifeCategoryBackfiller
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.data.repository.SchoolworkRepository
import com.averycorp.prismtask.data.seed.TemplateSeeder
import com.averycorp.prismtask.notifications.NotificationWorkerScheduler
import com.averycorp.prismtask.workers.AutoArchiveWorker
import com.averycorp.prismtask.workers.CalendarSyncScheduler
import com.averycorp.prismtask.workers.DailyResetWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
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
    lateinit var lifeCategoryBackfiller: LifeCategoryBackfiller

    @Inject
    lateinit var calendarSyncScheduler: CalendarSyncScheduler

    @Inject
    lateinit var notificationWorkerScheduler: NotificationWorkerScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        const val FIRESTORE_EMULATOR_PORT = 8080
        const val AUTH_EMULATOR_PORT = 9099
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration
            .Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        configureFirebaseEmulator()
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
            runLifeCategoryBackfill()
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
     * Routes Firestore and Auth at the local Firebase Emulator Suite when the
     * compile-time [BuildConfig.USE_FIREBASE_EMULATOR] flag is on (debug-only
     * by default). `useEmulator` must run BEFORE any Firestore / Auth
     * operation — all existing call sites use `by lazy` or read Firebase
     * inside functions, so running this at the top of `onCreate()` is safe.
     *
     * Host selection:
     *  - Android emulator: `10.0.2.2` (alias for host loopback).
     *  - Physical device:  `localhost`, which relies on the developer running
     *    `adb reverse tcp:8080 tcp:8080 && adb reverse tcp:9099 tcp:9099`
     *    after connecting the device. See docs/FIREBASE_EMULATOR.md.
     */
    private fun configureFirebaseEmulator() {
        if (!BuildConfig.USE_FIREBASE_EMULATOR) return
        val host = if (isAndroidEmulator()) "10.0.2.2" else "localhost"
        try {
            FirebaseFirestore.getInstance().useEmulator(host, FIRESTORE_EMULATOR_PORT)
            // Disable persistent cache so the emulator always reflects the
            // fresh server state — makes two-device sync tests deterministic.
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings
                    .Builder()
                    .setPersistenceEnabled(false)
                    .build()
            FirebaseAuth.getInstance().useEmulator(host, AUTH_EMULATOR_PORT)
            android.util.Log.i(
                "PrismTaskApp",
                "Firebase emulator routing active: firestore=$host:$FIRESTORE_EMULATOR_PORT " +
                    "auth=$host:$AUTH_EMULATOR_PORT"
            )
        } catch (e: Exception) {
            // useEmulator throws if called after the first Firestore op. If
            // that ever happens we want to fail loudly in debug rather than
            // silently talk to production.
            android.util.Log.e("PrismTaskApp", "Firebase emulator wiring failed", e)
        }
    }

    private fun isAndroidEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            Build.PRODUCT == "google_sdk" ||
            Build.PRODUCT == "sdk_google_phone_x86" ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

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
     * Runs the one-shot life-category classifier pass over every legacy
     * `tasks.life_category IS NULL` row. Gated by a DataStore flag so it
     * fires at most once per install. Details: [LifeCategoryBackfiller].
     */
    private fun runLifeCategoryBackfill() {
        appScope.launch {
            lifeCategoryBackfiller.runIfNeeded()
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
