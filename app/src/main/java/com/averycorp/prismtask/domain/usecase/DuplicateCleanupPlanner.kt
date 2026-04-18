package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity

/**
 * Plans which tasks, habits, and projects should be removed to clean up
 * accidental duplicates.
 *
 * Detection rule:
 *  - Tasks: grouped by (normalized title, dueDate). Only active, non-archived,
 *    non-completed root tasks (no parent) are considered — subtasks ride
 *    along via foreign-key CASCADE when their parent is deleted, and
 *    completed/archived work is preserved.
 *  - Habits: grouped by (normalized name, frequencyPeriod, targetFrequency).
 *    Archived habits are skipped.
 *  - Projects: grouped by normalized name. Archived projects are skipped.
 *
 * Keep rule: for each group of size > 1, the "most complete" entry is kept
 * (highest completeness score). Ties are broken by oldest createdAt, then by
 * lowest id. All other entries in the group are returned as the IDs to delete.
 *
 * Normalization: title/name are trimmed and lowercased. Null dueDates on
 * tasks are grouped together (two "buy milk" tasks with no due date collapse
 * to a single duplicate group).
 */
object DuplicateCleanupPlanner {
    /** Per-task data the planner needs for its completeness score. */
    data class TaskExtras(
        val subtaskCount: Int,
        val tagCount: Int
    )

    /** Per-habit data the planner needs for its completeness score. */
    data class HabitExtras(
        val completionCount: Int,
        val logCount: Int
    )

    fun findTaskDuplicatesToDelete(
        tasks: List<TaskEntity>,
        extras: Map<Long, TaskExtras>
    ): List<Long> {
        val candidates = tasks.filter {
            it.archivedAt == null && !it.isCompleted && it.parentTaskId == null
        }
        return candidates
            .groupBy { normalize(it.title) to it.dueDate }
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                val keeper = group.maxWithOrNull(
                    compareBy<TaskEntity> { scoreTask(it, extras[it.id]) }
                        .thenByDescending { it.createdAt }
                        .thenByDescending { it.id }
                ) ?: return@flatMap emptyList()
                group.filter { it.id != keeper.id }.map { it.id }
            }
    }

    fun findHabitDuplicatesToDelete(
        habits: List<HabitEntity>,
        extras: Map<Long, HabitExtras>
    ): List<Long> {
        val candidates = habits.filter { !it.isArchived }
        return candidates
            .groupBy { Triple(normalize(it.name), it.frequencyPeriod, it.targetFrequency) }
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                val keeper = group.maxWithOrNull(
                    compareBy<HabitEntity> { scoreHabit(it, extras[it.id]) }
                        .thenByDescending { it.createdAt }
                        .thenByDescending { it.id }
                ) ?: return@flatMap emptyList()
                group.filter { it.id != keeper.id }.map { it.id }
            }
    }

    fun findProjectDuplicatesToDelete(projects: List<ProjectEntity>): List<Long> {
        val candidates = projects.filter { it.archivedAt == null }
        return candidates
            .groupBy { normalize(it.name) }
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                val keeper = group.maxWithOrNull(
                    compareBy<ProjectEntity> { scoreProject(it) }
                        .thenByDescending { it.createdAt }
                        .thenByDescending { it.id }
                ) ?: return@flatMap emptyList()
                group.filter { it.id != keeper.id }.map { it.id }
            }
    }

    private fun normalize(text: String): String = text.trim().lowercase()

    private fun scoreTask(task: TaskEntity, extras: TaskExtras?): Int {
        var score = 0
        if (!task.description.isNullOrBlank()) score++
        if (!task.notes.isNullOrBlank()) score++
        if (task.reminderOffset != null) score++
        if (task.recurrenceRule != null) score++
        if (task.projectId != null) score++
        if (task.estimatedDuration != null) score++
        if (task.dueTime != null) score++
        score += extras?.subtaskCount ?: 0
        score += extras?.tagCount ?: 0
        return score
    }

    private fun scoreHabit(habit: HabitEntity, extras: HabitExtras?): Int {
        var score = 0
        if (!habit.description.isNullOrBlank()) score++
        if (habit.reminderTime != null) score++
        if (!habit.category.isNullOrBlank()) score++
        if (habit.reminderIntervalMillis != null) score++
        score += extras?.completionCount ?: 0
        score += extras?.logCount ?: 0
        return score
    }

    private fun scoreProject(project: ProjectEntity): Int {
        var score = 0
        if (!project.description.isNullOrBlank()) score++
        if (project.themeColorKey != null) score++
        if (project.startDate != null) score++
        if (project.endDate != null) score++
        return score
    }
}
