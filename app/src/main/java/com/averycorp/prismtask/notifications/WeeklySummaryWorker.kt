package com.averycorp.prismtask.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class WeeklySummaryWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val weeklyHabitSummary: WeeklyHabitSummary,
        private val notificationPreferences: NotificationPreferences
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            if (!notificationPreferences.weeklySummaryEnabled.first()) return Result.success()
            val data = weeklyHabitSummary.generateWeeklySummary()
            if (data.totalHabits > 0) {
                weeklyHabitSummary.showWeeklyNotification(applicationContext, data)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }

        companion object {
            private const val WORK_NAME = "weekly_habit_summary"

            fun schedule(context: Context) {
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    set(Calendar.HOUR_OF_DAY, 19)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
                }

                val delay = target.timeInMillis - now.timeInMillis

                val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
