package com.averykarlin.averytask.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.dao.SelfCareDao
import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitEntity
import com.averykarlin.averytask.data.local.entity.SelfCareLogEntity
import com.averykarlin.averytask.data.local.entity.SelfCareStepEntity
import com.averykarlin.averytask.data.preferences.MedicationPreferences
import com.averykarlin.averytask.domain.model.RoutineStep
import com.averykarlin.averytask.domain.model.SelfCareRoutines
import com.averykarlin.averytask.notifications.MedStepReminderReceiver
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MedStepLog(
    val id: String,
    val note: String = "",
    val at: Long = System.currentTimeMillis()
)

@Singleton
class SelfCareRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val selfCareDao: SelfCareDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val medicationPreferences: MedicationPreferences,
    private val gson: Gson
) {

    fun getTodayLog(routineType: String): Flow<SelfCareLogEntity?> =
        selfCareDao.getLogForDate(routineType, todayMidnight())

    fun getSteps(routineType: String): Flow<List<SelfCareStepEntity>> =
        selfCareDao.getStepsForRoutine(routineType)

    suspend fun ensureDefaultStepsSeeded() {
        if (selfCareDao.getStepCount() > 0) return
        val morningEntities = SelfCareRoutines.morningSteps.mapIndexed { i, step ->
            SelfCareStepEntity(
                stepId = step.id,
                routineType = "morning",
                label = step.label,
                duration = step.duration,
                tier = step.tier,
                note = step.note,
                phase = step.phase,
                sortOrder = i
            )
        }
        val bedtimeEntities = SelfCareRoutines.bedtimeSteps.mapIndexed { i, step ->
            SelfCareStepEntity(
                stepId = step.id,
                routineType = "bedtime",
                label = step.label,
                duration = step.duration,
                tier = step.tier,
                note = step.note,
                phase = step.phase,
                sortOrder = i
            )
        }
        selfCareDao.insertSteps(morningEntities + bedtimeEntities)
    }

    suspend fun addStep(
        routineType: String,
        label: String,
        duration: String,
        tier: String,
        note: String,
        phase: String,
        reminderDelayMillis: Long? = null
    ) {
        val nextOrder = selfCareDao.getMaxSortOrder(routineType) + 1
        val stepId = "custom_${UUID.randomUUID().toString().take(8)}"
        selfCareDao.insertStep(
            SelfCareStepEntity(
                stepId = stepId,
                routineType = routineType,
                label = label,
                duration = duration,
                tier = tier,
                note = note,
                phase = phase,
                sortOrder = nextOrder,
                reminderDelayMillis = reminderDelayMillis
            )
        )
    }

    suspend fun updateStep(step: SelfCareStepEntity) {
        selfCareDao.updateStep(step)
    }

    suspend fun deleteStep(step: SelfCareStepEntity) {
        selfCareDao.deleteStep(step)
    }

    suspend fun moveStep(step: SelfCareStepEntity, direction: Int) {
        val allSteps = selfCareDao.getStepsForRoutineOnce(step.routineType)
        val index = allSteps.indexOfFirst { it.id == step.id }
        if (index < 0) return
        val targetIndex = index + direction
        if (targetIndex < 0 || targetIndex >= allSteps.size) return

        val current = allSteps[index]
        val target = allSteps[targetIndex]
        selfCareDao.updateSteps(
            listOf(
                current.copy(sortOrder = target.sortOrder),
                target.copy(sortOrder = current.sortOrder)
            )
        )
    }

    fun getVisibleStepsFromEntities(
        steps: List<SelfCareStepEntity>,
        tier: String,
        routineType: String
    ): List<SelfCareStepEntity> {
        val tierOrder = SelfCareRoutines.getTierOrder(routineType)
        return steps.filter { SelfCareRoutines.tierIncludes(tierOrder, tier, it.tier) }
    }

    fun getPhaseGroupedSteps(
        steps: List<SelfCareStepEntity>,
        routineType: String
    ): List<Pair<String, List<SelfCareStepEntity>>> {
        if (routineType == "medication") {
            return if (steps.isEmpty()) emptyList() else listOf("Medications" to steps)
        }
        val phaseOrder = if (routineType == "morning") {
            listOf("Skincare", "Hygiene", "Grooming")
        } else {
            listOf("Wash", "Skincare", "Hygiene", "Sleep")
        }
        val grouped = steps.groupBy { it.phase }
        val result = mutableListOf<Pair<String, List<SelfCareStepEntity>>>()
        for (phase in phaseOrder) {
            grouped[phase]?.let { result.add(phase to it) }
        }
        // Include any custom phases not in the predefined order
        for ((phase, stepsInPhase) in grouped) {
            if (phase !in phaseOrder) {
                result.add(phase to stepsInPhase)
            }
        }
        return result
    }

    suspend fun setTier(routineType: String, tier: String) {
        val today = todayMidnight()
        val existing = selfCareDao.getLogForDateOnce(routineType, today)
        if (existing != null) {
            if (routineType == "medication") {
                // Medication: don't clear logs when switching tier view
                val logs = parseMedStepLogs(existing.completedSteps)
                val updatedIds = logs.map { it.id }.toSet()
                val dbSteps = selfCareDao.getStepsForRoutineOnce(routineType)
                val visibleSteps = getVisibleStepsFromEntities(dbSteps, tier, routineType)
                val allDone = visibleSteps.isNotEmpty() && visibleSteps.all { it.stepId in updatedIds }
                selfCareDao.updateLog(existing.copy(selectedTier = tier, isComplete = allDone))
                syncHabitCompletion(routineType, allDone)
            } else {
                selfCareDao.updateLog(
                    existing.copy(
                        selectedTier = tier,
                        completedSteps = "[]",
                        isComplete = false,
                        startedAt = null
                    )
                )
                syncHabitCompletion(routineType, false)
            }
        } else {
            selfCareDao.insertLog(
                SelfCareLogEntity(
                    routineType = routineType,
                    date = today,
                    selectedTier = tier
                )
            )
        }
    }

    suspend fun logTier(tier: String, note: String) {
        val routineType = "medication"
        val today = todayMidnight()
        var existing = selfCareDao.getLogForDateOnce(routineType, today)
        if (existing == null) {
            selfCareDao.insertLog(
                SelfCareLogEntity(
                    routineType = routineType,
                    date = today,
                    selectedTier = tier,
                    startedAt = System.currentTimeMillis()
                )
            )
            existing = selfCareDao.getLogForDateOnce(routineType, today)!!
        }

        val logs = parseMedStepLogs(existing.completedSteps).toMutableList()
        val loggedIds = logs.map { it.id }.toSet()
        val dbSteps = selfCareDao.getStepsForRoutineOnce(routineType)
        val visibleSteps = getVisibleStepsFromEntities(dbSteps, tier, routineType)
        val now = System.currentTimeMillis()
        val trimmedNote = note.trim()

        for (step in visibleSteps) {
            if (step.stepId !in loggedIds) {
                logs.add(MedStepLog(id = step.stepId, note = trimmedNote, at = now))
            }
        }

        val updatedIds = logs.map { it.id }.toSet()
        val activeTier = existing.selectedTier
        val activeVisible = getVisibleStepsFromEntities(dbSteps, activeTier, routineType)
        val allDone = activeVisible.isNotEmpty() && activeVisible.all { it.stepId in updatedIds }

        selfCareDao.updateLog(
            existing.copy(
                completedSteps = serializeMedStepLogs(logs),
                isComplete = allDone,
                startedAt = existing.startedAt ?: now
            )
        )
        syncHabitCompletion(routineType, allDone)

        // Schedule global medication reminder
        val intervalMinutes = medicationPreferences.getReminderIntervalMinutesOnce()
        if (intervalMinutes > 0) {
            scheduleMedicationReminder(now, intervalMinutes.toLong() * 60_000L)
        }
    }

    suspend fun unlogTier(tier: String) {
        val routineType = "medication"
        val today = todayMidnight()
        val existing = selfCareDao.getLogForDateOnce(routineType, today) ?: return

        val logs = parseMedStepLogs(existing.completedSteps).toMutableList()
        val dbSteps = selfCareDao.getStepsForRoutineOnce(routineType)
        // Only remove steps that belong exactly to this tier (not cumulative)
        val tierStepIds = dbSteps.filter { it.tier == tier }.map { it.stepId }.toSet()

        val removed = logs.filter { it.id in tierStepIds }
        removed.forEach { cancelMedStepReminder(it.id) }
        logs.removeAll { it.id in tierStepIds }

        val updatedIds = logs.map { it.id }.toSet()
        val activeTier = existing.selectedTier
        val activeVisible = getVisibleStepsFromEntities(dbSteps, activeTier, routineType)
        val allDone = activeVisible.isNotEmpty() && activeVisible.all { it.stepId in updatedIds }

        selfCareDao.updateLog(
            existing.copy(
                completedSteps = serializeMedStepLogs(logs),
                isComplete = allDone
            )
        )
        syncHabitCompletion(routineType, allDone)
    }

    suspend fun toggleStep(routineType: String, stepId: String, note: String? = null) {
        val today = todayMidnight()
        var existing = selfCareDao.getLogForDateOnce(routineType, today)
        if (existing == null) {
            selfCareDao.insertLog(
                SelfCareLogEntity(
                    routineType = routineType,
                    date = today,
                    startedAt = System.currentTimeMillis()
                )
            )
            existing = selfCareDao.getLogForDateOnce(routineType, today)!!
        }

        val isMedication = routineType == "medication"
        val completedStepsJson: String

        if (isMedication) {
            val logs = parseMedStepLogs(existing.completedSteps).toMutableList()
            val wasCompleted = logs.any { it.id == stepId }

            if (wasCompleted) {
                logs.removeAll { it.id == stepId }
            } else {
                val now = System.currentTimeMillis()
                logs.add(MedStepLog(id = stepId, note = note?.trim() ?: "", at = now))
            }

            val updatedIds = logs.map { it.id }.toSet()
            val dbSteps = selfCareDao.getStepsForRoutineOnce(routineType)
            val visibleSteps = getVisibleStepsFromEntities(dbSteps, existing.selectedTier, routineType)
            val allDone = visibleSteps.isNotEmpty() && visibleSteps.all { it.stepId in updatedIds }
            completedStepsJson = serializeMedStepLogs(logs)

            val updated = existing.copy(
                completedSteps = completedStepsJson,
                isComplete = allDone,
                startedAt = existing.startedAt ?: System.currentTimeMillis()
            )
            selfCareDao.updateLog(updated)
            syncHabitCompletion(routineType, allDone)
        } else {
            // Non-medication routines: plain string ID list
            val steps = parseSteps(existing.completedSteps).toMutableSet()
            if (steps.contains(stepId)) {
                steps.remove(stepId)
            } else {
                steps.add(stepId)
            }

            val dbSteps = selfCareDao.getStepsForRoutineOnce(routineType)
            val visibleSteps = getVisibleStepsFromEntities(dbSteps, existing.selectedTier, routineType)
            val allDone = visibleSteps.isNotEmpty() && visibleSteps.all { it.stepId in steps }

            val updated = existing.copy(
                completedSteps = gson.toJson(steps.toList()),
                isComplete = allDone,
                startedAt = existing.startedAt ?: System.currentTimeMillis()
            )
            selfCareDao.updateLog(updated)
            syncHabitCompletion(routineType, allDone)
        }
    }

    suspend fun resetToday(routineType: String) {
        val today = todayMidnight()
        val existing = selfCareDao.getLogForDateOnce(routineType, today) ?: return
        if (routineType == "medication") {
            cancelMedicationReminder()
        }
        val updated = existing.copy(
            completedSteps = "[]",
            isComplete = false,
            startedAt = null
        )
        selfCareDao.updateLog(updated)
        syncHabitCompletion(routineType, false)
    }

    private fun parseSteps(json: String): Set<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun parseMedStepLogs(json: String): List<MedStepLog> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val array = gson.fromJson(json, JsonArray::class.java)
            array.mapNotNull { element ->
                if (element.isJsonPrimitive) {
                    // Legacy format: plain string step ID
                    MedStepLog(id = element.asString)
                } else if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    MedStepLog(
                        id = obj.get("id")?.asString ?: return@mapNotNull null,
                        note = obj.get("note")?.asString ?: "",
                        at = obj.get("at")?.asLong ?: 0L
                    )
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeMedStepLogs(logs: List<MedStepLog>): String {
        val array = JsonArray()
        for (log in logs) {
            val obj = JsonObject()
            obj.addProperty("id", log.id)
            obj.addProperty("note", log.note)
            obj.addProperty("at", log.at)
            array.add(obj)
        }
        return gson.toJson(array)
    }

    private fun scheduleMedStepReminder(step: SelfCareStepEntity, loggedAt: Long) {
        val delay = step.reminderDelayMillis ?: return
        val triggerTime = loggedAt + delay
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }
        val intent = Intent(context, MedStepReminderReceiver::class.java).apply {
            putExtra("stepId", step.stepId)
            putExtra("medName", step.label)
            putExtra("medNote", step.note)
        }
        val requestCode = step.stepId.hashCode() + 400_000
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (_: SecurityException) {
            // Exact alarm permission not granted
        }
    }

    private fun cancelMedStepReminder(stepId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, MedStepReminderReceiver::class.java)
        val requestCode = stepId.hashCode() + 400_000
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleMedicationReminder(loggedAt: Long, intervalMillis: Long) {
        val triggerTime = maxOf(loggedAt + intervalMillis, System.currentTimeMillis() + 1000)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }
        val intent = Intent(context, MedStepReminderReceiver::class.java).apply {
            putExtra("stepId", "medication_global")
            putExtra("medName", "Medication Reminder")
            putExtra("medNote", "Time to take your next medications")
        }
        val requestCode = GLOBAL_MED_REMINDER_REQUEST_CODE
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (_: SecurityException) {
            // Exact alarm permission not granted
        }
    }

    fun cancelMedicationReminder() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, MedStepReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, GLOBAL_MED_REMINDER_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    suspend fun ensureHabitsExist() {
        getOrCreateHabit("morning")
        getOrCreateHabit("bedtime")
        getOrCreateHabit("medication")
    }

    private suspend fun getOrCreateHabit(routineType: String): HabitEntity {
        val name = when (routineType) {
            "morning" -> MORNING_HABIT_NAME
            "medication" -> MEDICATION_HABIT_NAME
            else -> BEDTIME_HABIT_NAME
        }
        val existing = habitDao.getHabitByName(name)
        if (existing != null) return existing

        val icon = when (routineType) {
            "morning" -> "\u2600\uFE0F"
            "medication" -> "\uD83D\uDC8A"
            else -> "\uD83C\uDF19"
        }
        val color = when (routineType) {
            "morning" -> "#F59E0B"
            "medication" -> "#EF4444"
            else -> "#8B5CF6"
        }
        val category = if (routineType == "medication") "Medication" else "Self-Care"
        val desc = when (routineType) {
            "morning" -> "Complete morning self-care routine"
            "medication" -> "Take all daily medications"
            else -> "Complete bedtime self-care routine"
        }

        val id = habitDao.insert(
            HabitEntity(
                name = name,
                description = desc,
                icon = icon,
                color = color,
                category = category,
                targetFrequency = 1,
                frequencyPeriod = "daily"
            )
        )
        return habitDao.getHabitByIdOnce(id)!!
    }

    private suspend fun syncHabitCompletion(routineType: String, allDone: Boolean) {
        val habit = getOrCreateHabit(routineType)
        val today = todayMidnight()
        val alreadyCompleted = habitCompletionDao.isCompletedOnDateOnce(habit.id, today)

        if (allDone && !alreadyCompleted) {
            habitCompletionDao.insert(
                HabitCompletionEntity(
                    habitId = habit.id,
                    completedDate = today,
                    completedAt = System.currentTimeMillis()
                )
            )
        } else if (!allDone && alreadyCompleted) {
            habitCompletionDao.deleteByHabitAndDate(habit.id, today)
        }
    }

    suspend fun sortStepsByPhaseOrder(routineType: String, phaseOrder: List<String>) {
        val steps = selfCareDao.getStepsForRoutineOnce(routineType)
        val sorted = steps.sortedWith(compareBy<SelfCareStepEntity> {
            val idx = phaseOrder.indexOf(it.phase)
            if (idx >= 0) idx else phaseOrder.size
        }.thenBy { it.sortOrder })

        val updated = sorted.mapIndexed { i, step -> step.copy(sortOrder = i) }
        selfCareDao.updateSteps(updated)
    }

    fun computeTierTimes(
        steps: List<SelfCareStepEntity>,
        routineType: String
    ): Map<String, String> {
        val tierOrder = SelfCareRoutines.getTierOrder(routineType)
        return tierOrder.associateWith { tier ->
            val visible = steps.filter { SelfCareRoutines.tierIncludes(tierOrder, tier, it.tier) }
            val totalSeconds = visible.sumOf { parseDurationSeconds(it.duration) }
            formatDuration(totalSeconds)
        }
    }

    companion object {
        const val MORNING_HABIT_NAME = "Morning Self-Care"
        const val BEDTIME_HABIT_NAME = "Bedtime Self-Care"
        const val MEDICATION_HABIT_NAME = "Medication"
        private const val GLOBAL_MED_REMINDER_REQUEST_CODE = 500_000

        fun todayMidnight(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        private fun parseDurationSeconds(duration: String): Int {
            val cleaned = duration.replace("~", "").replace("+", "").trim().lowercase()
            val minMatch = Regex("""(\d+)\s*min""").find(cleaned)
            val secMatch = Regex("""(\d+)\s*sec""").find(cleaned)
            val mins = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val secs = secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return mins * 60 + secs
        }

        private fun formatDuration(totalSeconds: Int): String {
            return when {
                totalSeconds >= 60 -> "~${totalSeconds / 60} min"
                totalSeconds > 0 -> "~${totalSeconds} sec"
                else -> "0 min"
            }
        }
    }
}
