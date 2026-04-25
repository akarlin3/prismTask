package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.seed.BuiltInHabitVersionRegistry
import com.averycorp.prismtask.domain.model.AcceptedChanges
import com.averycorp.prismtask.domain.model.PendingBuiltInUpdate
import com.averycorp.prismtask.domain.model.TemplateDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers, exposes, and applies built-in habit template updates.
 *
 *  * [pendingUpdates] is a hot StateFlow the UI observes. Empty until
 *    [refreshPendingUpdates] is called at least once per session.
 *  * [refreshPendingUpdates] is cheap (one DAO read per built-in habit
 *    plus one self-care steps fetch per template that has steps). Safe
 *    to call from sync completion and from a Settings pull-to-refresh.
 *  * [applyUpdate] writes the user's selected changes back to Room and
 *    bumps `source_version` on the row to the registry's current version.
 *  * [dismiss] records (templateKey, version) in BuiltInSyncPreferences.
 *  * [detach] sets `is_detached_from_template = true` on the row and
 *    clears any dismissals for that key.
 */
@Singleton
class BuiltInUpdateDetector @Inject constructor(
    private val habitDao: HabitDao,
    private val selfCareDao: SelfCareDao,
    private val differ: BuiltInTemplateDiffer,
    private val prefs: BuiltInSyncPreferences
) {
    private val registry = BuiltInHabitVersionRegistry

    private val _pendingUpdates = MutableStateFlow<List<PendingBuiltInUpdate>>(emptyList())
    val pendingUpdates: StateFlow<List<PendingBuiltInUpdate>> = _pendingUpdates.asStateFlow()

    private val cachedDiffs = mutableMapOf<String, TemplateDiff>()

    suspend fun refreshPendingUpdates() {
        val pending = mutableListOf<PendingBuiltInUpdate>()
        cachedDiffs.clear()
        for (definition in registry.allCurrent()) {
            val habit = habitDao.getByTemplateKeyOnce(definition.templateKey) ?: continue
            if (habit.isDetachedFromTemplate) continue
            if (prefs.isDismissed(definition.templateKey, definition.version)) continue
            val steps = if (definition.steps.isNotEmpty()) {
                stepsForRoutine(definition.steps.first().routineType)
            } else {
                emptyList()
            }
            val diff = differ.diff(habit, steps, definition) ?: continue
            cachedDiffs[definition.templateKey] = diff
            pending += PendingBuiltInUpdate(
                templateKey = diff.templateKey,
                displayName = definition.name,
                fromVersion = diff.fromVersion,
                toVersion = diff.toVersion,
                addedStepCount = diff.addedSteps.size,
                removedStepCount = diff.removedSteps.size,
                modifiedStepCount = diff.modifiedSteps.size,
                habitFieldChangeCount = diff.habitFieldChanges.size
            )
        }
        _pendingUpdates.value = pending
    }

    /**
     * Returns the cached diff produced by the most recent [refreshPendingUpdates]
     * call. Null if there is no cached diff for [templateKey].
     */
    fun diffFor(templateKey: String): TemplateDiff? = cachedDiffs[templateKey]

    suspend fun applyUpdate(diff: TemplateDiff, accepted: AcceptedChanges) {
        val now = System.currentTimeMillis()
        val routineType = (diff.addedSteps.firstOrNull()?.routineType)
            ?: (diff.modifiedSteps.firstOrNull()?.proposed?.routineType)
            ?: (diff.removedSteps.firstOrNull()?.routineType)

        // 1. Apply accepted habit field changes.
        var updatedHabit = accepted.habit
        for (change in diff.habitFieldChanges.filter { it.fieldName in accepted.acceptedFieldNames }) {
            updatedHabit = applyFieldChange(updatedHabit, change.fieldName, change.proposedValue)
        }
        updatedHabit = updatedHabit.copy(
            sourceVersion = diff.toVersion,
            updatedAt = now
        )
        habitDao.update(updatedHabit)

        // 2. Apply step inserts.
        val toInsert = diff.addedSteps
            .filter { it.stepId in accepted.acceptedAddedStepIds }
            .map { def ->
                SelfCareStepEntity(
                    stepId = def.stepId,
                    routineType = def.routineType,
                    label = def.label,
                    duration = def.duration,
                    tier = def.tier,
                    note = def.note,
                    phase = def.phase,
                    sortOrder = def.sortOrder,
                    sourceVersion = diff.toVersion,
                    updatedAt = now
                )
            }
        if (toInsert.isNotEmpty()) selfCareDao.insertSteps(toInsert)

        // 3. Apply step deletions.
        for (step in diff.removedSteps.filter { it.stepId in accepted.acceptedRemovedStepIds }) {
            selfCareDao.deleteStepById(step.id)
        }

        // 4. Apply step modifications.
        val toUpdate = diff.modifiedSteps
            .filter { it.stepId in accepted.acceptedModifiedStepIds }
            .map { change ->
                change.current.copy(
                    label = change.proposed.label,
                    duration = change.proposed.duration,
                    tier = change.proposed.tier,
                    phase = change.proposed.phase,
                    sortOrder = change.proposed.sortOrder,
                    note = change.proposed.note,
                    sourceVersion = diff.toVersion,
                    updatedAt = now
                )
            }
        if (toUpdate.isNotEmpty()) selfCareDao.updateSteps(toUpdate)

        // 5. Bump source_version on any steps the user kept untouched so they
        // stop showing diffs against this version on subsequent runs. Only
        // applies to steps in the proposed set the user did not opt into.
        if (routineType != null) {
            val all = selfCareDao.getStepsForRoutineOnce(routineType)
            val proposedKnownIds = (
                diff.modifiedSteps.map { it.stepId } +
                    diff.addedSteps.map { it.stepId }
                ).toSet()
            val touchedIds = accepted.acceptedAddedStepIds + accepted.acceptedModifiedStepIds
            val pending = all
                .filter { it.stepId in proposedKnownIds && it.stepId !in touchedIds }
                .filter { it.sourceVersion < diff.toVersion }
                .map { it.copy(sourceVersion = diff.toVersion, updatedAt = now) }
            if (pending.isNotEmpty()) selfCareDao.updateSteps(pending)
        }

        // 6. Refresh the pending list so the row drops out.
        refreshPendingUpdates()
    }

    suspend fun dismiss(templateKey: String, version: Int) {
        prefs.setDismissed(templateKey, version)
        refreshPendingUpdates()
    }

    suspend fun detach(templateKey: String) {
        val habit = habitDao.getByTemplateKeyOnce(templateKey) ?: return
        habitDao.update(
            habit.copy(
                isDetachedFromTemplate = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        prefs.clearDismissals(templateKey)
        refreshPendingUpdates()
    }

    private suspend fun stepsForRoutine(routineType: String): List<SelfCareStepEntity> =
        selfCareDao.getStepsForRoutineOnce(routineType)

    private fun applyFieldChange(
        habit: com.averycorp.prismtask.data.local.entity.HabitEntity,
        fieldName: String,
        proposed: String?
    ): com.averycorp.prismtask.data.local.entity.HabitEntity = when (fieldName) {
        "name" -> habit.copy(name = proposed.orEmpty())
        "description" -> habit.copy(description = proposed)
        "frequencyPeriod" -> habit.copy(frequencyPeriod = proposed ?: "daily")
        "targetFrequency" -> habit.copy(targetFrequency = proposed?.toIntOrNull() ?: habit.targetFrequency)
        "activeDays" -> habit.copy(activeDays = proposed)
        else -> habit
    }
}
