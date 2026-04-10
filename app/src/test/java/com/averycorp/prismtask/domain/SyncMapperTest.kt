package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncMapperTest {

    // --- Task ---

    @Test
    fun task_roundTrip() {
        val task = TaskEntity(
            id = 1, title = "Test Task", description = "desc",
            dueDate = 1000L, priority = 3, isCompleted = false,
            createdAt = 500L, updatedAt = 600L
        )
        val map = SyncMapper.taskToMap(task, listOf("cloud_tag_1"))
        val restored = SyncMapper.mapToTask(map, 1)
        assertEquals("Test Task", restored.title)
        assertEquals("desc", restored.description)
        assertEquals(1000L, restored.dueDate)
        assertEquals(3, restored.priority)
        assertEquals(false, restored.isCompleted)
    }

    @Test
    fun task_withTagIds() {
        val task = TaskEntity(id = 1, title = "t", createdAt = 0, updatedAt = 0)
        val map = SyncMapper.taskToMap(task, listOf("a", "b", "c"))
        @Suppress("UNCHECKED_CAST")
        val tags = map["tags"] as List<String>
        assertEquals(3, tags.size)
        assertEquals("a", tags[0])
    }

    @Test
    fun task_nullFields_handleGracefully() {
        val map = mapOf<String, Any?>("title" to "Test", "createdAt" to 100L, "updatedAt" to 200L)
        val task = SyncMapper.mapToTask(map)
        assertEquals("Test", task.title)
        assertNull(task.description)
        assertNull(task.dueDate)
        assertEquals(0, task.priority)
    }

    @Test
    fun task_missingTitle_defaultsEmpty() {
        val map = emptyMap<String, Any?>()
        val task = SyncMapper.mapToTask(map)
        assertEquals("", task.title)
    }

    // --- Project ---

    @Test
    fun project_roundTrip() {
        val project = ProjectEntity(id = 1, name = "Work", color = "#FF0000", icon = "\uD83D\uDCC1", createdAt = 100, updatedAt = 200)
        val map = SyncMapper.projectToMap(project)
        val restored = SyncMapper.mapToProject(map, 1)
        assertEquals("Work", restored.name)
        assertEquals("#FF0000", restored.color)
        assertEquals("\uD83D\uDCC1", restored.icon)
    }

    @Test
    fun project_missingFields_usesDefaults() {
        val map = mapOf<String, Any?>("name" to "Test")
        val project = SyncMapper.mapToProject(map)
        assertEquals("Test", project.name)
        assertEquals("#4A90D9", project.color)
    }

    // --- Tag ---

    @Test
    fun tag_roundTrip() {
        val tag = TagEntity(id = 1, name = "urgent", color = "#FF0000", createdAt = 100)
        val map = SyncMapper.tagToMap(tag)
        val restored = SyncMapper.mapToTag(map, 1)
        assertEquals("urgent", restored.name)
        assertEquals("#FF0000", restored.color)
    }

    @Test
    fun tag_missingColor_usesDefault() {
        val map = mapOf<String, Any?>("name" to "test")
        val tag = SyncMapper.mapToTag(map)
        assertEquals("#6B7280", tag.color)
    }

    // --- Habit ---

    @Test
    fun habit_roundTrip() {
        val habit = HabitEntity(
            id = 1, name = "Exercise", description = "Daily run",
            targetFrequency = 1, frequencyPeriod = "daily",
            color = "#4A90D9", icon = "\uD83C\uDFC3",
            category = "Fitness", createdAt = 100, updatedAt = 200
        )
        val map = SyncMapper.habitToMap(habit)
        val restored = SyncMapper.mapToHabit(map, 1)
        assertEquals("Exercise", restored.name)
        assertEquals("Daily run", restored.description)
        assertEquals(1, restored.targetFrequency)
        assertEquals("daily", restored.frequencyPeriod)
        assertEquals("Fitness", restored.category)
    }

    @Test
    fun habit_missingFields_usesDefaults() {
        val map = mapOf<String, Any?>("name" to "Test")
        val habit = SyncMapper.mapToHabit(map)
        assertEquals("Test", habit.name)
        assertEquals(1, habit.targetFrequency)
        assertEquals("daily", habit.frequencyPeriod)
        assertEquals("#4A90D9", habit.color)
    }

    @Test
    fun habit_booleanFields() {
        val habit = HabitEntity(id = 1, name = "t", isArchived = true, createDailyTask = true, createdAt = 0, updatedAt = 0)
        val map = SyncMapper.habitToMap(habit)
        val restored = SyncMapper.mapToHabit(map, 1)
        assertEquals(true, restored.isArchived)
        assertEquals(true, restored.createDailyTask)
    }

    // --- Habit Completion ---

    @Test
    fun habitCompletion_roundTrip() {
        val completion = HabitCompletionEntity(
            id = 1, habitId = 5, completedDate = 1000L,
            completedAt = 1100L, notes = "felt good"
        )
        val map = SyncMapper.habitCompletionToMap(completion, "cloud_habit_1")
        assertEquals("cloud_habit_1", map["habitCloudId"])
        assertEquals(1000L, map["completedDate"])
        assertEquals("felt good", map["notes"])
    }

    @Test
    fun habitCompletion_mapToEntity() {
        val map = mapOf<String, Any?>(
            "habitCloudId" to "cloud1",
            "completedDate" to 2000L,
            "completedAt" to 2100L,
            "notes" to "test note"
        )
        val completion = SyncMapper.mapToHabitCompletion(map, localId = 0, habitLocalId = 7)
        assertEquals(7L, completion.habitId)
        assertEquals(2000L, completion.completedDate)
        assertEquals("test note", completion.notes)
    }
}
