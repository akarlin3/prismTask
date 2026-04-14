package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.usecase.SmartDefaultsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmartDefaultsEngine]. Pure object — no fakes needed.
 */
class SmartDefaultsEngineTest {
    @Test
    fun compute_returnsAllNullsWhenHistoryBelowMinimum() {
        val result = SmartDefaultsEngine.compute(
            completedTasks = listOf(
                TaskEntity(id = 1, title = "a", priority = 2, isCompleted = true),
                TaskEntity(id = 2, title = "b", priority = 2, isCompleted = true)
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun compute_returnsMostCommonPriorityWhenHistorySufficient() {
        val tasks = (1..6).map { i ->
            TaskEntity(id = i.toLong(), title = "t$i", priority = 2, isCompleted = true)
        }
        val result = SmartDefaultsEngine.compute(tasks)
        assertEquals(2, result.priority)
    }

    @Test
    fun compute_ignoresMoreThanMaxHistoryTasks() {
        // Assembled list exceeds MAX_HISTORY; the engine should only look at
        // the first MAX_HISTORY entries.
        val tasks = (1..100).map { i ->
            TaskEntity(id = i.toLong(), title = "t$i", priority = if (i <= 50) 3 else 1, isCompleted = true)
        }
        val result = SmartDefaultsEngine.compute(tasks)
        // Only the first 50 (priority = 3) should drive the common priority.
        assertEquals(3, result.priority)
    }

    @Test
    fun compute_returnsNullProjectWhenNoConsistentProject() {
        val tasks = (1..6).map { i ->
            TaskEntity(
                id = i.toLong(),
                title = "t$i",
                priority = 1,
                projectId = i.toLong(), // each task has a distinct project
                isCompleted = true
            )
        }
        val result = SmartDefaultsEngine.compute(tasks)
        assertNull(result.projectId)
    }
}
