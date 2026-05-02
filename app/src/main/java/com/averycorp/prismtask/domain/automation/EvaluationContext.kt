package com.averycorp.prismtask.domain.automation

import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity

/**
 * Field-resolution context handed to [ConditionEvaluator]. Holds the
 * trigger event plus the entity (already loaded by the engine to avoid a
 * DAO round-trip per condition node) plus the optional list of tag names
 * for tasks (for `task.tags CONTAINS "#urgent"` style conditions).
 *
 * Field-path resolution table (the exhaustive list from § A4):
 *
 *  - `event.occurredAt`
 *  - `task.id`, `task.title`, `task.priority`, `task.dueDate`,
 *    `task.completedAt`, `task.tags`, `task.projectId`,
 *    `task.lifeCategory`, `task.isFlagged`
 *  - `habit.id`, `habit.name`, `habit.streakCount`, `habit.category`
 *  - `medication.id`, `medication.name`, `medication.lastTakenAt`
 */
data class EvaluationContext(
    val event: AutomationEvent,
    val task: TaskEntity? = null,
    val taskTags: List<String> = emptyList(),
    val habit: HabitEntity? = null,
    val habitStreak: Int? = null,
    val medication: MedicationEntity? = null,
    val now: Long = System.currentTimeMillis()
) {
    /**
     * Resolve a dotted field path. Returns null when the path is undefined
     * for the current entity (e.g. `task.priority` on a `HabitCompleted`
     * event) — `EXISTS` evaluates this to `false`, comparison ops return
     * false on mismatch.
     */
    fun resolve(path: String): Any? = when (path) {
        "event.occurredAt" -> event.occurredAt
        "task.id" -> task?.id
        "task.title" -> task?.title
        "task.priority" -> task?.priority
        "task.dueDate" -> task?.dueDate
        "task.completedAt" -> task?.completedAt
        "task.tags" -> taskTags
        "task.projectId" -> task?.projectId
        "task.lifeCategory" -> task?.lifeCategory
        "task.isFlagged" -> task?.isFlagged
        "habit.id" -> habit?.id
        "habit.name" -> habit?.name
        "habit.streakCount" -> habitStreak
        "habit.category" -> habit?.category
        "medication.id" -> medication?.id
        "medication.name" -> medication?.name
        "medication.lastTakenAt" -> medication?.let { resolveMedicationLastTakenAt(it) }
        else -> null
    }

    private fun resolveMedicationLastTakenAt(m: MedicationEntity): Long? {
        // The MedicationEntity itself doesn't denormalize lastTakenAt — the
        // engine is responsible for hydrating that via MedicationDoseDao
        // before invoking the evaluator. For now, return null when the
        // engine hasn't pre-loaded it; future work can hydrate this in
        // [AutomationEngine.buildContext].
        return null
    }
}
