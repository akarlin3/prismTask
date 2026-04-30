package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
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
 * **Medication path** — intent carries a `"medicationId"` extra. Fires for
 * alarms scheduled by the v1.4+ [MedicationReminderScheduler] that operates
 * on [com.averycorp.prismtask.data.local.entity.MedicationEntity].
 *
 * **Slot-interval path** — intent carries an `"intervalSlotId"` extra and
 * `medicationId == -1L`. Fires for INTERVAL-mode slot alarms registered by
 * [MedicationIntervalRescheduler.registerAlarmForSlot]. Re-anchoring is
 * handled by the rescheduler's dose-change Flow observer; this branch only
 * surfaces the notification.
 *
 * **Slot-clock path** — intent carries a `"clockSlotId"` extra and
 * `medicationId == -1L`. Fires for CLOCK-mode slot alarms registered by
 * [MedicationClockRescheduler.registerAlarmForSlot]. Tomorrow's
 * occurrence is re-armed from the receiver before the notification is
 * shown, since AlarmManager exact alarms are one-shot.
 *
 * Dispatch order is deliberate: medication > slot-clock > slot-interval >
 * habit. The interval rescheduler's per-medication-override path uses the
 * medication branch (it puts a real `medicationId`); only its per-slot path
 * uses the slot-interval branch.
 */
class MedicationReminderReceiver : BroadcastReceiver() {
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface MedReminderEntryPoint {
        fun habitReminderScheduler(): HabitReminderScheduler

        fun medicationReminderScheduler(): MedicationReminderScheduler

        fun medicationClockRescheduler(): MedicationClockRescheduler

        fun habitDao(): HabitDao

        fun medicationDao(): MedicationDao

        fun medicationSlotDao(): MedicationSlotDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("medicationId", -1L)
        val clockSlotId = intent.getLongExtra("clockSlotId", -1L)
        val intervalSlotId = intent.getLongExtra("intervalSlotId", -1L)
        val habitId = intent.getLongExtra("habitId", -1L)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MedReminderEntryPoint::class.java
        )

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                when (
                    val kind = classifyAlarm(
                        medicationId = medicationId,
                        clockSlotId = clockSlotId,
                        intervalSlotId = intervalSlotId,
                        habitId = habitId
                    )
                ) {
                    is AlarmKind.Medication ->
                        handleMedicationAlarm(context, intent, entryPoint, kind.medicationId)
                    is AlarmKind.SlotClock ->
                        handleSlotClockAlarm(context, entryPoint, kind.slotId)
                    is AlarmKind.SlotInterval ->
                        handleSlotIntervalAlarm(context, entryPoint, kind.slotId)
                    is AlarmKind.Habit ->
                        handleHabitAlarm(context, intent, entryPoint, kind.habitId)
                    AlarmKind.Unknown -> Log.w(
                        "MedReminderReceiver",
                        "Alarm fired with no recognised id extra " +
                            "(medId=$medicationId clockSlot=$clockSlotId " +
                            "intervalSlot=$intervalSlotId habitId=$habitId)"
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "MedReminderReceiver",
                    "Failed to process alarm " +
                        "medId=$medicationId clockSlot=$clockSlotId " +
                        "intervalSlot=$intervalSlotId habitId=$habitId",
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

    private suspend fun handleSlotIntervalAlarm(
        context: Context,
        entryPoint: MedReminderEntryPoint,
        slotId: Long
    ) {
        val slot = entryPoint.medicationSlotDao().getByIdOnce(slotId)
        if (slot == null || !slot.isActive) return

        // No self-re-register: MedicationIntervalRescheduler observes
        // medicationDoseDao.observeMostRecentDoseAny() and re-anchors the
        // chain whenever a dose lands. The notification's "Log" tap is
        // what produces that next emission.
        NotificationHelper.showSlotIntervalReminder(
            context = context,
            slotId = slotId,
            slotName = slot.name
        )
    }

    private suspend fun handleSlotClockAlarm(
        context: Context,
        entryPoint: MedReminderEntryPoint,
        slotId: Long
    ) {
        val slot = entryPoint.medicationSlotDao().getByIdOnce(slotId)
        if (slot == null || !slot.isActive) return

        // Re-arm tomorrow's wall-clock alarm before showing the
        // notification. AlarmManager exact alarms are one-shot, and
        // MedicationClockRescheduler does not observe a Flow that would
        // otherwise drive the next pass.
        entryPoint.medicationClockRescheduler().onAlarmFired(slotId)

        NotificationHelper.showSlotClockReminder(
            context = context,
            slotId = slotId,
            slotName = slot.name,
            idealTime = slot.idealTime
        )
    }

    companion object {
        @Suppress("MemberVisibilityCanBePrivate")
        internal sealed class AlarmKind {
            data class Medication(val medicationId: Long) : AlarmKind()

            data class SlotClock(val slotId: Long) : AlarmKind()

            data class SlotInterval(val slotId: Long) : AlarmKind()

            data class Habit(val habitId: Long) : AlarmKind()

            object Unknown : AlarmKind()
        }

        /**
         * Pure dispatch helper. Routes by id-extra presence in priority order
         * so legacy alarms (which set `habitId` only) never get pre-empted by
         * the newer slot paths.
         */
        internal fun classifyAlarm(
            medicationId: Long,
            clockSlotId: Long,
            intervalSlotId: Long,
            habitId: Long
        ): AlarmKind = when {
            medicationId >= 0 -> AlarmKind.Medication(medicationId)
            clockSlotId >= 0 -> AlarmKind.SlotClock(clockSlotId)
            intervalSlotId >= 0 -> AlarmKind.SlotInterval(intervalSlotId)
            habitId >= 0 -> AlarmKind.Habit(habitId)
            else -> AlarmKind.Unknown
        }
    }
}
