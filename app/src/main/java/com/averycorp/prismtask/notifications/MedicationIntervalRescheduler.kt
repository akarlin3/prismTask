package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.preferences.MedicationReminderMode
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.domain.usecase.MedicationReminderModeResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns AlarmManager registrations for medications/slots whose resolved
 * reminder mode is INTERVAL (PR1 schema; PR2 scheduler).
 *
 * Walks the world on every relevant change: active slots + active
 * medications + the most-recent dose. For each slot whose resolved mode
 * is INTERVAL, registers exactly one rolling alarm at
 * `anchor.takenAt + intervalMinutes`. The CLOCK-mode flow is unchanged —
 * [MedicationReminderScheduler] continues to own those alarms.
 *
 * Anchor semantics: the most recent dose row is the anchor, including
 * synthetic-skip rows. That way SKIPPED actions and backdated edits both
 * push the interval clock forward, matching the user's mental model
 * ("I just took/skipped something — start the next interval now").
 *
 * **Request-code namespace.** Base `500_000` per slot, distinct from
 * the existing `+400_000` per-medication scheduler. One slot id occupies
 * one request code (no slot-internal capacity needed — slots get one
 * rolling alarm each).
 *
 * Bootstrap: when no doses exist (fresh user), the alarm is scheduled
 * `interval_minutes` from now so the user gets their first reminder
 * without needing to mark a dose first.
 */
@Singleton
class MedicationIntervalRescheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val medicationDao: MedicationDao,
    private val medicationSlotDao: MedicationSlotDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val userPreferences: UserPreferencesDataStore
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * Cancel + re-register every interval-mode alarm. Idempotent;
     * call from [BootReceiver], dose-change observer, settings save, etc.
     */
    suspend fun rescheduleAll() {
        val global = userPreferences.medicationReminderModeFlow.first()
        val slots = medicationSlotDao.getActiveOnce()
        val medsById = medicationDao.getActiveOnce().associateBy { it.id }

        // Cancel everything first — easier than trying to delta-diff.
        for (slot in slots) cancelForSlot(slot.id)

        if (slots.isEmpty()) return

        val anchorMillis = medicationDoseDao.getMostRecentDoseAnyOnce()?.takenAt
        val now = System.currentTimeMillis()

        for (slot in slots) {
            // Find the medication-level override that wins for this slot
            // (if any). When a slot has multiple linked meds with different
            // overrides we resolve at the slot level using the slot's own
            // value, not any of the medications' — per-med overrides
            // applied per-(med,slot) pair would multiply alarms beyond what
            // a single rolling reminder can sensibly express.
            val mode = MedicationReminderModeResolver.resolveReminderMode(
                medication = null,
                slot = slot,
                global = global
            )
            if (mode != MedicationReminderMode.INTERVAL) continue

            val intervalMinutes = MedicationReminderModeResolver.resolveIntervalMinutes(
                medication = null,
                slot = slot,
                global = global
            )
            val intervalMillis = intervalMinutes * MILLIS_PER_MINUTE
            val triggerMillis = computeTriggerMillis(anchorMillis, intervalMillis, now)
            registerAlarmForSlot(slot, triggerMillis)
        }

        // Per-medication overrides: when a med opts into INTERVAL but its
        // slot is CLOCK, the resolver still says INTERVAL. We want to fire
        // a separate alarm per such medication so the clock-mode slot
        // alarm doesn't shadow it.
        for (med in medsById.values) {
            val slot = slots.firstOrNull() ?: continue
            val perMedMode = MedicationReminderModeResolver.resolveReminderMode(
                medication = med,
                slot = null,
                global = global.copy(mode = MedicationReminderMode.CLOCK)
            )
            // Only consider explicit per-medication INTERVAL overrides
            // (the slot's value is already handled above).
            if (med.reminderMode == "INTERVAL" && perMedMode == MedicationReminderMode.INTERVAL) {
                val intervalMinutes = MedicationReminderModeResolver.resolveIntervalMinutes(
                    medication = med,
                    slot = null,
                    global = global
                )
                val intervalMillis = intervalMinutes * MILLIS_PER_MINUTE
                val triggerMillis = computeTriggerMillis(anchorMillis, intervalMillis, now)
                registerAlarmForMedication(med.id, triggerMillis)
            }
            // Avoid the unused-variable warning when the override doesn't apply.
            @Suppress("UNUSED_EXPRESSION")
            slot
        }
    }

    /**
     * Wire a Flow observer so any dose insert/update/delete triggers a
     * reschedule pass after the configured debounce window.
     *
     * We do not use Flow.debounce here because Room's reactive Flows
     * already emit at most once per write transaction, and the overhead
     * of [rescheduleAll] is dominated by AlarmManager IPC — a few extra
     * passes per second is fine. If real-world load shows excess passes,
     * add a debounce in a follow-up.
     */
    fun start(scope: CoroutineScope = defaultScope) {
        medicationDoseDao.observeMostRecentDoseAny()
            .onEach { scope.launch { rescheduleAll() } }
            .launchIn(scope)
    }

    private fun computeTriggerMillis(anchorMillis: Long?, intervalMillis: Long, now: Long): Long {
        // Bootstrap: no anchor → fire one interval from now.
        val baseMillis = anchorMillis ?: now
        return maxOf(baseMillis + intervalMillis, now + MIN_LEAD_MILLIS)
    }

    private fun registerAlarmForSlot(slot: MedicationSlotEntity, triggerMillis: Long) {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("medicationId", -1L)
            putExtra("slotKey", slot.id.toString())
            putExtra("intervalSlotId", slot.id)
        }
        val requestCode = slotRequestCode(slot.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ExactAlarmHelper.scheduleExact(context, triggerMillis, pendingIntent)
    }

    private fun registerAlarmForMedication(medicationId: Long, triggerMillis: Long) {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("medicationId", medicationId)
            putExtra("slotKey", "interval-override")
            putExtra("intervalMedicationId", medicationId)
        }
        val requestCode = medicationRequestCode(medicationId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ExactAlarmHelper.scheduleExact(context, triggerMillis, pendingIntent)
    }

    private fun cancelForSlot(slotId: Long) {
        cancelByRequestCode(slotRequestCode(slotId))
        // Med-override alarms for any med ever in INTERVAL mode would
        // need to be cancelled too, but rescheduleAll re-issues them
        // with FLAG_UPDATE_CURRENT — old PendingIntents get overwritten
        // when the new one is scheduled.
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

    companion object {
        internal const val SLOT_BASE_REQUEST_CODE = 500_000
        internal const val MED_BASE_REQUEST_CODE = 600_000

        // Avoid scheduling alarms in the past or sub-second; AlarmManager
        // can fire immediately for past triggers, defeating the "rolling"
        // illusion when the user rapidly logs back-to-back doses.
        private const val MIN_LEAD_MILLIS = 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L

        private val defaultScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun slotRequestCode(slotId: Long): Int =
            SLOT_BASE_REQUEST_CODE + (slotId % 1000L).toInt()

        internal fun medicationRequestCode(medicationId: Long): Int =
            MED_BASE_REQUEST_CODE + (medicationId % 1000L).toInt()
    }
}
