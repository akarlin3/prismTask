package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.MedicationDao
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Unified broadcast receiver for habit and medication reminder alarms.
 *
 * **Legacy (habit) path** — intent carries a `"habitId"` extra. This was
 * the original path when the scheduler was named `MedicationReminderScheduler`
 * (pre v1.4 medication-top-level refactor). Still active — habits and
 * the transitional `MedicationPreferences.specificTimes` flow both use it.
 *
 * **New (medication) path** — intent carries a `"medicationId"` extra. Fires
 * for alarms scheduled by the v1.4+ [MedicationReminderScheduler] that
 * operates on [com.averycorp.prismtask.data.local.entity.MedicationEntity].
 *
 * Dispatch is by extra presence; legacy and new alarms coexist during the
 * 2-week convergence window so existing PendingIntents don't get orphaned.
 */
class MedicationReminderReceiver : BroadcastReceiver() {
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface MedReminderEntryPoint {
        fun habitReminderScheduler(): HabitReminderScheduler

        fun medicationReminderScheduler(): MedicationReminderScheduler

        fun habitDao(): HabitDao

        fun medicationDao(): MedicationDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("medicationId", -1L)
        val habitId = intent.getLongExtra("habitId", -1L)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MedReminderEntryPoint::class.java
        )

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                when {
                    medicationId >= 0 -> handleMedicationAlarm(context, intent, entryPoint, medicationId)
                    habitId >= 0 -> handleHabitAlarm(context, intent, entryPoint, habitId)
                    else -> Log.w(
                        "MedReminderReceiver",
                        "Alarm fired with neither habitId nor medicationId extra"
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "MedReminderReceiver",
                    "Failed to process alarm medId=$medicationId habitId=$habitId",
                    e
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleHabitAlarm(
        context: Context,
        intent: Intent,
        entryPoint: MedReminderEntryPoint,
        habitId: Long
    ) {
        val name = intent.getStringExtra("habitName") ?: "Medication Reminder"
        val description = intent.getStringExtra("habitDescription")
        val intervalMillis = intent.getLongExtra("intervalMillis", 0L)
        val doseNumber = intent.getIntExtra("doseNumber", 0)
        val totalDoses = intent.getIntExtra("totalDoses", 1)
        val alarmKind = intent.getStringExtra(HabitReminderScheduler.EXTRA_ALARM_KIND)

        val scheduler = entryPoint.habitReminderScheduler()
        val habit = entryPoint.habitDao().getHabitByIdOnce(habitId)

        // Daily-time alarms re-register tomorrow's occurrence as
        // soon as they fire, since AlarmManager exact alarms are
        // one-shot. If the habit was archived or had its reminder
        // cleared between scheduling and firing, the re-read
        // above surfaces the current state and we skip the
        // re-register.
        if (alarmKind == HabitReminderScheduler.ALARM_KIND_DAILY_TIME) {
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
                return
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

    private suspend fun handleMedicationAlarm(
        context: Context,
        intent: Intent,
        entryPoint: MedReminderEntryPoint,
        medicationId: Long
    ) {
        val slotKey = intent.getStringExtra("slotKey") ?: "anytime"
        val med = entryPoint.medicationDao().getByIdOnce(medicationId)
        if (med == null || med.isArchived) return

        // Per-medication alarms self-re-register on the new scheduler.
        entryPoint.medicationReminderScheduler().onAlarmFired(medicationId, slotKey)

        NotificationHelper.showMedicationReminder(
            context,
            medicationId,
            med.displayLabel ?: med.name,
            med.notes.ifBlank { null },
            0L,
            0,
            1
        )
    }
}
