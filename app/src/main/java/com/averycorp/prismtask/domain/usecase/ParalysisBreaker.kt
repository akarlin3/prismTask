package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.NdPreferences

/**
 * Decision Paralysis Breaker logic for Focus & Release Mode.
 * Helps users who freeze when faced with too many choices.
 */
object ParalysisBreaker {

    /**
     * Suggest the best next task from a list of tasks.
     * Strategy: highest priority first, then nearest due date.
     * When ADHD Mode is also on, prefer the smallest/quickest task.
     */
    fun suggestNextTask(
        tasks: List<TaskEntity>,
        ndPrefs: NdPreferences
    ): TaskEntity? {
        if (tasks.isEmpty()) return null
        if (!ndPrefs.focusReleaseModeEnabled) return null
        if (!ndPrefs.paralysisBreakersEnabled) return null
        if (!ndPrefs.autoSuggestEnabled) return null

        val candidates = tasks
            .filter { !it.isCompleted && it.archivedAt == null }

        if (candidates.isEmpty()) return null

        return if (ndPrefs.adhdModeEnabled) {
            // ADHD + F&R: prefer smallest/quickest task (momentum)
            candidates.sortedWith(
                compareBy<TaskEntity> { it.estimatedDuration ?: Int.MAX_VALUE }
                    .thenByDescending { it.priority }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
            ).first()
        } else {
            // F&R only: highest priority + nearest due date
            candidates.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
            ).first()
        }
    }

    /**
     * Pick a random task from the top N candidates.
     * Used for "Pick for me" — removes the choice entirely.
     */
    fun pickRandom(tasks: List<TaskEntity>, ndPrefs: NdPreferences, topN: Int = 3): TaskEntity? {
        if (tasks.isEmpty()) return null
        val candidates = tasks.filter { !it.isCompleted && it.archivedAt == null }
        if (candidates.isEmpty()) return null

        val sorted = if (ndPrefs.adhdModeEnabled) {
            candidates.sortedWith(
                compareBy<TaskEntity> { it.estimatedDuration ?: Int.MAX_VALUE }
                    .thenByDescending { it.priority }
            )
        } else {
            candidates.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
            )
        }

        return sorted.take(topN).random()
    }

    /**
     * Returns simplified priority levels for the priority picker.
     * Maps 3 user-facing levels to internal priority values.
     */
    fun simplifiedPriorities(): List<SimplifiedPriority> = listOf(
        SimplifiedPriority("Not Urgent", 1),   // Low
        SimplifiedPriority("Normal", 2),        // Medium
        SimplifiedPriority("Urgent", 3)         // High
    )

    /**
     * Returns simplified sort options.
     */
    fun simplifiedSortOptions(): List<SimplifiedSort> = listOf(
        SimplifiedSort("Due Soonest", "due_date_asc"),
        SimplifiedSort("Most Important", "priority_desc"),
        SimplifiedSort("Quick Wins", "duration_asc")
    )

    /**
     * Returns simplified filter presets.
     */
    fun simplifiedFilters(): List<SimplifiedFilter> = listOf(
        SimplifiedFilter("Due Today", "today"),
        SimplifiedFilter("This Week", "this_week"),
        SimplifiedFilter("Overdue", "overdue")
    )
}

data class SimplifiedPriority(val label: String, val internalPriority: Int)
data class SimplifiedSort(val label: String, val sortKey: String)
data class SimplifiedFilter(val label: String, val filterKey: String)
