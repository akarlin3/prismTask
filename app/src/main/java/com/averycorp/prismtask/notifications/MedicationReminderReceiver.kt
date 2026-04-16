package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MedicationReminderReceiver : BroadcastReceiver() {
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface MedReminderEntryPoint {
        fun medicationReminderScheduler(): MedicationReminderScheduler

        fun habitDao(): com.averycorp.prismtask.data.local.dao.HabitDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra("habitId", -1L)
        if (habitId == -1L) return

        val name = intent.getStringExtra("habitName") ?: "Medication Reminder"
        val description = intent.getStringExtra("habitDescription")
        val intervalMillis = intent.getLongExtra("intervalMillis", 0L)
        val doseNumber = intent.getIntExtra("doseNumber", 0)
        val totalDoses = intent.getIntExtra("totalDoses", 1)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MedReminderEntryPoint::class.java
        )

        @Suppress("GlobalCoroutineUsage")
        GlobalScope.launch {
            val scheduler = entryPoint.medicationReminderScheduler()
            val habit = entryPoint.habitDao().getHabitByIdOnce(habitId)

            // Check if nag should be suppressed in favor of a delayed follow-up
            if (habit != null) {
                val followUpTime = scheduler.getFollowUpTimeIfSuppressed(habit)
                if (followUpTime != null) {
                    scheduler.scheduleDelayedHabitFollowUp(habitId, name, followUpTime)
                    return@launch
                }
            }

            // No suppression — fire the nag notification as normal
            NotificationHelper.showMedicationReminder(
                context,
                habitId,
                name,
                description,
                intervalMillis,
                doseNumber,
                totalDoses
            )
        }
    }
}
