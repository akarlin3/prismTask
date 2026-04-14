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

object NotificationHelper {
    private const val CHANNEL_ID = "averytask_reminders"
    private const val CHANNEL_NAME = "Task Reminders"
    private const val MED_CHANNEL_ID = "averytask_medication_reminders"
    private const val MED_CHANNEL_NAME = "Medication Reminders"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for upcoming tasks"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showTaskReminder(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDescription: String?
    ) {
        Log.d("NotificationHelper", "Showing notification for task=$taskId")
        createNotificationChannel(context)

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
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$taskTitle is coming up")
            .setContentText(taskDescription ?: "Ready when you are.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        val channel = NotificationChannel(
            MED_CHANNEL_ID,
            MED_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for medication and timed habits"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
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
        createMedicationChannel(context)

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
            .Builder(context, MED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        createMedicationChannel(context)

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
            .Builder(context, MED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$medName \u2014 Heads Up")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(stepId.hashCode() + 400_000, notification)
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
