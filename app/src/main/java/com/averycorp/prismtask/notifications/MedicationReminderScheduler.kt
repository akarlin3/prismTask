package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.preferences.MedicationReminderMode
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.domain.usecase.MedicationReminderModeResolver
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules per-medication alarms for the top-level Medication entity
 * (v1.4+; spec: `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §5).
 *
 * Schedule modes (see [MedicationEntity.scheduleMode]):
 *  - `TIMES_OF_DAY` — one alarm per time-of-day bucket in
 *    [MedicationEntity.timesOfDay]; buckets map to clock times per
 *    [MEDICATION_TIME_OF_DAY_CLOCK].
 *  - `SPECIFIC_TIMES` — one alarm per `"HH:mm"` in
 *    [MedicationEntity.specificTimes].
 *  - `INTERVAL` — one chained alarm fired [MedicationEntity.intervalMillis]
 *    after the most recent dose. Re-registered by
 *    [onAlarmFired] when the alarm delivers.
 *  - `AS_NEEDED` — no alarms.
 *
 * **Request-code namespace.** Base `400_000` + slot-offset, distinct from
 * the legacy `200_000`/`300_000`/`900_000` offsets
 * [HabitReminderScheduler] uses. Every medication reserves 10 consecutive
 * slot codes; up to 1000 active medications fit cleanly. Callers should
 * archive rather than hard-delete so slot codes remain stable.
 */
@Singleton
class MedicationReminderScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val medicationDao: MedicationDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val medicationSlotDao: MedicationSlotDao,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val userPreferences: UserPreferencesDataStore
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * Registers every alarm the medication's schedule implies. Idempotent
     * per medication — call [cancelForMedication] first if a schedule
     * change needs to fire fresh.
     *
     * Wall-clock paths (`TIMES_OF_DAY`, `SPECIFIC_TIMES`) are skipped when
     * the resolved reminder mode is INTERVAL — the per-medication-override
     * alarm fires from [MedicationIntervalRescheduler] instead, and we'd
     * otherwise double-fire. The `INTERVAL` schedule-mode path keeps
     * running so legacy migrated medications with a populated
     * `intervalMillis` continue to work.
     */
    suspend fun scheduleForMedication(med: MedicationEntity) {
        if (med.isArchived) return
        // Slots are canonical when present (NotificationProjector.kt:243
        // already gates legacy projections the same way). Sync-pulled
        // medications carry both legacy `timesOfDay`/`specificTimes` AND
        // linked slots; without this guard both schedulers fire and the
        // user sees duplicate notifications.
        if (medicationSlotDao.getSlotIdsForMedicationOnce(med.id).isNotEmpty()) return
        val mode = resolvedReminderMode(med)
        when (med.scheduleMode) {
            "TIMES_OF_DAY" -> if (mode == MedicationReminderMode.CLOCK) scheduleTimesOfDay(med)
            "SPECIFIC_TIMES" -> if (mode == MedicationReminderMode.CLOCK) scheduleSpecificTimes(med)
            "INTERVAL" -> scheduleInterval(med)
            // AS_NEEDED and anything else: no alarms.
        }
    }

    private suspend fun resolvedReminderMode(med: MedicationEntity): MedicationReminderMode {
        val global = userPreferences.medicationReminderModeFlow.first()
        return MedicationReminderModeResolver.resolveReminderMode(
            medication = med,
            slot = null,
            global = global
        )
    }

    /** Cancels every slot alarm for the medication. */
    fun cancelForMedication(medicationId: Long) {
        val base = baseRequestCode(medicationId)
        for (slotIndex in 0 until SLOT_CAPACITY) {
            cancelByRequestCode(base + slotIndex)
        }
    }

    /** Re-registers every active medication's schedule. Used by [BootReceiver]. */
    suspend fun rescheduleAll() {
        for (med in medicationDao.getActiveOnce()) {
            cancelForMedication(med.id)
            scheduleForMedication(med)
        }
    }

    /**
     * Called by [MedicationReminderReceiver] when an alarm fires so the
     * scheduler can re-register the next occurrence. For fixed-time slots
     * the next occurrence is tomorrow; for interval mode it is
     * [MedicationEntity.intervalMillis] after the current wall-clock now.
     */
    suspend fun onAlarmFired(medicationId: Long, slotKey: String) {
        val med = medicationDao.getByIdOnce(medicationId) ?: return
        if (med.isArchived) return
        when (med.scheduleMode) {
            "TIMES_OF_DAY", "SPECIFIC_TIMES" -> {
                val slotIndex = resolveSlotIndex(med, slotKey)
                val triggerMillis = when (med.scheduleMode) {
                    "TIMES_OF_DAY" -> nextTriggerForTimeOfDay(slotKey)
                    else -> nextTriggerForSpecificTime(slotKey)
                } ?: return
                registerAlarm(med, slotKey, triggerMillis, slotIndex)
            }
            "INTERVAL" -> scheduleInterval(med)
        }
    }

    // --- mode-specific schedulers -------------------------------------

    private fun scheduleTimesOfDay(med: MedicationEntity) {
        val slots = parseTimesOfDay(med.timesOfDay)
        for ((index, slot) in slots.withIndex()) {
            val triggerMillis = nextTriggerForTimeOfDay(slot) ?: continue
            registerAlarm(med, slot, triggerMillis, index)
        }
    }

    private fun scheduleSpecificTimes(med: MedicationEntity) {
        val slots = parseSpecificTimes(med.specificTimes)
        for ((index, slot) in slots.withIndex()) {
            val triggerMillis = nextTriggerForSpecificTime(slot) ?: continue
            registerAlarm(med, slot, triggerMillis, index)
        }
    }

    private suspend fun scheduleInterval(med: MedicationEntity) {
        val intervalMillis = med.intervalMillis ?: return
        if (intervalMillis <= 0) return
        val lastDose = medicationDoseDao.getLatestForMedOnce(med.id)
        val baseMillis = lastDose?.takenAt ?: System.currentTimeMillis()
        val triggerMillis = maxOf(
            baseMillis + intervalMillis,
            System.currentTimeMillis() + 1000L
        )
        registerAlarm(med, "interval", triggerMillis, INTERVAL_SLOT_INDEX)
    }

    // --- alarm registration helpers -----------------------------------

    private fun registerAlarm(
        med: MedicationEntity,
        slotKey: String,
        triggerMillis: Long,
        slotIndex: Int
    ) {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("medicationId", med.id)
            putExtra("slotKey", slotKey)
        }
        val requestCode = baseRequestCode(med.id) + slotIndex.coerceIn(0, SLOT_CAPACITY - 1)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ExactAlarmHelper.scheduleExact(context, triggerMillis, pendingIntent)
    }

    private fun cancelByRequestCode(requestCode: Int) {
        val intent = Intent(context, MedicationReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)
    }

    // --- pure helpers (unit-testable via the companion) ---------------

    private fun parseTimesOfDay(raw: String?): List<String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in MEDICATION_TIME_OF_DAY_CLOCK }
            .distinct()

    private fun parseSpecificTimes(raw: String?): List<String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { isValidClockString(it) }
            .distinct()
            .sorted()

    private fun resolveSlotIndex(med: MedicationEntity, slotKey: String): Int =
        when (med.scheduleMode) {
            "TIMES_OF_DAY" -> parseTimesOfDay(med.timesOfDay)
                .indexOf(slotKey.lowercase())
                .let { if (it >= 0) it else 0 }
            "SPECIFIC_TIMES" -> parseSpecificTimes(med.specificTimes)
                .indexOf(slotKey)
                .let { if (it >= 0) it else 0 }
            else -> INTERVAL_SLOT_INDEX
        }

    private fun nextTriggerForTimeOfDay(slot: String): Long? {
        val clock = MEDICATION_TIME_OF_DAY_CLOCK[slot.lowercase()] ?: return null
        return nextTriggerForSpecificTime(clock)
    }

    private fun nextTriggerForSpecificTime(clock: String): Long? {
        val parts = clock.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** Surface only to drive non-Android unit tests of the day-hour helper. */
    internal suspend fun currentLogicalDateString(): String {
        val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
        return DayBoundary.currentLocalDateString(dayStartHour)
    }

    companion object {
        internal const val BASE_REQUEST_CODE = 400_000
        internal const val SLOT_CAPACITY = 10
        private const val INTERVAL_SLOT_INDEX = 9

        internal fun baseRequestCode(medicationId: Long): Int =
            BASE_REQUEST_CODE + ((medicationId % 1000L).toInt()) * SLOT_CAPACITY

        internal fun isValidClockString(raw: String): Boolean {
            val parts = raw.split(':')
            if (parts.size != 2) return false
            val hour = parts[0].toIntOrNull() ?: return false
            val minute = parts[1].toIntOrNull() ?: return false
            return hour in 0..23 && minute in 0..59
        }
    }
}
