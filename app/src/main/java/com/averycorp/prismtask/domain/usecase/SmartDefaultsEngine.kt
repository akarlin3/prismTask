package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.TaskDefaults

/**
 * Computes "smart defaults" for new tasks by analyzing the user's recent
 * completed tasks. Kept as a pure object so it is trivially unit-testable
 * without DI.
 *
 * The engine returns null fields for anything it can't confidently infer
 * (e.g. too few tasks in history). Call sites should fall back to the
 * user-configured static defaults in that case.
 *
 * Added in v1.3.0 (P9).
 */
object SmartDefaultsEngine {

    /** Minimum history required before smart defaults kick in. */
    const val MIN_HISTORY = 5

    /** Maximum number of completed tasks to consider. */
    const val MAX_HISTORY = 50

    data class ComputedDefaults(
        val priority: Int?,
        val projectId: Long?,
        val reminderOffset: Long?,
        val averageDurationMinutes: Int?,
        val typicalDueDateOffsetDays: Long?
    ) {
        fun isEmpty(): Boolean =
            priority == null && projectId == null && reminderOffset == null &&
                averageDurationMinutes == null && typicalDueDateOffsetDays == null
    }

    /**
     * Builds smart defaults from the supplied [completedTasks]. The list is
     * expected to be the most recent completed tasks (already sorted newest-
     * first by the caller).
     *
     * Returns an empty [ComputedDefaults] when there is insufficient history.
     */
    fun compute(completedTasks: List<TaskEntity>): ComputedDefaults {
        if (completedTasks.size < MIN_HISTORY) {
            return ComputedDefaults(null, null, null, null, null)
        }
        val slice = completedTasks.take(MAX_HISTORY)

        val priority = slice.map { it.priority }.mode()
        val projectId = slice.mapNotNull { it.projectId }.mode()
        val reminderOffset = slice.mapNotNull { it.reminderOffset }.mode()

        val averageDuration = slice.mapNotNull { it.estimatedDuration }
            .takeIf { it.isNotEmpty() }
            ?.let { list -> (list.average() / 15.0).let { Math.round(it).toInt() * 15 } }
            ?.takeIf { it > 0 }

        val offsetDaysList = slice.mapNotNull { task ->
            val due = task.dueDate ?: return@mapNotNull null
            val dayMs = 24L * 60 * 60 * 1000
            (due - task.createdAt) / dayMs
        }
        val typicalOffsetDays = offsetDaysList.mode()

        return ComputedDefaults(
            priority = priority,
            projectId = projectId,
            reminderOffset = reminderOffset,
            averageDurationMinutes = averageDuration,
            typicalDueDateOffsetDays = typicalOffsetDays
        )
    }

    /**
     * Applies the hierarchy: NLP result > template > smart defaults > static
     * defaults > blank. Given a [base] (what's already resolved from NLP/
     * template), fills in the nulls first from [smart] then from [static].
     *
     * Returns a new [TaskDefaults] representing the effective values to
     * pre-populate the task editor form with.
     */
    fun merge(
        base: TaskDefaults,
        smart: ComputedDefaults?,
        static: TaskDefaults
    ): TaskDefaults {
        fun pickInt(current: Int?, fromSmart: Int?, fromStatic: Int?): Int =
            current ?: fromSmart ?: fromStatic ?: 0
        fun pickLong(current: Long?, fromSmart: Long?, fromStatic: Long?): Long =
            current ?: fromSmart ?: fromStatic ?: -1L

        // We treat a 0 priority on `base` as "not specified" to match how
        // the NLP result typically leaves priority at 0 unless explicitly set.
        val basePriority = base.defaultPriority.takeIf { it > 0 }
        val priority = pickInt(basePriority, smart?.priority, static.defaultPriority.takeIf { it > 0 })

        val baseReminder = base.defaultReminderOffset.takeIf { it >= 0 }
        val reminder = pickLong(baseReminder, smart?.reminderOffset, static.defaultReminderOffset.takeIf { it >= 0 })

        val project = base.defaultProjectId ?: smart?.projectId ?: static.defaultProjectId
        val duration = base.defaultDuration ?: smart?.averageDurationMinutes ?: static.defaultDuration

        return base.copy(
            defaultPriority = priority,
            defaultReminderOffset = reminder,
            defaultProjectId = project,
            defaultDuration = duration
        )
    }

    /** Returns the most common value in the list, or null if the list is empty. */
    private fun <T : Any> List<T>.mode(): T? {
        if (isEmpty()) return null
        return groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
}
