package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.preferences.MedicationReminderMode
import com.averycorp.prismtask.data.preferences.MedicationReminderModePrefs
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore.Companion.MED_REMINDER_INTERVAL_MAX_MINUTES
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore.Companion.MED_REMINDER_INTERVAL_MIN_MINUTES

/**
 * Resolves the effective reminder mode and interval-minutes value for a
 * (medication, slot) pair against the three-level precedence chain:
 *
 *   1. Per-medication override (`medications.reminder_mode`)
 *   2. Per-slot override (`medication_slots.reminder_mode`)
 *   3. Global default (`UserPreferencesDataStore.medicationReminderModeFlow`)
 *
 * Either column being NULL at a given level means "inherit the next level
 * down." The resolver is a pure function — every reminder-scheduling path
 * (boot, reactive rescheduler, settings preview) calls into it so the
 * resolved value is consistent everywhere.
 *
 * Defensive parsing: an unknown `reminder_mode` string anywhere in the
 * chain is treated as NULL (inherit). An INTERVAL resolution missing an
 * `interval_minutes` value at every level falls back to the global
 * default — never null. A non-positive interval is clamped to the
 * preferences min/max.
 */
object MedicationReminderModeResolver {

    /**
     * Resolve the mode for a (medication, slot, global). [medication] may
     * be null when scheduling at slot level (no specific medication
     * pinned), in which case only slot + global are consulted.
     */
    fun resolveReminderMode(
        medication: MedicationEntity?,
        slot: MedicationSlotEntity?,
        global: MedicationReminderModePrefs
    ): MedicationReminderMode {
        medication?.reminderMode?.let { parseMode(it)?.let { mode -> return mode } }
        slot?.reminderMode?.let { parseMode(it)?.let { mode -> return mode } }
        return global.mode
    }

    /**
     * Resolve interval-minutes. Only meaningful when the resolved mode is
     * INTERVAL — callers in CLOCK mode should ignore this. Always returns
     * a value clamped to `[60, 1440]`.
     */
    fun resolveIntervalMinutes(
        medication: MedicationEntity?,
        slot: MedicationSlotEntity?,
        global: MedicationReminderModePrefs
    ): Int {
        val raw = medication?.reminderIntervalMinutes
            ?: slot?.reminderIntervalMinutes
            ?: global.intervalDefaultMinutes
        return raw.coerceIn(MED_REMINDER_INTERVAL_MIN_MINUTES, MED_REMINDER_INTERVAL_MAX_MINUTES)
    }

    private fun parseMode(raw: String): MedicationReminderMode? = when (raw) {
        MedicationReminderMode.CLOCK.name -> MedicationReminderMode.CLOCK
        MedicationReminderMode.INTERVAL.name -> MedicationReminderMode.INTERVAL
        else -> null
    }
}
