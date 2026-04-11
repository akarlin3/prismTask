package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1L)
        if (taskId == -1L) return

        val title = intent.getStringExtra("taskTitle") ?: "Gentle Nudge"
        val description = intent.getStringExtra("taskDescription")

        NotificationHelper.showTaskReminder(context, taskId, title, description)
    }
}
