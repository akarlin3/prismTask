package com.averycorp.prismtask.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.util.DayBoundary
import com.averycorp.prismtask.widget.WidgetUpdateManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Wakes up at the user's configured "day start hour" each day. The vast majority
 * of "today" data is computed at query time using [DayBoundary], so as soon as
 * the boundary passes, ViewModels observing day-start preference flows already
 * re-emit. The job of this worker is to:
 *
 *  1. Refresh home screen widgets so they reflect the new day immediately,
 *     even if the app is not in the foreground.
 *  2. Re-schedule itself for the next boundary, since [androidx.work.PeriodicWorkRequest]
 *     does not let you anchor work to an exact wall-clock time.
 *
 * Tasks/habits are not destructively reset — instead, the "today" window
 * advances and previously-completed items naturally fall out of the
 * "today" view, while pending ones become the new day's work.
 */
@HiltWorker
class DailyResetWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val widgetUpdateManager: WidgetUpdateManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Refresh widgets so the new day's tasks/habits are visible immediately.
        try {
            widgetUpdateManager.updateAllWidgets()
        } catch (_: Throwable) {
            // Best-effort: don't fail the worker if widget update throws.
        }

        // Reschedule for the next day boundary.
        val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
        schedule(appContext, dayStartHour)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_reset"

        /**
         * Schedules the next run for the next occurrence of the configured
         * day-start hour. Replaces any previously scheduled run so a settings
         * change immediately takes effect.
         */
        fun schedule(context: Context, dayStartHour: Int) {
            val now = System.currentTimeMillis()
            val nextBoundary = DayBoundary.nextBoundary(dayStartHour, now)
            val delay = (nextBoundary - now).coerceAtLeast(0L)

            val request = OneTimeWorkRequestBuilder<DailyResetWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
