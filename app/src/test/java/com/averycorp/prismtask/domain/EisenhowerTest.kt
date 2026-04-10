package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EisenhowerTest {

    @Test
    fun tasks_groupedByQuadrant() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "Urgent bug", eisenhowerQuadrant = "Q1", createdAt = 0, updatedAt = 0),
            TaskEntity(id = 2, title = "Plan vacation", eisenhowerQuadrant = "Q2", createdAt = 0, updatedAt = 0),
            TaskEntity(id = 3, title = "Reply email", eisenhowerQuadrant = "Q3", createdAt = 0, updatedAt = 0),
            TaskEntity(id = 4, title = "Browse social", eisenhowerQuadrant = "Q4", createdAt = 0, updatedAt = 0),
            TaskEntity(id = 5, title = "Another Q1", eisenhowerQuadrant = "Q1", createdAt = 0, updatedAt = 0),
            TaskEntity(id = 6, title = "Uncategorized", eisenhowerQuadrant = null, createdAt = 0, updatedAt = 0)
        )
        val grouped = tasks.filter { it.eisenhowerQuadrant != null }.groupBy { it.eisenhowerQuadrant!! }
        assertEquals(2, grouped["Q1"]?.size)
        assertEquals(1, grouped["Q2"]?.size)
        assertEquals(1, grouped["Q3"]?.size)
        assertEquals(1, grouped["Q4"]?.size)
        assertNull(grouped[""])
    }

    @Test
    fun manualOverride_changesQuadrant() {
        val task = TaskEntity(
            id = 1, title = "Test", eisenhowerQuadrant = "Q1",
            eisenhowerReason = "AI categorized", createdAt = 0, updatedAt = 0
        )
        val moved = task.copy(eisenhowerQuadrant = "Q2", eisenhowerReason = "Manually moved")
        assertEquals("Q2", moved.eisenhowerQuadrant)
        assertEquals("Manually moved", moved.eisenhowerReason)
    }

    @Test
    fun syncMapper_roundTrip_eisenhowerFields() {
        val task = TaskEntity(
            id = 1, title = "Sync test",
            eisenhowerQuadrant = "Q3",
            eisenhowerUpdatedAt = 1234567890L,
            eisenhowerReason = "Due today, low priority",
            createdAt = 100L, updatedAt = 200L
        )
        val map = SyncMapper.taskToMap(task)
        val restored = SyncMapper.mapToTask(map, 1)
        assertEquals("Q3", restored.eisenhowerQuadrant)
        assertEquals(1234567890L, restored.eisenhowerUpdatedAt)
        assertEquals("Due today, low priority", restored.eisenhowerReason)
    }

    @Test
    fun syncMapper_nullEisenhower_preserved() {
        val task = TaskEntity(id = 1, title = "No quadrant", createdAt = 0, updatedAt = 0)
        val map = SyncMapper.taskToMap(task)
        val restored = SyncMapper.mapToTask(map, 1)
        assertNull(restored.eisenhowerQuadrant)
        assertNull(restored.eisenhowerUpdatedAt)
        assertNull(restored.eisenhowerReason)
    }
}
