package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val alarmKind = intent.getStringExtra(MedicationReminderScheduler.EXTRA_ALARM_KIND)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MedReminderEntryPoint::class.java
        )

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val scheduler = entryPoint.medicationReminderScheduler()
                val habit = entryPoint.habitDao().getHabitByIdOnce(habitId)

                // Daily-time alarms re-register tomorrow's occurrence as
                // soon as they fire, since AlarmManager exact alarms are
                // one-shot. If the habit was archived or had its reminder
                // cleared between scheduling and firing, the re-read
                // above surfaces the current state and we skip the
                // re-register.
                if (alarmKind == MedicationReminderScheduler.ALARM_KIND_DAILY_TIME) {
                    if (habit != null &&
                        !habit.isArchived &&
                        habit.reminderTime != null &&
                        habit.reminderIntervalMillis == null
                    ) {
                        scheduler.scheduleDailyTime(habit)
                    }
                }

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
            } catch (e: Exception) {
                Log.e("MedReminderReceiver", "Failed to process med reminder $habitId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
