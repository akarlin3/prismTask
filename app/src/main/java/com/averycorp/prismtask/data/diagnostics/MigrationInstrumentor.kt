package com.averycorp.prismtask.data.diagnostics

import android.content.Context
import android.os.Bundle
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.averycorp.prismtask.domain.model.telemetry.MigrationTelemetryEvent
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Times every Room migration and emits a [MigrationTelemetryEvent]
 * to Firebase Crashlytics + Analytics. Migrations may run before
 * Firebase is initialized on cold-boot paths (BootReceiver-driven
 * DB opens on Samsung — see memory `BootReceiver Hilt test crash`),
 * so events that fire pre-init are buffered and flushed by
 * [flushPending].
 *
 * Plain `object` rather than `@Singleton` because migrations run on
 * Room's IO worker before the Hilt graph realizes on some boot
 * paths. All Firebase access goes through `try { … } catch (_) { }`
 * blocks that mirror [DiagnosticLogger.updateCrashlyticsContext].
 *
 * No row content ever reaches Firebase — see [MigrationTelemetryEvent]
 * for the closed payload surface and `MigrationTelemetryPiiTest` for
 * the grep sentinel that fails the build if this file ever calls
 * `setCustomKey`/`putString` with a PII-shaped key or value.
 */
object MigrationInstrumentor {
    private const val ANALYTICS_STARTED = "db_migration_started"
    private const val ANALYTICS_COMPLETED = "db_migration_completed"
    private const val ANALYTICS_FAILED = "db_migration_failed"
    private const val ANALYTICS_POST_V54 = "db_post_v54_install"
    private const val PARAM_VERSION_FROM = "version_from"
    private const val PARAM_VERSION_TO = "version_to"
    private const val PARAM_DB_SIZE_BYTES = "db_size_bytes"
    private const val PARAM_DURATION_MS = "duration_ms"
    private const val PARAM_CUMULATIVE_MS = "cumulative_ms"
    private const val PARAM_EXCEPTION_CLASS = "exception_class"
    private const val PARAM_LAST_COMPLETED_STEP = "last_completed_step"
    private const val PARAM_SHIM_AGE_DAYS = "shim_age_days"
    private const val MAX_PENDING = 16
    private const val MAX_MESSAGE_LEN = 120

    private val pending = ConcurrentLinkedDeque<MigrationTelemetryEvent>()
    private val cumulativeMs = AtomicLong(0L)
    private val postV54Emitted = AtomicBoolean(false)

    @Volatile
    private var lastCompletedStep: String? = null

    /**
     * Wraps a single [migrate] call with start/complete/fail timing
     * and event emission. Surfaces the original exception unchanged
     * so Room's migration error handling stays intact.
     */
    fun <T> run(
        from: Int,
        to: Int,
        dbSizeBytes: Long,
        migrate: () -> T
    ): T {
        emit(MigrationTelemetryEvent.Started(from, to, dbSizeBytes))
        val startNanos = System.nanoTime()
        return try {
            val result = migrate()
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000L
            val cumulative = cumulativeMs.addAndGet(durationMs)
            lastCompletedStep = "$from->$to"
            emit(MigrationTelemetryEvent.Completed(from, to, durationMs, cumulative))
            result
        } catch (e: Throwable) {
            emit(
                MigrationTelemetryEvent.Failed(
                    versionFrom = from,
                    versionTo = to,
                    dbSizeBytes = dbSizeBytes,
                    exceptionClass = e.javaClass.name,
                    messageFirst120 = (e.message ?: "").take(MAX_MESSAGE_LEN),
                    lastCompletedStep = lastCompletedStep
                )
            )
            recordException(e)
            throw e
        }
    }

    /**
     * Emits [MigrationTelemetryEvent.PostV54Install] at most once
     * per app launch. Idempotent — safe to call from both
     * [PrismTaskApplication.onCreate] and the
     * [RoomDatabase.Callback.onOpen] flush hook.
     */
    fun emitPostV54IfApplicable(currentVersion: Int, shimAgeDays: Long) {
        if (currentVersion < V54_VERSION) return
        if (!postV54Emitted.compareAndSet(false, true)) return
        emit(
            MigrationTelemetryEvent.PostV54Install(
                versionFrom = currentVersion,
                versionTo = currentVersion,
                shimAgeDays = shimAgeDays
            )
        )
    }

    /**
     * Flushes events that were buffered before Firebase was ready.
     * Call sites: [PrismTaskApplication.onCreate] (after Crashlytics
     * init) and [RoomDatabase.Callback.onOpen] (guaranteed-after
     * migrations). Both run; buffer is drained idempotently.
     */
    fun flushPending(context: Context) {
        val analytics = safeAnalytics(context) ?: return
        while (true) {
            val event = pending.pollFirst() ?: break
            sendToAnalytics(event, analytics)
            sendCustomKeys(event)
        }
    }

    /**
     * Exposed for [InstrumentedMigrations] to read DB size without
     * forcing the Hilt graph open. Returns 0 if the DB file does
     * not yet exist (first-launch case).
     */
    fun dbSizeBytes(context: Context, dbName: String): Long =
        try {
            val file = context.getDatabasePath(dbName)
            if (file?.exists() == true) file.length() else 0L
        } catch (_: Exception) {
            0L
        }

