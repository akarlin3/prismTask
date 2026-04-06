package com.averykarlin.averytask.domain

import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.domain.usecase.UrgencyLevel
import com.averykarlin.averytask.domain.usecase.UrgencyScorer
import org.junit.Assert.*
import org.junit.Test

class UrgencyScorerTest {

    private val now = System.currentTimeMillis()
    private val oneDay = 24 * 60 * 60 * 1000L

    private fun task(
        dueDate: Long? = null,
        priority: Int = 0,
        createdAt: Long = now,
    ) = TaskEntity(
        title = "Test",
        dueDate = dueDate,
        priority = priority,
        createdAt = createdAt,
        updatedAt = now
    )

    @Test
    fun test_noDueDate_lowPriority_newTask_isLow() {
        val score = UrgencyScorer.calculateScore(task())
        assertEquals(UrgencyLevel.LOW, UrgencyScorer.getUrgencyLevel(score))
    }

    @Test
    fun test_overdue_highPriority_isHighOrCritical() {
        val score = UrgencyScorer.calculateScore(
            task(dueDate = now - 3 * oneDay, priority = 3, createdAt = now - 7 * oneDay)
        )
        val level = UrgencyScorer.getUrgencyLevel(score)
        assertTrue(level == UrgencyLevel.HIGH || level == UrgencyLevel.CRITICAL)
    }

    @Test
    fun test_dueToday_noPriority_isMedium() {
        val score = UrgencyScorer.calculateScore(
            task(dueDate = now, priority = 0, createdAt = now - oneDay)
        )
        assertTrue(score >= 0.2f)
    }

    @Test
    fun test_dueTomorrow_lowPriority() {
        val score = UrgencyScorer.calculateScore(
            task(dueDate = now + oneDay, priority = 1)
        )
        assertTrue(score > 0f)
        assertTrue(score < 0.5f)
    }

    @Test
    fun test_urgentPriority_noDueDate() {
        val score = UrgencyScorer.calculateScore(task(priority = 4))
        assertEquals(0.3f, score, 0.05f) // only priority contributes
    }

    @Test
    fun test_oldTask_getsHigherAge() {
        val oldScore = UrgencyScorer.calculateScore(
            task(createdAt = now - 14 * oneDay)
        )
        val newScore = UrgencyScorer.calculateScore(
            task(createdAt = now)
        )
        assertTrue(oldScore > newScore)
    }

    @Test
    fun test_subtasksNoneDone_increasesScore() {
        val withSubtasks = UrgencyScorer.calculateScore(task(), subtaskCount = 5, subtaskCompleted = 0)
        val without = UrgencyScorer.calculateScore(task())
        assertTrue(withSubtasks > without)
    }

    @Test
    fun test_subtasksAllDone_noIncrease() {
        val score = UrgencyScorer.calculateScore(task(), subtaskCount = 3, subtaskCompleted = 3)
        val without = UrgencyScorer.calculateScore(task())
        assertEquals(without, score, 0.01f)
    }

    @Test
    fun test_scoreClampedTo01() {
        val score = UrgencyScorer.calculateScore(
            task(dueDate = now - 30 * oneDay, priority = 4, createdAt = now - 30 * oneDay),
            subtaskCount = 10, subtaskCompleted = 0
        )
        assertTrue(score <= 1.0f)
        assertTrue(score >= 0.0f)
    }

    @Test
    fun test_urgencyLevels() {
        assertEquals(UrgencyLevel.LOW, UrgencyScorer.getUrgencyLevel(0.1f))
        assertEquals(UrgencyLevel.MEDIUM, UrgencyScorer.getUrgencyLevel(0.4f))
        assertEquals(UrgencyLevel.HIGH, UrgencyScorer.getUrgencyLevel(0.6f))
        assertEquals(UrgencyLevel.CRITICAL, UrgencyScorer.getUrgencyLevel(0.8f))
    }
}
