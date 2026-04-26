package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.core.time.LocalDateFlow
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is any medication dose due today and not yet
 * taken." Post v1.4 medication-top-level refactor (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §6.5), reads exclusively from
 * [MedicationDao] + [MedicationDoseDao] — the three-way HabitRepository /
 * SelfCareDao / MedicationPreferences scatter is gone.
 *
 * Each active [MedicationEntity] expands into one or more `MedicationDose`
 * entries based on its `scheduleMode`:
 *  - `TIMES_OF_DAY` → one dose per time-of-day slot in `timesOfDay`.
 *  - `SPECIFIC_TIMES` → one dose per `"HH:mm"` in `specificTimes`.
 *  - `INTERVAL` → one dose with `slotKey = "anytime"`.
 *  - `AS_NEEDED` → no doses (user logs ad-hoc).
 *
 * A dose is `takenToday=true` when at least one [MedicationDoseEntity]
 * exists for this `(medicationId, slotKey)` on the current logical day —
 * the day source is [LocalDateFlow], which re-keys the DAO query at every
 * SoD boundary crossing.
 */
enum class DoseSource { INTERVAL_HABIT, SELF_CARE_STEP, SPECIFIC_TIME }

data class MedicationDose(
    val medicationName: String,
    val displayLabel: String,
    val source: DoseSource,
    /** null for interval-based schedules; wall-clock millis for scheduled doses */
    val scheduledAt: Long?,
    val takenToday: Boolean,
    val linkedHabitId: Long?,
    /**
     * Time-slot identity used by the Daily Essentials grouping layer. Either a
     * ``"HH:mm"`` wall-clock time (for specific-time doses or time-of-day
     * buckets via [MedicationSlotGrouper.slotKeyForTimeOfDay]) or
     * [MedicationSlotGrouper.ANYTIME_KEY] for interval-based doses.
     */
    val slotKey: String? = null,
    /**
     * Retained for daily_essential_slot_completions compatibility, but no
     * longer populated post v1.4 — the field stays nullable so stored dose
     * keys formed pre-rewrite continue to round-trip.
     */
    val selfCareStepId: String? = null
) {
    /** Stable synthetic identifier the slot layer uses inside ``med_ids_json``. */
    val doseKey: String
        get() = "${source.name.lowercase()}:${medicationName.trim().lowercase()}"
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MedicationStatusUseCase
@Inject
constructor(
    private val medicationDao: MedicationDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val localDateFlow: LocalDateFlow
) {
    /**
     * Emits the list of doses that are *due today and not yet taken*. Emits
     * an empty list once every schedule is satisfied so callers can collapse
     * the medication card.
     *
     * Re-keys the underlying DAO query whenever the user's logical day
     * advances — at every SoD boundary, not just on preference change. See
     * `docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md`.
     */
    fun observeDueDosesToday(): Flow<List<MedicationDose>> =
        localDateFlow.observeIsoString(taskBehaviorPreferences.getStartOfDay())
            .flatMapLatest { todayLocal ->
                combine(
                    medicationDao.getActive(),
                    medicationDoseDao.getForDate(todayLocal)
                ) { meds, doses ->
                    expandMedicationsToDoses(meds, doses)
                        .filterNot { it.takenToday }
                }
            }

    companion object {
        fun empty(): Flow<List<MedicationDose>> = flowOf(emptyList())

        /**
         * Core expansion: given the active medication rows and today's
         * dose-log rows, emit one [MedicationDose] per schedule slot with
         * `takenToday` computed from the log.
         *
         * Exposed on the companion so unit tests can verify the rule
         * without wiring up DAOs.
         */
        @JvmStatic
        internal fun expandMedicationsToDoses(
            medications: List<MedicationEntity>,
            todaysDoses: List<MedicationDoseEntity>
        ): List<MedicationDose> {
            val dosesByMedAndSlot: Map<Pair<Long, String>, List<MedicationDoseEntity>> =
                todaysDoses.groupBy { it.medicationId to it.slotKey }

            return medications.flatMap { med ->
                when (med.scheduleMode) {
                    "TIMES_OF_DAY" -> expandTimesOfDay(med, dosesByMedAndSlot)
                    "SPECIFIC_TIMES" -> expandSpecificTimes(med, dosesByMedAndSlot)
                    "INTERVAL" -> expandInterval(med, dosesByMedAndSlot)
                    // AS_NEEDED and any unknown: no scheduled doses.
                    else -> emptyList()
                }
            }
        }

        private fun expandTimesOfDay(
            med: MedicationEntity,
            dosesByKey: Map<Pair<Long, String>, List<MedicationDoseEntity>>
        ): List<MedicationDose> {
            val slots = med.timesOfDay.orEmpty()
                .split(',').map { it.trim() }
                .filter { it in VALID_TOD_SLOTS }
                .distinct()
            if (slots.isEmpty()) return emptyList()
            return slots.map { slot ->
                MedicationDose(
                    medicationName = med.name,
                    displayLabel = med.displayLabel ?: med.name,
                    source = DoseSource.SELF_CARE_STEP,
                    scheduledAt = null,
                    takenToday = dosesByKey[med.id to slot]?.isNotEmpty() == true,
                    linkedHabitId = null,
                    slotKey = MedicationSlotGrouper.slotKeyForTimeOfDay(slot),
                    selfCareStepId = null
                )
            }
        }

        private fun expandSpecificTimes(
            med: MedicationEntity,
            dosesByKey: Map<Pair<Long, String>, List<MedicationDoseEntity>>
        ): List<MedicationDose> {
            val slots = med.specificTimes.orEmpty()
                .split(',').map { it.trim() }
                .filter { isValidClockString(it) }
                .distinct()
                .sorted()
            if (slots.isEmpty()) return emptyList()
            return slots.map { slot ->
                val normalized = MedicationSlotGrouper.normalizeTimeKey(slot)
                MedicationDose(
                    medicationName = med.name,
                    displayLabel = med.displayLabel ?: med.name,
                    source = DoseSource.SPECIFIC_TIME,
                    scheduledAt = null,
                    takenToday = dosesByKey[med.id to normalized]?.isNotEmpty() == true,
                    linkedHabitId = null,
                    slotKey = normalized,
                    selfCareStepId = null
                )
            }
        }

        private fun expandInterval(
            med: MedicationEntity,
            dosesByKey: Map<Pair<Long, String>, List<MedicationDoseEntity>>
        ): List<MedicationDose> {
            val dosesTaken = dosesByKey[med.id to MedicationSlotGrouper.ANYTIME_KEY]
                ?.size ?: 0
            val target = med.dosesPerDay.coerceAtLeast(1)
            return listOf(
                MedicationDose(
                    medicationName = med.name,
                    displayLabel = med.displayLabel ?: med.name,
                    source = DoseSource.INTERVAL_HABIT,
                    scheduledAt = null,
                    takenToday = dosesTaken >= target,
                    linkedHabitId = null,
                    slotKey = MedicationSlotGrouper.ANYTIME_KEY,
                    selfCareStepId = null
                )
            )
        }

        private fun isValidClockString(raw: String): Boolean {
            val parts = raw.split(':')
            if (parts.size != 2) return false
            val hour = parts[0].toIntOrNull() ?: return false
            val minute = parts[1].toIntOrNull() ?: return false
            return hour in 0..23 && minute in 0..59
        }

        private val VALID_TOD_SLOTS = setOf("morning", "afternoon", "evening", "night")

        /**
         * Legacy dedup helper retained for pre-v1.4 callers that may still
         * merge dose lists from multiple sources. Post-rewrite the
         * expansion path never emits cross-source duplicates for a single
         * `(name, slot)`, so this method is a no-op for current
         * [expandMedicationsToDoses] output. Priority order preserved
         * (`SPECIFIC_TIME > INTERVAL_HABIT > SELF_CARE_STEP`) so external
         * callers that still hand-build composite dose lists get the
         * same behavior they had before.
         */
        @JvmStatic
        internal fun dedupByName(doses: List<MedicationDose>): List<MedicationDose> {
            val priority = mapOf(
                DoseSource.SPECIFIC_TIME to 2,
                DoseSource.INTERVAL_HABIT to 1,
                DoseSource.SELF_CARE_STEP to 0
            )
            val picked = mutableMapOf<String, MedicationDose>()
            for (dose in doses) {
                val slotPart = dose.slotKey ?: ""
                val key = "${dose.medicationName.trim().lowercase()}|$slotPart"
                val existing = picked[key]
                val keep = when {
                    existing == null -> true
                    (priority[dose.source] ?: 0) > (priority[existing.source] ?: 0) -> true
                    (priority[dose.source] ?: 0) == (priority[existing.source] ?: 0) &&
                        !dose.takenToday && existing.takenToday -> true
                    else -> false
                }
                if (keep) picked[key] = dose
            }
            return picked.values.toList()
        }
    }
}
