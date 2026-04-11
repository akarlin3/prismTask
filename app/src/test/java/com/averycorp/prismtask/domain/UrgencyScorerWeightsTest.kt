package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.UrgencyWeights
import com.averycorp.prismtask.domain.usecase.UrgencyScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that exercise the user-configurable UrgencyWeights feature.
 * These complement the existing UrgencyScorerTest which uses default weights.
 */
class UrgencyScorerWeightsTest {

    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    private fun task(
        dueDate: Long? = null,
        priority: Int = 0,
        createdAt: Long = now
    ) = TaskEntity(title = "T", dueDate = dueDate, priority = priority, createdAt = createdAt)

    @Test
    fun `all zero weights falls back to default weights`() {
        val urgent = task(dueDate = now - day, priority = 4)
        val allZero = UrgencyScorer.calculateScore(urgent, weights = UrgencyWeights(0f, 0f, 0f, 0f))
        val defaults = UrgencyScorer.calculateScore(urgent, weights = UrgencyWeights())
        assertEquals(defaults, allZero, 0.001f)
    }

    @Test
    fun `only due date weight makes score purely deadline driven`() {
        val urgentOverdue = task(dueDate = now - 10 * day, priority = 1)
        val weights = UrgencyWeights(dueDate = 1f, priority = 0f, age = 0f, subtasks = 0f)
        val score = UrgencyScorer.calculateScore(urgentOverdue, weights = weights)
        // An overdue 10-day task should hit the max dueDateScore of 1.0.
        assertEquals(1f, score, 0.001f)
    }

    @Test
    fun `only priority weight ignores due date`() {
        val lowPriorityOverdue = task(dueDate = now - 10 * day, priority = 0)
        val weights = UrgencyWeights(dueDate = 0f, priority = 1f, age = 0f, subtasks = 0f)
        val score = UrgencyScorer.calculateScore(lowPriorityOverdue, weights = weights)
        // Priority 0 maps to 0 in the scorer.
        assertEquals(0f, score, 0.001f)
    }

    @Test
    fun `custom weights change score compared to defaults`() {
        val t = task(dueDate = now + 3 * day, priority = 4)
        val defaultScore = UrgencyScorer.calculateScore(t, weights = UrgencyWeights())
        val priorityFocused = UrgencyScorer.calculateScore(
            t, weights = UrgencyWeights(dueDate = 0f, priority = 1f, age = 0f, subtasks = 0f)
        )
        // Priority-focused scoring of a priority-4 task should be >= the default.
        assertTrue(priorityFocused >= defaultScore)
    }

    @Test
    fun `subtask weight only influences score when task has subtasks`() {
        val t = task(priority = 0)
        val weights = UrgencyWeights(dueDate = 0f, priority = 0f, age = 0f, subtasks = 1f)
        val noSubtasks = UrgencyScorer.calculateScore(t, 0, 0, weights)
        val withIncomplete = UrgencyScorer.calculateScore(t, subtaskCount = 5, subtaskCompleted = 0, weights = weights)
        assertEquals(0f, noSubtasks, 0.001f)
        // 5 subtasks with 0 completed -> 0.8 subtaskScore * 1.0 weight = 0.8
        assertEquals(0.8f, withIncomplete, 0.001f)
    }

    @Test
    fun `score is always clamped to 0 to 1`() {
        val t = task(dueDate = now - 30 * day, priority = 4, createdAt = now - 60 * day)
        // Even at default weights and extreme values.
        val score = UrgencyScorer.calculateScore(t, 5, 0, UrgencyWeights())
        assertTrue(score in 0f..1f)
    }

    @Test
    fun `negative priority defaults to zero priority score`() {
        val t = task(priority = -1)
        val weights = UrgencyWeights(dueDate = 0f, priority = 1f, age = 0f, subtasks = 0f)
        assertEquals(0f, UrgencyScorer.calculateScore(t, weights = weights), 0.001f)
    }
}
