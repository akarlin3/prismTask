package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.NdPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParalysisBreakerTest {

    private val frPrefs = NdPreferences(
        focusReleaseModeEnabled = true,
        paralysisBreakersEnabled = true,
        autoSuggestEnabled = true,
        simplifyChoicesEnabled = true,
        stuckDetectionMinutes = 5,
        adhdModeEnabled = false
    )

    private fun makeTask(
        id: Long = 1,
        title: String = "Task $id",
        priority: Int = 2,
        dueDate: Long? = null,
        estimatedDuration: Int? = null,
        isCompleted: Boolean = false,
        archivedAt: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        priority = priority,
        dueDate = dueDate,
        estimatedDuration = estimatedDuration,
        isCompleted = isCompleted,
        archivedAt = archivedAt
    )

    @Test
    fun `suggestNextTask returns null when FR mode is off`() {
        val prefs = frPrefs.copy(focusReleaseModeEnabled = false)
        val result = ParalysisBreaker.suggestNextTask(listOf(makeTask()), prefs)
        assertNull(result)
    }

    @Test
    fun `suggestNextTask returns null when paralysis breakers disabled`() {
        val prefs = frPrefs.copy(paralysisBreakersEnabled = false)
        val result = ParalysisBreaker.suggestNextTask(listOf(makeTask()), prefs)
        assertNull(result)
    }

    @Test
    fun `suggestNextTask returns null when auto-suggest disabled`() {
        val prefs = frPrefs.copy(autoSuggestEnabled = false)
        val result = ParalysisBreaker.suggestNextTask(listOf(makeTask()), prefs)
        assertNull(result)
    }

    @Test
    fun `suggestNextTask returns null for empty list`() {
        val result = ParalysisBreaker.suggestNextTask(emptyList(), frPrefs)
        assertNull(result)
    }

    @Test
    fun `suggestNextTask skips completed tasks`() {
        val tasks = listOf(
            makeTask(id = 1, isCompleted = true),
            makeTask(id = 2, isCompleted = false, priority = 3)
        )
        val result = ParalysisBreaker.suggestNextTask(tasks, frPrefs)
        assertNotNull(result)
        assertEquals(2L, result!!.id)
    }

    @Test
    fun `suggestNextTask skips archived tasks`() {
        val tasks = listOf(
            makeTask(id = 1, archivedAt = System.currentTimeMillis()),
            makeTask(id = 2, priority = 1)
        )
        val result = ParalysisBreaker.suggestNextTask(tasks, frPrefs)
        assertNotNull(result)
        assertEquals(2L, result!!.id)
    }

    @Test
    fun `suggestNextTask selects highest priority first`() {
        val tasks = listOf(
            makeTask(id = 1, priority = 1),
            makeTask(id = 2, priority = 4),
            makeTask(id = 3, priority = 2)
        )
        val result = ParalysisBreaker.suggestNextTask(tasks, frPrefs)
        assertNotNull(result)
        assertEquals(2L, result!!.id)
    }

    @Test
    fun `suggestNextTask breaks priority ties by due date`() {
        val now = System.currentTimeMillis()
        val tasks = listOf(
            makeTask(id = 1, priority = 3, dueDate = now + 1000000),
            makeTask(id = 2, priority = 3, dueDate = now + 100),
            makeTask(id = 3, priority = 3, dueDate = null) // null goes last
        )
        val result = ParalysisBreaker.suggestNextTask(tasks, frPrefs)
        assertNotNull(result)
        assertEquals(2L, result!!.id)
    }

    @Test
    fun `suggestNextTask with ADHD mode prefers smallest task`() {
        val prefs = frPrefs.copy(adhdModeEnabled = true)
        val tasks = listOf(
            makeTask(id = 1, priority = 4, estimatedDuration = 60),
            makeTask(id = 2, priority = 1, estimatedDuration = 5),
            makeTask(id = 3, priority = 3, estimatedDuration = 30)
        )
        val result = ParalysisBreaker.suggestNextTask(tasks, prefs)
        assertNotNull(result)
        assertEquals(2L, result!!.id) // 5 min is quickest
    }

    @Test
    fun `pickRandom returns from top candidates`() {
        val tasks = (1..10).map { makeTask(id = it.toLong(), priority = it) }
        val results = (1..20).mapNotNull { ParalysisBreaker.pickRandom(tasks, frPrefs) }
        // All picks should be from the top 3 by priority (8, 9, 10)
        assertTrue(results.all { it.priority >= 8 })
    }

    @Test
    fun `pickRandom returns null for empty list`() {
        assertNull(ParalysisBreaker.pickRandom(emptyList(), frPrefs))
    }

    @Test
    fun `simplified priorities has 3 options`() {
        assertEquals(3, ParalysisBreaker.simplifiedPriorities().size)
    }

    @Test
    fun `simplified sort options has 3 options`() {
        assertEquals(3, ParalysisBreaker.simplifiedSortOptions().size)
    }

    @Test
    fun `simplified filters has 3 options`() {
        assertEquals(3, ParalysisBreaker.simplifiedFilters().size)
    }

    @Test
    fun `simplified priorities map to correct internal values`() {
        val priorities = ParalysisBreaker.simplifiedPriorities()
        assertEquals(1, priorities[0].internalPriority) // Not Urgent -> Low
        assertEquals(2, priorities[1].internalPriority) // Normal -> Medium
        assertEquals(3, priorities[2].internalPriority) // Urgent -> High
    }
}
