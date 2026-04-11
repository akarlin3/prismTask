package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.TaskDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartDefaultsEngineTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun t(
        priority: Int = 0,
        projectId: Long? = null,
        dueDate: Long? = null,
        reminderOffset: Long? = null,
        estimatedDuration: Int? = null,
        createdAt: Long = now
    ) = TaskEntity(
        title = "t",
        priority = priority,
        projectId = projectId,
        dueDate = dueDate,
        reminderOffset = reminderOffset,
        estimatedDuration = estimatedDuration,
        createdAt = createdAt,
        isCompleted = true,
        completedAt = createdAt
    )

    @Test
    fun `empty history returns empty defaults`() {
        val result = SmartDefaultsEngine.compute(emptyList())
        assertEquals(true, result.isEmpty())
    }

    @Test
    fun `insufficient history returns empty defaults`() {
        val history = List(4) { t(priority = 3) }
        val result = SmartDefaultsEngine.compute(history)
        assertEquals(true, result.isEmpty())
    }

    @Test
    fun `most common priority wins`() {
        val history = listOf(
            t(priority = 3), t(priority = 3), t(priority = 3),
            t(priority = 1), t(priority = 2), t(priority = 4)
        )
        val result = SmartDefaultsEngine.compute(history)
        assertEquals(3, result.priority)
    }

    @Test
    fun `most common project wins`() {
        val history = listOf(
            t(projectId = 10), t(projectId = 10), t(projectId = 10),
            t(projectId = 20), t(projectId = 30), t(projectId = null)
        )
        val result = SmartDefaultsEngine.compute(history)
        assertEquals(10L, result.projectId)
    }

    @Test
    fun `average duration rounded to nearest 15 minutes`() {
        val history = listOf(
            t(estimatedDuration = 20),
            t(estimatedDuration = 30),
            t(estimatedDuration = 40),
            t(estimatedDuration = 50),
            t(estimatedDuration = 60)
        )
        // mean = 40 -> rounds to 45
        val result = SmartDefaultsEngine.compute(history)
        assertEquals(45, result.averageDurationMinutes)
    }

    @Test
    fun `typical due date offset is the most common delta in days`() {
        val history = listOf(
            t(dueDate = now + 1 * day, createdAt = now),
            t(dueDate = now + 1 * day, createdAt = now),
            t(dueDate = now + 7 * day, createdAt = now),
            t(dueDate = now + 1 * day, createdAt = now),
            t(dueDate = now + 30 * day, createdAt = now)
        )
        val result = SmartDefaultsEngine.compute(history)
        assertEquals(1L, result.typicalDueDateOffsetDays)
    }

    @Test
    fun `merge uses base priority over smart and static`() {
        val base = TaskDefaults(defaultPriority = 4)
        val smart = SmartDefaultsEngine.ComputedDefaults(priority = 2, null, null, null, null)
        val static = TaskDefaults(defaultPriority = 1)
        val merged = SmartDefaultsEngine.merge(base, smart, static)
        assertEquals(4, merged.defaultPriority)
    }

    @Test
    fun `merge falls back to smart when base priority unset`() {
        val base = TaskDefaults(defaultPriority = 0)
        val smart = SmartDefaultsEngine.ComputedDefaults(priority = 2, null, null, null, null)
        val static = TaskDefaults(defaultPriority = 1)
        val merged = SmartDefaultsEngine.merge(base, smart, static)
        assertEquals(2, merged.defaultPriority)
    }

    @Test
    fun `merge falls back to static when smart is null`() {
        val base = TaskDefaults(defaultPriority = 0)
        val static = TaskDefaults(defaultPriority = 2)
        val merged = SmartDefaultsEngine.merge(base, smart = null, static = static)
        assertEquals(2, merged.defaultPriority)
    }

    @Test
    fun `merge project id hierarchy base over smart over static`() {
        val base = TaskDefaults(defaultProjectId = 1L)
        val smart = SmartDefaultsEngine.ComputedDefaults(null, projectId = 2L, null, null, null)
        val static = TaskDefaults(defaultProjectId = 3L)
        assertEquals(1L, SmartDefaultsEngine.merge(base, smart, static).defaultProjectId)
        assertEquals(2L, SmartDefaultsEngine.merge(TaskDefaults(), smart, static).defaultProjectId)
        assertEquals(3L, SmartDefaultsEngine.merge(TaskDefaults(), null, static).defaultProjectId)
        assertNull(SmartDefaultsEngine.merge(TaskDefaults(), null, TaskDefaults()).defaultProjectId)
    }
}
