package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotOverrideDao
import com.averycorp.prismtask.data.local.entity.MedicationEntity
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
 * **Two registration paths.** As of the per-(med, slot) audit fix:
 *  - **Slot-level alarm** at `slot.idealTime` whenever the slot resolves
 *    to CLOCK ignoring per-medication overrides. The notification names
 *    the slot only ("Morning Medications").
 *  - **Per-(medication, slot) alarm** at the override `idealTime` (or
 *    `slot.idealTime` if no override) when EITHER the pair has an
 *    explicit idealTime override that differs from the slot's own time
 *    OR the medication opts into CLOCK while the slot resolves to a
 *    non-CLOCK mode (so the slot-level alarm doesn't fire). The
 *    notification names the medication explicitly.
 *
 * The two paths are deliberately additive: a (med, slot) pair with a
 * differing override fires twice — once at the slot's ideal time
 * (slot-level "Morning Medications" reminding the user about the *other*
 * meds at that slot) and once at the override time (med-specific
 * reminder for the deviating med). When the pair only opts into CLOCK
 * (no idealTime override), only the per-(med, slot) alarm fires —
 * the slot-level alarm is suppressed by the resolver returning a
 * non-CLOCK mode.
 *
 * **Request-code namespace.** Base `700_000` per slot for slot-level
 * alarms; base `800_000 + (medId % 1000) * 1000 + (slotId % 1000)` for
 * per-(med, slot) alarms. Distinct from the `400_000` (per-med legacy),
 * `500_000` (slot INTERVAL), and `600_000` (per-med INTERVAL override)
 * namespaces.
 *
 * **Re-arm semantics.** AlarmManager exact alarms are one-shot, so
 * [onAlarmFired] (slot-level) and [onMedSlotAlarmFired] (per-pair)
 * re-register tomorrow's occurrence at the same wall clock time. The
 * receiver calls these from
 * [MedicationReminderReceiver.handleSlotClockAlarm] /
 * [MedicationReminderReceiver.handleMedSlotClockAlarm] before showing
 * the notification, so a process death between fire and re-register
 * doesn't leave the slot dark — the next [rescheduleAll] (boot, app
 * launch, settings save) repairs it.
 *
 * **Reactivity.** [start] subscribes to three Flow seams:
 *  - [MedicationSlotDao.observeAll] for slot insert/update/soft-delete/
 *    restore (sync-pulled or local).
 *  - [MedicationDao.getAll] for medication insert/archive/`reminderMode`
 *    edits — without this seam, flipping `medications.reminder_mode`
 *    between CLOCK and INTERVAL leaves the per-pair alarms stale.
 *  - [MedicationSlotOverrideDao.observeAll] for `medication_slot_overrides`
 *    edits — the override row lives in its own table, so neither slot
 *    nor med Flows fire when an override-only edit lands.
 */
