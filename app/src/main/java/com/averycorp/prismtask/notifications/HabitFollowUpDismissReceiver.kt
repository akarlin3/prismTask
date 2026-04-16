package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the user swiping away a habit follow-up notification.
 * Cancels any pending follow-up alarm so it doesn't re-fire.
 */
class HabitFollowUpDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(HabitFollowUpReceiver.EXTRA_HABIT_ID, -1L)
        if (habitId == -1L) return

        cancelFollowUp(context, habitId)
    }

    companion object {
        fun cancelFollowUp(context: Context, habitId: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val cancelIntent = Intent(context, HabitFollowUpReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                habitId.toInt() + HabitFollowUpReceiver.FOLLOW_UP_REQUEST_CODE_OFFSET,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
