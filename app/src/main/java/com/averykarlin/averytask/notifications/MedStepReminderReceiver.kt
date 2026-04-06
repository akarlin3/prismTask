package com.averykarlin.averytask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedStepReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val stepId = intent.getStringExtra("stepId") ?: return
        val medName = intent.getStringExtra("medName") ?: "Medication"
        val medNote = intent.getStringExtra("medNote") ?: ""

        NotificationHelper.showMedStepReminder(context, stepId, medName, medNote)
    }
}
