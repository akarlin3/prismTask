package com.averycorp.prismtask.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Fires a delayed follow-up notification after a habit's scheduled time
 * has passed. The text is softer than the original nag: "How did it go?"
 *
 * Tagged with notification ID `habit_followup_<habitId>` so it can be
 * cancelled if the habit is completed before the follow-up fires.
 */
class HabitFollowUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (habitId == -1L) return

        val habitName = intent.getStringExtra(EXTRA_HABIT_NAME) ?: "Habit"

        val prefs = NotificationPreferences.from(context)
        val enabled = runBlocking { prefs.medicationRemindersEnabled.first() }
        if (!enabled) return

        // Reuse the medication channel via the public importance-based API
        NotificationHelper.createNotificationChannel(context)
        val importance = runBlocking { prefs.getImportanceOnce() }
        val channelId = NotificationHelper.channelIdFor(BASE_MED_CHANNEL_ID, importance)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            habitId.toInt() + FOLLOW_UP_REQUEST_CODE_OFFSET,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, HabitFollowUpDismissReceiver::class.java).apply {
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        val cancelPending = PendingIntent.getBroadcast(
            context,
            habitId.toInt() + FOLLOW_UP_DISMISS_OFFSET,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$habitName \u2014 How Did It Go?")
            .setContentText("Your scheduled time has passed. Tap to log.")
            .setPriority(NotificationHelper.importanceToBuilderPriority(importance))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .setDeleteIntent(cancelPending)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(followUpNotificationId(habitId), builder.build())
    }

    companion object {
        const val EXTRA_HABIT_ID = "habitId"
        const val EXTRA_HABIT_NAME = "habitName"
        const val FOLLOW_UP_REQUEST_CODE_OFFSET = 700_000
        const val FOLLOW_UP_DISMISS_OFFSET = 800_000
        private const val BASE_MED_CHANNEL_ID = "prismtask_medication_reminders"

        fun followUpNotificationId(habitId: Long): Int = habitId.toInt() + FOLLOW_UP_REQUEST_CODE_OFFSET
    }
}
