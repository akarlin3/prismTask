package com.averycorp.prismtask.domain.model

/**
 * Domain-layer view of a task's "where does this sit in the day" state.
 * Used by upstream filters that build the activity-date pools fed to
 * [com.averycorp.prismtask.domain.usecase.DailyForgivenessStreakCore]
 * and the urgency / Today-screen filters. Not persisted — derived from
 * [com.averycorp.prismtask.data.local.entity.TaskEntity] + the
 * dependency-graph state at read time.
 *
 * [BlockedByDependency] is the third state introduced by the
 * PrismTask-Timeline-Class scope (audit § P6): a task whose blocker is
 * unmet is neither overdue (don't break the streak) nor met (don't
 * count). Filters drop it from BOTH the "met" and "missed" pools so
 * the forgiveness streak math sees a no-op for that day.
 */
sealed class TaskState {
    object Open : TaskState()
    object Done : TaskState()
    object BlockedByDependency : TaskState()
}
