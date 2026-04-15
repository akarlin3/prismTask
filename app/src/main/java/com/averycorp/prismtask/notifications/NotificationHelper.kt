package com.averycorp.prismtask.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.R
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object NotificationHelper {
    private const val BASE_CHANNEL_ID = "prismtask_reminders"
    private const val CHANNEL_NAME = "Task Reminders"
    private const val BASE_MED_CHANNEL_ID = "prismtask_medication_reminders"
    private const val MED_CHANNEL_NAME = "Medication Reminders"
    private const val BASE_TIMER_CHANNEL_ID = "prismtask_timer_alerts"
    private const val TIMER_CHANNEL_NAME = "Timer Alerts"
    private const val TIMER_NOTIFICATION_ID = 8_001

    private const val LEGACY_CHANNEL_ID = "averytask_reminders"
    private const val LEGACY_MED_CHANNEL_ID = "averytask_medication_reminders"

    /**
     * Resolves the importance currently configured by the user. Workers /
     * receivers call this synchronously; we accept the brief block because
     * notification posting is itself a one-shot side-effect.
     */
    private fun currentImportance(context: Context): String = runBlocking {
        NotificationPreferences(context).getImportanceOnce()
    }

    private fun previousImportance(context: Context): String? = runBlocking {
        NotificationPreferences(context).getPreviousImportanceOnce()
    }

    private fun recordImportance(context: Context, importance: String) {
        runBlocking {
            NotificationPreferences(context).setPreviousImportance(importance)
        }
    }

    fun channelIdFor(base: String, importance: String): String = "${base}_${importance}"

    fun importanceToChannelLevel(importance: String): Int = when (importance) {
        NotificationPreferences.IMPORTANCE_MINIMAL -> NotificationManager.IMPORTANCE_LOW
        NotificationPreferences.IMPORTANCE_URGENT -> NotificationManager.IMPORTANCE_HIGH
        else -> NotificationManager.IMPORTANCE_DEFAULT
    }

    fun importanceToBuilderPriority(importance: String): Int = when (importance) {
        NotificationPreferences.IMPORTANCE_MINIMAL -> NotificationCompat.PRIORITY_LOW
        NotificationPreferences.IMPORTANCE_URGENT -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    /**
     * Drops every channel the app has previously created for the
     * importance-suffix scheme so a level change wipes the stale channel
     * (whose importance is immutable). Called whenever we're about to
     * (re-)create a channel for the *current* importance.
     */
    private fun deleteStaleChannels(context: Context, base: String, currentImportance: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val previous = previousImportance(context)
        if (previous != null && previous != currentImportance) {
            manager.deleteNotificationChannel(channelIdFor(base, previous))
        }
        // Also clear any other suffixes the user might have toggled through
        // before we started recording previousImportance — cheap, idempotent.
        for (level in NotificationPreferences.ALL_IMPORTANCES) {
            if (level != currentImportance) {
                manager.deleteNotificationChannel(channelIdFor(base, level))
            }
        }
    }

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        migrateOldChannels(context)
        val importance = currentImportance(context)
        deleteStaleChannels(context, BASE_CHANNEL_ID, importance)
        // Also remove the bare/legacy "prismtask_reminders" channel created
        // before the importance suffix scheme existed.
        manager.deleteNotificationChannel(BASE_CHANNEL_ID)
        val channel = NotificationChannel(
            channelIdFor(BASE_CHANNEL_ID, importance),
            CHANNEL_NAME,
            importanceToChannelLevel(importance)
        ).apply {
            description = "Reminders for upcoming tasks"
        }
        manager.createNotificationChannel(channel)
        recordImportance(context, importance)
    }

    fun migrateOldChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_MED_CHANNEL_ID)
    }

    fun showTaskReminder(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDescription: String?
    ) {
        val prefs = NotificationPreferences(context)
        val enabled = runBlocking { prefs.taskRemindersEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Task reminders disabled — skipping task=$taskId")
            return
        }
        Log.d("NotificationHelper", "Showing notification for task=$taskId")
        createNotificationChannel(context)
        val importance = currentImportance(context)
        val channelId = channelIdFor(BASE_CHANNEL_ID, importance)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, CompleteTaskReceiver::class.java).apply {
            putExtra("taskId", taskId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            taskId.toInt() + 100_000,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$taskTitle is coming up")
            .setContentText(taskDescription ?: "Ready when you are.")
            .setPriority(importanceToBuilderPriority(importance))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Complete",
                completePending
            ).build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(taskId.toInt(), notification)
    }

    private fun createMedicationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = currentImportance(context)
        deleteStaleChannels(context, BASE_MED_CHANNEL_ID, importance)
        manager.deleteNotificationChannel(BASE_MED_CHANNEL_ID)
        val channel = NotificationChannel(
            channelIdFor(BASE_MED_CHANNEL_ID, importance),
            MED_CHANNEL_NAME,
            importanceToChannelLevel(importance)
        ).apply {
            description = "Reminders for medication and timed habits"
        }
        manager.createNotificationChannel(channel)
        recordImportance(context, importance)
    }

    fun showMedicationReminder(
        context: Context,
        habitId: Long,
        habitName: String,
        habitDescription: String?,
        intervalMillis: Long,
        doseNumber: Int = 0,
        totalDoses: Int = 1
    ) {
        val prefs = NotificationPreferences(context)
        val enabled = runBlocking { prefs.medicationRemindersEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Medication reminders disabled — skipping habit=$habitId")
            return
        }
        createMedicationChannel(context)
        val importance = currentImportance(context)
        val channelId = channelIdFor(BASE_MED_CHANNEL_ID, importance)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            habitId.toInt() + 200_000,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val logIntent = Intent(context, LogMedicationReceiver::class.java).apply {
            putExtra("habitId", habitId)
        }
        val logPending = PendingIntent.getBroadcast(
            context,
            habitId.toInt() + 300_000,
            logIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalText = formatInterval(intervalMillis)
        val contentText = habitDescription ?: "$habitName \u2014 whenever you're ready."

        val doseInfo = if (totalDoses > 1 && doseNumber > 0) " (dose $doseNumber of $totalDoses)" else ""
        val title = "$habitName$doseInfo"

        val bigText = if (totalDoses > 1 && doseNumber > 0 && doseNumber < totalDoses) {
            "$contentText\nDose $doseNumber of $totalDoses \u2022 next reminder $intervalText after logging."
        } else if (totalDoses > 1 && doseNumber >= totalDoses) {
            "$contentText\nFinal dose ($doseNumber of $totalDoses)."
        } else {
            "$contentText\nNext reminder $intervalText after logging."
        }

        val notification = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(importanceToBuilderPriority(importance))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Log",
                logPending
            ).build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(habitId.toInt() + 200_000, notification)
    }

    fun showMedStepReminder(
        context: Context,
        stepId: String,
        medName: String,
        medNote: String
    ) {
        val prefs = NotificationPreferences(context)
        val enabled = runBlocking { prefs.medicationRemindersEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Medication reminders disabled — skipping step=$stepId")
            return
        }
        createMedicationChannel(context)
        val importance = currentImportance(context)
        val channelId = channelIdFor(BASE_MED_CHANNEL_ID, importance)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            stepId.hashCode() + 400_000,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (medNote.isNotEmpty()) medNote else "$medName \u2014 whenever you're ready."

        val notification = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$medName \u2014 Heads Up")
            .setContentText(contentText)
            .setPriority(importanceToBuilderPriority(importance))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(stepId.hashCode() + 400_000, notification)
    }

    private fun createTimerChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = currentImportance(context)
        deleteStaleChannels(context, BASE_TIMER_CHANNEL_ID, importance)
        manager.deleteNotificationChannel(BASE_TIMER_CHANNEL_ID)
        val channel = NotificationChannel(
            channelIdFor(BASE_TIMER_CHANNEL_ID, importance),
            TIMER_CHANNEL_NAME,
            importanceToChannelLevel(importance)
        ).apply {
            description = "Alerts when a Timer countdown completes"
        }
        manager.createNotificationChannel(channel)
        recordImportance(context, importance)
    }

    fun showTimerCompleteNotification(context: Context, mode: String) {
        val prefs = NotificationPreferences(context)
        val enabled = runBlocking { prefs.timerAlertsEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Timer alerts disabled — skipping mode=$mode")
            return
        }
        createTimerChannel(context)
        val importance = currentImportance(context)
        val channelId = channelIdFor(BASE_TIMER_CHANNEL_ID, importance)

        val isBreak = mode.equals("BREAK", ignoreCase = true)
        val title = if (isBreak) "Break Complete!" else "Timer Complete!"
        val body = if (isBreak) {
            "Ready to get back to focus?"
        } else {
            "Nice work \u2014 time for a break."
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            TIMER_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(importanceToBuilderPriority(importance))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(TIMER_NOTIFICATION_ID, notification)
    }

    private fun formatInterval(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}
