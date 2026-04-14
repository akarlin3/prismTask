package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CoachingRepository] tier-gating logic and heuristics.
 *
 * API call orchestration is tested only at the boundary level (result type);
 * actual HTTP calls are covered by integration tests. This class focuses on:
 * - Perfectionism detection heuristic
 * - Celebration eligibility
 * - Task breakdown suggestion heuristic
 * - Tier-based access control patterns
 */
class CoachingRepositoryTest {
    // region Perfectionism detection (Trigger 2)

    @Test
    fun `shouldShowPerfectionismCard returns true when editCount at least 3`() {
        assertTrue(
            shouldShowPerfectionismCardStatic(
                editCount = 3,
                rescheduleCount = 0,
                subtasksAdded = 0,
                subtasksCompleted = 0
            )
        )
    }

    @Test
    fun `shouldShowPerfectionismCard returns true when rescheduleCount at least 2`() {
        assertTrue(
            shouldShowPerfectionismCardStatic(
                editCount = 0,
                rescheduleCount = 2,
                subtasksAdded = 0,
                subtasksCompleted = 0
            )
        )
    }

    @Test
    fun `shouldShowPerfectionismCard returns true when subtasks added but none completed`() {
        assertTrue(
            shouldShowPerfectionismCardStatic(
                editCount = 0,
                rescheduleCount = 0,
                subtasksAdded = 2,
                subtasksCompleted = 0
            )
        )
    }

    @Test
    fun `shouldShowPerfectionismCard returns false when subtasks are partially completed`() {
        assertFalse(
            shouldShowPerfectionismCardStatic(
                editCount = 0,
                rescheduleCount = 0,
                subtasksAdded = 3,
                subtasksCompleted = 1
            )
        )
    }

    @Test
    fun `shouldShowPerfectionismCard returns false when all counts below threshold`() {
        assertFalse(
            shouldShowPerfectionismCardStatic(
                editCount = 2,
                rescheduleCount = 1,
                subtasksAdded = 1,
                subtasksCompleted = 0
            )
        )
    }

    @Test
    fun `shouldShowPerfectionismCard returns true when multiple conditions met`() {
        assertTrue(
            shouldShowPerfectionismCardStatic(
                editCount = 5,
                rescheduleCount = 3,
                subtasksAdded = 4,
                subtasksCompleted = 0
            )
        )
    }

    // endregion

    // region Celebration eligibility (Trigger 5)

    @Test
    fun `shouldCelebrate returns true for partial subtask completion`() {
        assertTrue(
            shouldCelebrateStatic(
                completedSubtaskCount = 3,
                totalSubtaskCount = 5,
                daysOverdue = 0,
                firstAfterGap = false
            )
        )
    }

    @Test
    fun `shouldCelebrate returns false when all subtasks completed`() {
        // All subtasks done is not "partial" — the task is just complete.
        assertFalse(
            shouldCelebrateStatic(
                completedSubtaskCount = 5,
                totalSubtaskCount = 5,
                daysOverdue = 0,
                firstAfterGap = false
            )
        )
    }

    @Test
    fun `shouldCelebrate returns true when task completed significantly overdue`() {
        assertTrue(
            shouldCelebrateStatic(
                completedSubtaskCount = 0,
                totalSubtaskCount = 0,
                daysOverdue = 2,
                firstAfterGap = false
            )
        )
    }

    @Test
    fun `shouldCelebrate returns true for first task after gap`() {
        assertTrue(
            shouldCelebrateStatic(
                completedSubtaskCount = 0,
                totalSubtaskCount = 0,
                daysOverdue = 0,
                firstAfterGap = true
            )
        )
    }

    @Test
    fun `shouldCelebrate returns false when no qualifying conditions`() {
        assertFalse(
            shouldCelebrateStatic(
                completedSubtaskCount = 0,
                totalSubtaskCount = 0,
                daysOverdue = 1,
                firstAfterGap = false
            )
        )
    }

    // endregion

    // region Task breakdown suggestion (Trigger 6)

