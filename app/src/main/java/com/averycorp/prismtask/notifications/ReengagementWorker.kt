package com.averycorp.prismtask.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.R
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.ReengagementRequest
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private val Context.reengagementStore by preferencesDataStore(name = "reengagement_prefs")

/**
 * Fires as a push notification when the user hasn't opened the app in 2 days.
 * Maximum ONE notification per absence period — if they don't open after the
 * first nudge, do NOT send another. Ever. This is critical.
 *
 * Tier: Premium (AI_REENGAGEMENT)
 */
@HiltWorker
class ReengagementWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val api: PrismTaskApi,
        private val taskDao: TaskDao,
        private val proFeatureGate: ProFeatureGate
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!proFeatureGate.hasAccess(ProFeatureGate.AI_REENGAGEMENT)) return Result.success()

            val store = applicationContext.reengagementStore
            val prefs = store.data.first()

            // Check if we already sent a nudge and user hasn't opened the app since
            val alreadySent = prefs[KEY_REENGAGEMENT_SENT] ?: false
            if (alreadySent) return Result.success() // Silence is better than nagging

            val lastOpenTime = prefs[KEY_LAST_OPEN_TIME] ?: System.currentTimeMillis()
            val daysSinceOpen = TimeUnit.MILLISECONDS
                .toDays(
                    System.currentTimeMillis() - lastOpenTime
                ).toInt()

            // Only nudge if absent for 2+ days
            if (daysSinceOpen < 2) return Result.success()

            return try {
                // Get last completed task title
                val lastCompletedTask = taskDao.getLastCompletedTask()
                val lastTaskTitle = lastCompletedTask?.title

                // Get total pending count (but we won't show it)
                val totalPending = taskDao.getIncompleteTaskCount()

                val response = api.getReengagementNudge(
                    ReengagementRequest(
                        daysAbsent = daysSinceOpen,
                        lastTaskTitle = lastTaskTitle,
                        totalPending = totalPending
                    )
                )

                showNotification(applicationContext, response.nudge)

                // Mark as sent — won't send again until user opens app
                store.edit { it[KEY_REENGAGEMENT_SENT] = true }

                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }

        private fun showNotification(context: Context, nudge: String) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gentle Nudges",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Gentle re-engagement nudges" }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("PrismTask")
                .setContentText(nudge)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        }

        companion object {
            private const val WORK_NAME = "reengagement_nudge"
            private const val CHANNEL_ID = "prismtask_reengagement"
            private const val NOTIFICATION_ID = 9003

            private val KEY_REENGAGEMENT_SENT = booleanPreferencesKey("reengagement_sent")
            private val KEY_LAST_OPEN_TIME = longPreferencesKey("last_open_time")

            fun schedule(context: Context) {
                val request = PeriodicWorkRequestBuilder<ReengagementWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }

            fun cancel(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }

            /**
             * Call this when the user opens the app to reset the re-engagement flag
             * and record the last open time.
             */
            suspend fun onAppOpened(context: Context) {
                context.reengagementStore.edit {
                    it[KEY_REENGAGEMENT_SENT] = false
                    it[KEY_LAST_OPEN_TIME] = System.currentTimeMillis()
                }
            }
        }
    }
