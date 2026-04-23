package com.averycorp.prismtask.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.averycorp.prismtask.data.local.dao.BatchUndoLogDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily sweep of `batch_undo_log` (A2 — pulled-from-H PR3).
 *
 * Drops rows where:
 *   * `expires_at < now AND undone_at IS NULL` — the 24-hour undo
 *     window has lapsed.
 *   * `undone_at IS NOT NULL AND undone_at < now - 7 days` — already
 *     reversed and the short tail-display window has passed too.
 *
 * Keeps the table small without losing the "X minutes ago" affordance
 * that the Settings history shows for recently-undone batches.
 *
 * No user preference toggles this worker. The sweep is pure
 * maintenance and runs unconditionally on startup via
 * [PrismTaskApplication.onCreate].
 */
@HiltWorker
class BatchUndoSweepWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val batchUndoLogDao: BatchUndoLogDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val undoneCutoff = now - UNDO_TAIL_MILLIS
        return try {
            batchUndoLogDao.sweep(now = now, undoneCutoff = undoneCutoff)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Batch undo log sweep failed", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_undo_log_sweep_daily"
        const val TAG = "BatchUndoSweepWorker"

        /** Already-undone rows linger for 7 days so the Settings history
         *  can show "undone X minutes/hours/days ago" before they vanish. */
        const val UNDO_TAIL_MILLIS = 7L * 24 * 60 * 60 * 1000

        /**
         * Schedules the sweep to run daily at the given hour (default 03:00
         * local time — low-traffic window). Uses
         * [ExistingPeriodicWorkPolicy.UPDATE] so re-scheduling on every
         * launch is a no-op once the work is enqueued.
         */
        fun schedule(context: Context, hourOfDay: Int = 3, minute: Int = 0) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<BatchUndoSweepWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