    /**
     * Returns a [RoomDatabase.Callback] that flushes buffered
     * events on first open. Flushing here guarantees Firebase is
     * up: [androidx.room.RoomDatabase.openHelper] is constructed
     * after [androidx.room.RoomDatabase.Builder.build] returns,
     * which in this codebase happens inside Hilt's
     * `provideDatabase` — strictly after `Application.onCreate`.
     */
    fun flushCallback(context: Context): RoomDatabase.Callback =
        object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                flushPending(context)
            }
        }

    internal fun reset() {
        pending.clear()
        cumulativeMs.set(0L)
        postV54Emitted.set(false)
        lastCompletedStep = null
    }

    internal fun pendingSnapshot(): List<MigrationTelemetryEvent> = pending.toList()

    private fun emit(event: MigrationTelemetryEvent) {
        val analytics = safeAnalyticsOrNull()
        if (analytics == null) {
            buffer(event)
            return
        }
        sendToAnalytics(event, analytics)
        sendCustomKeys(event)
    }

    private fun buffer(event: MigrationTelemetryEvent) {
        if (pending.size >= MAX_PENDING) {
            pending.pollFirst()
        }
        pending.addLast(event)
    }

    private fun sendToAnalytics(event: MigrationTelemetryEvent, analytics: FirebaseAnalytics) {
        try {
            analytics.logEvent(eventName(event), bundleFor(event))
        } catch (_: Exception) {
            // Drop on Firebase failure — DiagnosticLogger keeps the local trace.
        }
    }

    private fun sendCustomKeys(event: MigrationTelemetryEvent) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            when (event) {
                is MigrationTelemetryEvent.Started -> {
                    crashlytics.setCustomKey("mig_active", "${event.versionFrom}->${event.versionTo}")
                    crashlytics.setCustomKey("mig_started_at_ms", System.currentTimeMillis())
                }
                is MigrationTelemetryEvent.Completed -> {
                    crashlytics.setCustomKey(
                        "mig_last_completed",
                        "${event.versionFrom}->${event.versionTo}"
                    )
                    crashlytics.setCustomKey("mig_last_duration_ms", event.durationMs)
                }
                is MigrationTelemetryEvent.Failed -> {
                    crashlytics.setCustomKey("mig_failed_from", event.versionFrom)
                    crashlytics.setCustomKey("mig_failed_to", event.versionTo)
                    crashlytics.setCustomKey("mig_db_size_bytes", event.dbSizeBytes)
                    crashlytics.setCustomKey("mig_exception_class", event.exceptionClass)
                    crashlytics.setCustomKey(
                        "mig_last_completed_step",
                        event.lastCompletedStep ?: "none"
                    )
                }
                is MigrationTelemetryEvent.PostV54Install -> {
                    crashlytics.setCustomKey("mig_post_v54_age_days", event.shimAgeDays)
                }
            }
        } catch (_: Exception) {
            // Crashlytics not available on emulator/test — drop.
        }
    }

    private fun recordException(throwable: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (_: Exception) {
            // Drop on Firebase failure.
        }
    }

    private fun eventName(event: MigrationTelemetryEvent): String =
        when (event) {
            is MigrationTelemetryEvent.Started -> ANALYTICS_STARTED
            is MigrationTelemetryEvent.Completed -> ANALYTICS_COMPLETED
            is MigrationTelemetryEvent.Failed -> ANALYTICS_FAILED
            is MigrationTelemetryEvent.PostV54Install -> ANALYTICS_POST_V54
        }

    private fun bundleFor(event: MigrationTelemetryEvent): Bundle {
        val bundle = Bundle()
        bundle.putInt(PARAM_VERSION_FROM, event.versionFrom)
        bundle.putInt(PARAM_VERSION_TO, event.versionTo)
        when (event) {
            is MigrationTelemetryEvent.Started -> {
                bundle.putLong(PARAM_DB_SIZE_BYTES, event.dbSizeBytes)
            }
            is MigrationTelemetryEvent.Completed -> {
                bundle.putLong(PARAM_DURATION_MS, event.durationMs)
                bundle.putLong(PARAM_CUMULATIVE_MS, event.cumulativeMs)
            }
            is MigrationTelemetryEvent.Failed -> {
                bundle.putLong(PARAM_DB_SIZE_BYTES, event.dbSizeBytes)
                bundle.putString(PARAM_EXCEPTION_CLASS, event.exceptionClass)
                bundle.putString(PARAM_LAST_COMPLETED_STEP, event.lastCompletedStep ?: "none")
            }
            is MigrationTelemetryEvent.PostV54Install -> {
                bundle.putLong(PARAM_SHIM_AGE_DAYS, event.shimAgeDays)
            }
        }
        return bundle
    }

    private fun safeAnalytics(context: Context): FirebaseAnalytics? =
        try {
            FirebaseAnalytics.getInstance(context)
        } catch (_: Exception) {
            null
        }

    private fun safeAnalyticsOrNull(): FirebaseAnalytics? = analyticsHolder

    /**
     * Holds the [FirebaseAnalytics] instance once Hilt's
     * `provideDatabase` has wired it up. Migration callbacks that
     * fire before the holder is set fall through to [buffer].
     */
    @Volatile
    private var analyticsHolder: FirebaseAnalytics? = null

    /**
     * Called from [InstrumentedMigrations.instrumentedMigrations]
     * via the database-module factory once Firebase is available
     * to the application context.
     */
    fun bindAnalytics(context: Context) {
        if (analyticsHolder != null) return
        analyticsHolder = safeAnalytics(context)
    }

    private const val V54_VERSION = 54
}
