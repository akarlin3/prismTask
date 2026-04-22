package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.preferences.MedicationMigrationPreferences
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Post-migration Kotlin runner for the v53 → v54 medication refactor.
 * Two one-shot passes that Room migrations can't do themselves:
 *
 *  1. **Schedule preservation** — reads the user's pre-migration global
 *     schedule (`MedicationPreferences.scheduleMode` + the built-in
 *     `"Medication"` habit's `reminderIntervalMillis`) and writes it onto
 *     every migrated medication row. Room migrations can't read DataStore.
 *  2. **Dose backfill** — parses the JSON `completed_steps` column of
 *     every `self_care_logs` row where `routine_type='medication'` and
 *     inserts a corresponding `medication_doses` row. Factored out of the
 *     SQL migration because `json_each()` / `->>` availability is
 *     OEM-dependent on API 26.
 *
 * Each pass is guarded by its own one-shot flag in
 * [MedicationMigrationPreferences]; the flag is set only after the pass
 * succeeds so a mid-run crash stays retryable.
 *
 * Called from `PrismTaskApplication.onCreate` — same place
 * [BuiltInHabitReconciler.runBackfillIfNeeded] is dispatched from.
 */
@Singleton
class MedicationMigrationRunner
@Inject
constructor(
    private val medicationDao: MedicationDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val habitDao: HabitDao,
    private val selfCareDao: SelfCareDao,
    private val medicationPreferences: MedicationPreferences,
    private val migrationPreferences: MedicationMigrationPreferences,
    private val syncTracker: SyncTracker,
    private val logger: PrismSyncLogger
) {
    /**
     * Writes the user's pre-migration global schedule onto every medication
     * row. Idempotent: no-ops after the first successful run per install.
     */
    suspend fun preserveScheduleIfNeeded() {
        if (migrationPreferences.isSchedulePreserved()) return
        try {
            val meds = medicationDao.getAllOnce()
            if (meds.isEmpty()) {
                // Nothing to update — mark done so we don't re-run every launch.
                migrationPreferences.setSchedulePreserved(true)
                return
            }

            val builtInHabit = habitDao.getHabitByName(SelfCareRepository.MEDICATION_HABIT_NAME)
            val intervalFromHabit = builtInHabit?.reminderIntervalMillis
            val mode = medicationPreferences.getScheduleModeOnce()
            val specificTimes = medicationPreferences.getSpecificTimesOnce().sorted()
            val intervalMinutesFromPrefs = medicationPreferences.getReminderIntervalMinutesOnce()

            logger.info(
                operation = "medication.migration.preserve_schedule",
                detail = "habit_interval=$intervalFromHabit pref_mode=$mode " +
                    "pref_interval_min=$intervalMinutesFromPrefs " +
                    "specific_times=${specificTimes.size} meds=${meds.size}"
            )

            val now = System.currentTimeMillis()
            for (med in meds) {
                val updated = when (mode) {
                    MedicationScheduleMode.SPECIFIC_TIMES -> {
                        val times = if (specificTimes.isEmpty()) {
                            null
                        } else {
                            specificTimes.joinToString(",")
                        }
                        med.copy(
                            scheduleMode = "SPECIFIC_TIMES",
                            specificTimes = times,
                            intervalMillis = null,
                            updatedAt = now
                        )
                    }
                    MedicationScheduleMode.INTERVAL -> {
                        val intervalMillis = intervalFromHabit
                            ?: intervalMinutesFromPrefs
                                .takeIf { it > 0 }
                                ?.let { it * MINUTE_MILLIS }
                        // If neither source has a real interval, fall
                        // back to the TIMES_OF_DAY default the SQL
                        // migration wrote — no net change.
                        if (intervalMillis == null) {
                            med
                        } else {
                            med.copy(
                                scheduleMode = "INTERVAL",
                                intervalMillis = intervalMillis,
                                specificTimes = null,
                                updatedAt = now
                            )
                        }
                    }
                }
                if (updated !== med) {
                    medicationDao.update(updated)
                    syncTracker.trackUpdate(updated.id, "medication")
                }
            }

            migrationPreferences.setSchedulePreserved(true)
        } catch (e: Exception) {
            logger.error(
                operation = "medication.migration.preserve_schedule",
                throwable = e,
                detail = "failed: ${e.message}"
            )
            // Flag intentionally NOT set — retry on next app start.
        }
    }

    /**
     * Parses every legacy `self_care_logs` entry (where
     * `routine_type='medication'`) and inserts matching
     * `medication_doses` rows. Idempotent via the one-shot flag.
     *
     * Each JSON entry in `completed_steps` can be either a bare string
     * step-id or an object `{id, note, at, timeOfDay}`; both forms are
     * handled.
     */
    suspend fun backfillDosesIfNeeded() {
        if (migrationPreferences.isDoseBackfillDone()) return
        try {
            // Step → medication-id map, built by matching step.medication_name
            // (fallback step.label) to medications.name.
            val meds = medicationDao.getAllOnce()
            if (meds.isEmpty()) {
                migrationPreferences.setDoseBackfillDone(true)
                return
            }
            val medByName = meds.associateBy { it.name.trim().lowercase() }

            val steps = selfCareDao.getStepsForRoutineOnce("medication")
            val stepToMedId: Map<String, Long> = steps.mapNotNull { step ->
                val name = step.medicationName?.trim().orEmpty().ifBlank { step.label.trim() }
                val key = name.lowercase()
                val medId = medByName[key]?.id ?: return@mapNotNull null
                step.stepId to medId
            }.toMap()

            val logs = selfCareDao.getAllLogsOnce().filter { it.routineType == "medication" }
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            var inserted = 0
            for (log in logs) {
                val entries = parseCompletedSteps(log.completedSteps)
                for (entry in entries) {
                    val medId = stepToMedId[entry.stepId] ?: continue
                    val takenAt = entry.takenAt ?: log.date
                    val takenDateLocal = Instant.ofEpochMilli(takenAt)
                        .atZone(zone)
                        .toLocalDate()
                        .toString()
                    val slot = entry.timeOfDay?.lowercase()
                        ?.takeIf { it in VALID_TOD_SLOTS } ?: "anytime"
                    val id = medicationDoseDao.insert(
                        MedicationDoseEntity(
                            medicationId = medId,
                            slotKey = slot,
                            takenAt = takenAt,
                            takenDateLocal = takenDateLocal,
                            note = entry.note.orEmpty(),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    syncTracker.trackCreate(id, "medication_dose")
                    inserted++
                }
            }

            logger.info(
                operation = "medication.migration.backfill_doses",
                detail = "logs=${logs.size} steps=${steps.size} inserted=$inserted"
            )

            migrationPreferences.setDoseBackfillDone(true)
        } catch (e: Exception) {
            logger.error(
                operation = "medication.migration.backfill_doses",
                throwable = e,
                detail = "failed: ${e.message}"
            )
        }
    }

    private data class ParsedStep(
        val stepId: String,
        val note: String?,
        val takenAt: Long?,
        val timeOfDay: String?
    )

    private fun parseCompletedSteps(json: String?): List<ParsedStep> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val parsed = JsonParser.parseString(json)
            if (!parsed.isJsonArray) return emptyList()
            (parsed.asJsonArray as JsonArray).mapNotNull { el ->
                when {
                    el.isJsonPrimitive -> ParsedStep(
                        stepId = el.asString,
                        note = null,
                        takenAt = null,
                        timeOfDay = null
                    )
                    el.isJsonObject -> {
                        val obj = el.asJsonObject
                        val id = obj.get("id")?.asString ?: return@mapNotNull null
                        ParsedStep(
                            stepId = id,
                            note = obj.get("note")?.asString,
                            takenAt = obj.get("at")?.asLong,
                            timeOfDay = obj.get("timeOfDay")?.asString
                        )
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val MINUTE_MILLIS = 60_000L
        private val VALID_TOD_SLOTS = setOf("morning", "afternoon", "evening", "night")
    }
}