@Singleton
class MedicationClockRescheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val medicationDao: MedicationDao,
    private val medicationSlotDao: MedicationSlotDao,
    private val medicationSlotOverrideDao: MedicationSlotOverrideDao,
    private val userPreferences: UserPreferencesDataStore
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * Cancel + re-register every CLOCK-mode slot alarm AND every
     * per-(med, slot) override alarm. Idempotent; call from
     * [BootReceiver], app launch, and settings-save paths.
     */
    suspend fun rescheduleAll() {
        val global = userPreferences.medicationReminderModeFlow.first()
        val slots = medicationSlotDao.getActiveOnce()
        val meds = medicationDao.getActiveOnce()
        val overrides = medicationSlotOverrideDao.getAllOnce()
            .associateBy { it.medicationId to it.slotId }

        // Cancel every alarm we might have registered. Per-(med, slot)
        // alarms get cancelled across the full meds × slots cross product
        // because we don't track which pairs were registered last pass —
        // a deleted override or a flipped med.reminderMode shouldn't
        // leave a stale alarm armed.
        for (slot in slots) cancelForSlot(slot.id)
        for (med in meds) {
            for (slot in slots) cancelForMedSlot(med.id, slot.id)
        }
        if (slots.isEmpty()) return

        val now = System.currentTimeMillis()

        // ── Slot-level CLOCK alarms (covers the no-override common case) ──
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

        // ── Per-(medication, slot) CLOCK alarms ──
        for (med in meds) {
            val linkedSlotIds = medicationSlotDao
                .getSlotIdsForMedicationOnce(med.id)
                .toSet()
            for (slot in slots) {
                if (slot.id !in linkedSlotIds) continue
                val pairMode = MedicationReminderModeResolver.resolveReminderMode(
                    medication = med,
                    slot = slot,
                    global = global
                )
                if (pairMode != MedicationReminderMode.CLOCK) continue

                val slotMode = MedicationReminderModeResolver.resolveReminderMode(
                    medication = null,
                    slot = slot,
                    global = global
                )
                val override = overrides[med.id to slot.id]
                if (
                    !needsPerMedAlarm(
                        overrideIdealTime = override?.overrideIdealTime,
                        slotIdealTime = slot.idealTime,
                        medReminderMode = med.reminderMode,
                        slotResolvedMode = slotMode
                    )
                ) {
                    continue
                }
                val effectiveTime = override?.overrideIdealTime ?: slot.idealTime
                val triggerMillis = nextTriggerForClock(effectiveTime, now) ?: continue
                registerAlarmForMedSlot(med, slot, triggerMillis)
            }
        }
    }

    /**
     * Wire Flow observers so any slot, medication, or override edit
     * triggers a fresh reschedule pass.
     *
     * No debounce: Room emits at most once per write transaction, and
     * the dominant cost in [rescheduleAll] is AlarmManager IPC. The
     * interval rescheduler made the same trade-off
     * (see [MedicationIntervalRescheduler.start]); medication / slot /
     * override edits are rare enough that even a small burst is fine.
     */
    fun start(scope: CoroutineScope = defaultScope) {
        medicationSlotDao.observeAll()
            .onEach { scope.launch { rescheduleAll() } }
            .launchIn(scope)
        medicationDao.getAll()
            .onEach { scope.launch { rescheduleAll() } }
            .launchIn(scope)
        medicationSlotOverrideDao.observeAll()
            .onEach { scope.launch { rescheduleAll() } }
            .launchIn(scope)
    }

    /**
     * Re-register tomorrow's slot-level alarm. Called by
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

    /**
     * Re-register tomorrow's per-(med, slot) alarm. Called by
     * [MedicationReminderReceiver.handleMedSlotClockAlarm] when the OS
     * delivers an alarm. Defensive against the medication being archived,
     * the slot deactivated, the override removed, or the resolved mode
     * flipped between scheduling and firing.
     */
    suspend fun onMedSlotAlarmFired(medicationId: Long, slotId: Long) {
        val med = medicationDao.getByIdOnce(medicationId) ?: return
        if (med.isArchived) return
        val slot = medicationSlotDao.getByIdOnce(slotId) ?: return
        if (!slot.isActive) return

        val global = userPreferences.medicationReminderModeFlow.first()
        val pairMode = MedicationReminderModeResolver.resolveReminderMode(
            medication = med,
            slot = slot,
            global = global
        )
        if (pairMode != MedicationReminderMode.CLOCK) return

        val slotMode = MedicationReminderModeResolver.resolveReminderMode(
            medication = null,
            slot = slot,
            global = global
        )
        val override = medicationSlotOverrideDao.getForPairOnce(medicationId, slotId)
        if (
            !needsPerMedAlarm(
                overrideIdealTime = override?.overrideIdealTime,
                slotIdealTime = slot.idealTime,
                medReminderMode = med.reminderMode,
                slotResolvedMode = slotMode
            )
        ) {
            return
        }
        val effectiveTime = override?.overrideIdealTime ?: slot.idealTime
        val triggerMillis = nextTriggerForClock(effectiveTime, System.currentTimeMillis())
            ?: return
        registerAlarmForMedSlot(med, slot, triggerMillis)
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

    private fun registerAlarmForMedSlot(
        med: MedicationEntity,
        slot: MedicationSlotEntity,
        triggerMillis: Long
    ) {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("medicationId", med.id)
            putExtra("clockSlotId", slot.id)
            putExtra("slotKey", "${med.id}:${slot.id}")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medSlotRequestCode(med.id, slot.id),
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

    private fun cancelForMedSlot(medicationId: Long, slotId: Long) {
        val intent = Intent(context, MedicationReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medSlotRequestCode(medicationId, slotId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)
    }

    companion object {
        internal const val SLOT_BASE_REQUEST_CODE = 700_000
        internal const val MED_SLOT_BASE_REQUEST_CODE = 800_000

        private val defaultScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun slotRequestCode(slotId: Long): Int =
            SLOT_BASE_REQUEST_CODE + (slotId % 1000L).toInt()

        /**
         * Encodes a (medicationId, slotId) pair into a stable request
         * code in the `800_000`-`1_799_999` range. The `% 1000` per
         * dimension matches the existing per-slot / per-med wrap-around
         * convention; collisions are bounded to pairs with both
         * `(medId mod 1000)` and `(slotId mod 1000)` equal — vanishingly
         * rare in practice. Callers should archive rather than
         * hard-delete medications so the encoding stays stable.
         */
        internal fun medSlotRequestCode(medicationId: Long, slotId: Long): Int =
            MED_SLOT_BASE_REQUEST_CODE +
                (medicationId % 1000L).toInt() * 1000 +
                (slotId % 1000L).toInt()

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

        /**
         * Pure helper. Returns true when a (medication, slot) pair needs
         * its own CLOCK alarm separate from the slot-level alarm. Two
         * cases trigger this:
         *
         *  - **Idealtime override differs from slot.** The pair has an
         *    explicit `overrideIdealTime` that differs from the slot's
         *    own `idealTime`. The slot-level alarm fires at the slot's
         *    time; the user wants this med to fire at a different time
         *    too. Both alarms are registered.
         *  - **Med opts into CLOCK over a non-CLOCK slot.** The
         *    medication's `reminderMode` is "CLOCK" while the slot
         *    resolves to a non-CLOCK mode (e.g. global=INTERVAL with the
         *    slot inheriting). The slot-level alarm doesn't fire, so
         *    this medication needs its own. Per-pair alarm registers at
         *    the slot's `idealTime` (overrides ignored in this branch
         *    only when `overrideIdealTime` is null — when both
         *    conditions apply, the override time wins).
         */
        internal fun needsPerMedAlarm(
            overrideIdealTime: String?,
            slotIdealTime: String,
            medReminderMode: String?,
            slotResolvedMode: MedicationReminderMode
        ): Boolean {
            val overrideDiffers =
                overrideIdealTime != null && overrideIdealTime != slotIdealTime
            val medOptsIntoClock =
                medReminderMode == MedicationReminderMode.CLOCK.name &&
                    slotResolvedMode != MedicationReminderMode.CLOCK
            return overrideDiffers || medOptsIntoClock
        }
    }
}
