package com.averycorp.prismtask.domain.model

import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.domain.usecase.StreakResult

/**
 * Projection a project list screen (or widget) can render without recomputing
 * milestone/task aggregates per frame. Repository precomputes this once per
 * project whenever the underlying data changes.
 */
data class ProjectWithProgress(
    val project: ProjectEntity,
    val status: ProjectStatus,
    val totalMilestones: Int,
    val completedMilestones: Int,
    val upcomingMilestoneTitle: String?,
    val totalTasks: Int,
    val openTasks: Int,
    val streak: StreakResult,
    /**
     * Epoch millis of the most recent project activity (task completion
     * where `task.projectId == project.id`, or a milestone completion on
     * this project). `null` when the project has no recorded activity yet.
     */
    val lastActivityAt: Long?
) {
    /** Fraction of milestones completed, in `[0f, 1f]`. `0f` when the project has no milestones. */
    val milestoneProgress: Float
        get() = if (totalMilestones == 0) 0f else completedMilestones.toFloat() / totalMilestones

    /** Days since the most recent activity, or `null` if there's never been any. */
    fun daysSinceActivity(nowMillis: Long): Int? {
        val last = lastActivityAt ?: return null
        val diff = nowMillis - last
        if (diff <= 0L) return 0
        return (diff / (24L * 60L * 60L * 1000L)).toInt()
    }
}
