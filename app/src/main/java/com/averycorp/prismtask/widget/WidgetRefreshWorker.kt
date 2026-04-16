package com.averycorp.prismtask.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that refreshes all placed widgets every 15 minutes.
 *
 * This complements the XML `updatePeriodMillis` value which is clamped to a
 * minimum of 30 minutes on API 31+. WorkManager's flex window gives us a
 * more reliable 15-minute cadence on all API levels.
 */
@HiltWorker
class WidgetRefreshWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val widgetUpdateManager: WidgetUpdateManager
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            widgetUpdateManager.updateAllWidgets()
            return Result.success()
        }

        companion object {
            const val WORK_NAME = "widget_refresh_periodic"

            fun schedule(workManager: WorkManager) {
                val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                    15,
                    TimeUnit.MINUTES
                ).setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                ).build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }
    }
