package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedStepReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stepId = intent.getStringExtra("stepId") ?: return
        val medName = intent.getStringExtra("medName") ?: "Medication"
        val medNote = intent.getStringExtra("medNote") ?: ""

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                NotificationHelper.showMedStepReminder(context, stepId, medName, medNote)
            } catch (e: Exception) {
                Log.e("MedStepReceiver", "Failed to show med step reminder $stepId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
