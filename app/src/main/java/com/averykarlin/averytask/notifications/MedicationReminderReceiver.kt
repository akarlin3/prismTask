package com.averykarlin.averytask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedicationReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra("habitId", -1L)
        if (habitId == -1L) return

        val name = intent.getStringExtra("habitName") ?: "Medication Reminder"
        val description = intent.getStringExtra("habitDescription")
        val intervalMillis = intent.getLongExtra("intervalMillis", 0L)
        val doseNumber = intent.getIntExtra("doseNumber", 0)
        val totalDoses = intent.getIntExtra("totalDoses", 1)

        NotificationHelper.showMedicationReminder(context, habitId, name, description, intervalMillis, doseNumber, totalDoses)
    }
}
