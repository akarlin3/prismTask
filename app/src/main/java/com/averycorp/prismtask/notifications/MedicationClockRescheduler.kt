package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.preferences.MedicationReminderMode
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.domain.usecase.MedicationReminderModeResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns AlarmManager registrations for medication slots whose resolved
 * reminder mode is CLOCK. Symmetric with [MedicationIntervalRescheduler]:
 * walks active slots on every relevant change, registers exactly one
 * wall-clock alarm per slot at `slot.idealTime`, and re-arms tomorrow's
 * occurrence whenever the alarm fires.
 *
 * The legacy [MedicationReminderScheduler] still owns per-medication
 * alarms when `MedicationEntity.scheduleMode` is populated
 * (`TIMES_OF_DAY` / `SPECIFIC_TIMES` / `INTERVAL`). Those are independent
 * of the resolver's `reminderMode` and were the only CLOCK path before
 * this scheduler landed — but the medication editor never sets those
 * fields, so a fresh-install user without a legacy migration would have
 * received zero CLOCK reminders. This scheduler closes that gap by
 * making slots the source of truth.
 *
 * **Request-code namespace.** Base `700_000` per slot, distinct from the
 * `400_000` (per-med legacy), `500_000` (slot INTERVAL), and `600_000`
 * (per-med INTERVAL override) namespaces.
 *
 * **Re-arm semantics.** AlarmManager exact alarms are one-shot, so
 * [onAlarmFired] re-registers tomorrow's occurrence at the same wall
 * clock time. The receiver calls [onAlarmFired] from
 * [MedicationReminderReceiver.handleSlotClockAlarm] before showing the
 * notification, so a process death between fire and re-register doesn't
 * leave the slot dark — the next [rescheduleAll] (boot, app launch,
 * settings save) repairs it.
 */
@Singleton
class MedicationClockRescheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val medicationSlotDao: MedicationSlotDao,
    private val userPreferences: UserPreferencesDataStore
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * Cancel + re-register every CLOCK-mode slot alarm. Idempotent;
     * call from [BootReceiver], app launch, and settings-save paths.
     */
    suspend fun rescheduleAll() {
        val global = userPreferences.medicationReminderModeFlow.first()
        val slots = medicationSlotDao.getActiveOnce()

        for (slot in slots) cancelForSlot(slot.id)
        if (slots.isEmpty()) return

        val now = System.currentTimeMillis()
        for (slot in slots) {
            val mode = MedicationReminderModeResolver.resolveReminderMode(
                medication = null,
                slot = slot,
                global = global
            )
            if (mode != MedicationReminderMode.CLOCK) continue
            val triggerMillis = nextTriggerForClock(slot.idealTime, now) ?: continue
            registerAlarmForSlot(slot, triggerMillis)
        }
    }

    /**
     * Re-register tomorrow's alarm. Called by
     * [MedicationReminderReceiver.handleSlotClockAlarm] when the OS
     * delivers an alarm. Defensive against the slot being deactivated or
     * flipped to INTERVAL between scheduling and firing.
     */
    suspend fun onAlarmFired(slotId: Long) {
        val slot = medicationSlotDao.getByIdOnce(slotId) ?: return
        if (!slot.isActive) return

        val global = userPreferences.medicationReminderModeFlow.first()
        val mode = MedicationReminderModeResolver.resolveReminderMode(
            medication = null,
            slot = slot,
            global = global
        )
        if (mode != MedicationReminderMode.CLOCK) return

        val triggerMillis = nextTriggerForClock(slot.idealTime, System.currentTimeMillis())
            ?: return
        registerAlarmForSlot(slot, triggerMillis)
    }

    private fun registerAlarmForSlot(slot: MedicationSlotEntity, triggerMillis: Long) {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("medicationId", -1L)
            putExtra("clockSlotId", slot.id)
            putExtra("slotKey", slot.id.toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            slotRequestCode(slot.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ExactAlarmHelper.scheduleExact(context, triggerMillis, pendingIntent)
    }

    private fun cancelForSlot(slotId: Long) {
        val intent = Intent(context, MedicationReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            slotRequestCode(slotId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)
    }

    companion object {
        internal const val SLOT_BASE_REQUEST_CODE = 700_000

        internal fun slotRequestCode(slotId: Long): Int =
            SLOT_BASE_REQUEST_CODE + (slotId % 1000L).toInt()

        /**
         * Pure helper. Returns the next millis at which a wall-clock alarm
         * for [idealTime] (`"HH:mm"`) should fire — today if still in the
         * future, tomorrow otherwise. Returns null on malformed input.
         */
        internal fun nextTriggerForClock(idealTime: String, now: Long): Long? {
            val parts = idealTime.split(':')
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
    }
}
