package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.SelfCareLogEntity
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.util.DayBoundary
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is any medication dose due today and not yet
 * taken". Consolidates the three places where medication scheduling lives:
 *
 *  1. `HabitEntity` rows with an interval reminder (`reminderIntervalMillis`).
 *  2. `SelfCareStepEntity` rows with `routine_type = "medication"` (logged
 *     via the Self-Care medication screen into `self_care_logs`).
 *  3. `MedicationPreferences.specificTimes` — user-declared "HH:mm" slots
 *     cross-referenced against the medication-habit completion log.
 *
 * The use case dedups entries by medication name (case-insensitive) using
 * priority `SPECIFIC_TIME > INTERVAL_HABIT > SELF_CARE_STEP`, then drops any
 * dose already marked taken.
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
     * ``"HH:mm"`` wall-clock time (for specific-time doses or self-care steps
     * with a known time-of-day bucket) or [MedicationSlotGrouper.ANYTIME_KEY]
     * for interval-based doses that have no fixed clock time. ``null`` when a
     * caller hasn't opted into the grouping layer (back-compat).
     */
    val slotKey: String? = null
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
    private val habitRepository: HabitRepository,
    private val habitCompletionDao: HabitCompletionDao,
    private val selfCareDao: SelfCareDao,
    private val medicationPreferences: MedicationPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences
) {
    /**
     * Emits the list of doses that are *due today and not yet taken*. Emits
     * an empty list once every schedule is satisfied so callers can collapse
     * the medication card.
     */
    fun observeDueDosesToday(): Flow<List<MedicationDose>> =
        taskBehaviorPreferences.getDayStartHour().flatMapLatest { dayStartHour ->
            val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)
            combine(
                habitRepository.getActiveHabits(),
                selfCareDao.getStepsForRoutine("medication"),
                selfCareDao.getLogForDate("medication", todayStart),
                medicationPreferences.getScheduleMode(),
                medicationPreferences.getSpecificTimes()
            ) { habits, steps, medLog, mode, specificTimes ->
                val medicationHabit = habits.firstOrNull {
                    it.name == SelfCareRepository.MEDICATION_HABIT_NAME
                }
                val takenStepIds = medLog?.let { parseTakenStepIds(it) } ?: emptySet()
                val completionCount = medicationHabit?.let {
                    habitCompletionDao.getCompletionCountForDateOnce(it.id, todayStart)
                } ?: 0

                val doses = buildList {
                    addAll(intervalDoses(habits, todayStart))
                    addAll(selfCareStepDoses(steps, takenStepIds))
                    if (mode == MedicationScheduleMode.SPECIFIC_TIMES) {
                        addAll(specificTimeDoses(specificTimes, medicationHabit, completionCount))
                    }
                }

                dedupByName(doses).filterNot { it.takenToday }
            }
        }

    private suspend fun intervalDoses(
        habits: List<HabitEntity>,
        todayStart: Long
    ): List<MedicationDose> = habits
        .filter {
            it.reminderIntervalMillis != null &&
                it.category?.equals("Medication", ignoreCase = true) == true
        }
        .map { habit ->
            val takenCount = habitCompletionDao.getCompletionCountForDateOnce(habit.id, todayStart)
            val target = habit.reminderTimesPerDay.coerceAtLeast(1)
            MedicationDose(
                medicationName = habit.name,
                displayLabel = habit.name,
                source = DoseSource.INTERVAL_HABIT,
                scheduledAt = null,
                takenToday = takenCount >= target,
                linkedHabitId = habit.id,
                slotKey = MedicationSlotGrouper.ANYTIME_KEY
            )
        }

    private fun selfCareStepDoses(
        steps: List<SelfCareStepEntity>,
        takenStepIds: Set<String>
    ): List<MedicationDose> = steps.mapNotNull { step ->
        val name = step.medicationName?.trim().orEmpty().ifBlank { step.label.trim() }
        if (name.isBlank()) return@mapNotNull null
        MedicationDose(
            medicationName = name,
            displayLabel = step.label,
            source = DoseSource.SELF_CARE_STEP,
            scheduledAt = null,
            takenToday = step.stepId in takenStepIds,
            linkedHabitId = null,
            slotKey = MedicationSlotGrouper.slotKeyForTimeOfDay(step.timeOfDay)
        )
    }

    private fun specificTimeDoses(
        specificTimes: Set<String>,
        medicationHabit: HabitEntity?,
        completionCount: Int
    ): List<MedicationDose> {
        if (specificTimes.isEmpty()) return emptyList()
        val sortedTimes = specificTimes.sorted()
        // The completion log doesn't carry per-time metadata — the Nth completion
        // taken today satisfies the Nth scheduled slot.
        return sortedTimes.mapIndexed { index, time ->
            MedicationDose(
                medicationName = medicationHabit?.name ?: "Medication",
                displayLabel = "Medication at $time",
                source = DoseSource.SPECIFIC_TIME,
                scheduledAt = null,
                takenToday = index < completionCount,
                linkedHabitId = medicationHabit?.id,
                slotKey = MedicationSlotGrouper.normalizeTimeKey(time)
            )
        }
    }

    private fun parseTakenStepIds(log: SelfCareLogEntity): Set<String> {
        val json = log.completedSteps
        if (json.isBlank() || json == "[]") return emptySet()
        return try {
            val parsed = JsonParser.parseString(json)
            if (!parsed.isJsonArray) return emptySet()
            val array = parsed.asJsonArray as JsonArray
            array.mapNotNull { element ->
                when {
                    element.isJsonPrimitive -> element.asString
                    element.isJsonObject -> element.asJsonObject.get("id")?.asString
                    else -> null
                }
            }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    companion object {
        fun empty(): Flow<List<MedicationDose>> = flowOf(emptyList())

        /**
         * Collapses duplicates that point to the same medication (e.g. a habit
         * named "Adderall" plus a self-care step with `medication_name="Adderall"`).
         * Priority: SPECIFIC_TIME > INTERVAL_HABIT > SELF_CARE_STEP. Within the
         * same priority, the entry with the lower takenToday flag wins (so a
         * still-pending dose isn't hidden by an already-taken duplicate).
         *
         * Exposed on the companion so unit tests can verify the rule without
         * wiring up the full use case + DAOs.
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
                // Dedup is scoped to (name, slotKey) so two distinct specific-time
                // slots of the same medication stay separate rows; only
                // cross-source duplicates of the *same* slot collapse.
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
