package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1L)
        if (taskId == -1L) return

        val title = intent.getStringExtra("taskTitle") ?: "Gentle Nudge"
        val description = intent.getStringExtra("taskDescription")

        Log.d("ReminderReceiver", "Alarm fired for task=$taskId title=$title")

        // showTaskReminder reads DataStore preferences for delivery style;
        // goAsync() keeps the receiver alive while the suspend call runs on
        // the IO dispatcher instead of blocking the Main thread.
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                NotificationHelper.showTaskReminder(context, taskId, title, description)
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Failed to show reminder task=$taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
