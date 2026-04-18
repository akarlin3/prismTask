package com.averycorp.prismtask.domain.model

import com.averycorp.prismtask.data.local.entity.MilestoneEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.domain.usecase.StreakResult

/**
 * Full detail projection for the project detail screen.
 *
 * [linkedTaskIds] is just the IDs of tasks on this project so the detail
 * screen can delegate task rendering to the existing task-list component
 * (filtered by these IDs / by `projectId == id`) without importing task
 * entities into the projection.
 */
data class ProjectDetail(
    val project: ProjectEntity,
    val status: ProjectStatus,
    val milestones: List<MilestoneEntity>,
    val totalTasks: Int,
    val openTasks: Int,
    val streak: StreakResult,
    val lastActivityAt: Long?
) {
    val totalMilestones: Int get() = milestones.size
    val completedMilestones: Int get() = milestones.count { it.isCompleted }
    val milestoneProgress: Float
        get() = if (totalMilestones == 0) 0f else completedMilestones.toFloat() / totalMilestones
    val upcomingMilestone: MilestoneEntity?
        get() = milestones.firstOrNull { !it.isCompleted }
}