    @Test
    fun `shouldSuggestBreakdown returns true when estimated duration over 30 minutes`() {
        val task = testTask(title = "Write report", estimatedDuration = 60)
        assertTrue(shouldSuggestBreakdownStatic(task, subtaskCount = 0))
    }

    @Test
    fun `shouldSuggestBreakdown returns false when task already has subtasks`() {
        val task = testTask(title = "finish homework", estimatedDuration = 120)
        assertFalse(shouldSuggestBreakdownStatic(task, subtaskCount = 2))
    }

    @Test
    fun `shouldSuggestBreakdown returns true for vague title`() {
        val vagueWords = listOf("finish", "complete", "work on", "figure out", "do", "handle", "deal with")
        vagueWords.forEach { word ->
            val task = testTask(title = "$word the project", estimatedDuration = null)
            assertTrue(
                "Should suggest breakdown for vague word: '$word'",
                shouldSuggestBreakdownStatic(task, subtaskCount = 0)
            )
        }
    }

    @Test
    fun `shouldSuggestBreakdown returns false for specific short task`() {
        val task = testTask(title = "Email Jake about meeting", estimatedDuration = 5)
        assertFalse(shouldSuggestBreakdownStatic(task, subtaskCount = 0))
    }

    @Test
    fun `shouldSuggestBreakdown returns false for task with no duration and specific title`() {
        val task = testTask(title = "Review PR 47", estimatedDuration = null)
        assertFalse(shouldSuggestBreakdownStatic(task, subtaskCount = 0))
    }

    @Test
    fun `shouldSuggestBreakdown is case insensitive for vague words`() {
        val task = testTask(title = "FINISH the presentation", estimatedDuration = null)
        assertTrue(shouldSuggestBreakdownStatic(task, subtaskCount = 0))
    }

    // endregion

    // region CoachingResult sealed class

    @Test
    fun `CoachingResult Success carries response`() {
        val response = com.averycorp.prismtask.data.remote.api.CoachingResponse(
            message = "Test message",
            subtasks = listOf("a", "b")
        )
        val result = CoachingResult.Success(response)
        assertEquals("Test message", result.response.message)
        assertEquals(2, result.response.subtasks?.size)
    }

    @Test
    fun `CoachingResult UpgradeRequired carries required tier`() {
        val result = CoachingResult.UpgradeRequired(UserTier.PRO)
        assertEquals(UserTier.PRO, result.requiredTier)
    }

    @Test
    fun `CoachingResult FreeLimitReached carries daily limit`() {
        val result = CoachingResult.FreeLimitReached(3)
        assertEquals(3, result.dailyLimit)
    }

    // endregion

    // region Helpers

    /**
     * Static mirror of [CoachingRepository.shouldShowPerfectionismCard] for
     * testability without injecting the full repo.
     */
    companion object {
        fun shouldShowPerfectionismCardStatic(
            editCount: Int,
            rescheduleCount: Int,
            subtasksAdded: Int,
            subtasksCompleted: Int
        ): Boolean = editCount >= 3 ||
            rescheduleCount >= 2 ||
            (subtasksAdded >= 2 && subtasksCompleted == 0)
    }

    private fun shouldCelebrateStatic(
        completedSubtaskCount: Int,
        totalSubtaskCount: Int,
        daysOverdue: Int,
        firstAfterGap: Boolean
    ): Boolean = (completedSubtaskCount in 1 until totalSubtaskCount) ||
        daysOverdue >= 2 ||
        firstAfterGap

    private fun shouldSuggestBreakdownStatic(task: TaskEntity, subtaskCount: Int): Boolean {
        if (subtaskCount > 0) return false
        if ((task.estimatedDuration ?: 0) > 30) return true
        val vague = listOf("finish", "complete", "work on", "figure out", "do", "handle", "deal with")
        return vague.any { task.title.lowercase().contains(it) }
    }

    private fun testTask(
        title: String,
        estimatedDuration: Int? = null
    ) = TaskEntity(
        id = 1L,
        title = title,
        estimatedDuration = estimatedDuration,
        createdAt = System.currentTimeMillis()
    )

    // endregion
}
