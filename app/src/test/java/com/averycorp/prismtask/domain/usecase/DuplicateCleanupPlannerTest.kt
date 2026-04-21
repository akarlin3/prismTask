package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCleanupPlannerTest {
    private fun task(
        id: Long,
        title: String,
        dueDate: Long? = null,
        description: String? = null,
        notes: String? = null,
        reminderOffset: Long? = null,
        recurrenceRule: String? = null,
        projectId: Long? = null,
        estimatedDuration: Int? = null,
        dueTime: Long? = null,
        parentTaskId: Long? = null,
        isCompleted: Boolean = false,
        archivedAt: Long? = null,
        createdAt: Long = 1_000L
    ): TaskEntity = TaskEntity(
        id = id,
        title = title,
        description = description,
        notes = notes,
        dueDate = dueDate,
        dueTime = dueTime,
        reminderOffset = reminderOffset,
        recurrenceRule = recurrenceRule,
        projectId = projectId,
        estimatedDuration = estimatedDuration,
        parentTaskId = parentTaskId,
        isCompleted = isCompleted,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun habit(
        id: Long,
        name: String,
        targetFrequency: Int = 1,
        frequencyPeriod: String = "daily",
        description: String? = null,
        reminderTime: Long? = null,
        category: String? = null,
        reminderIntervalMillis: Long? = null,
        isArchived: Boolean = false,
        isBuiltIn: Boolean = false,
        templateKey: String? = null,
        createdAt: Long = 1_000L
    ): HabitEntity = HabitEntity(
        id = id,
        name = name,
        description = description,
        targetFrequency = targetFrequency,
        frequencyPeriod = frequencyPeriod,
        reminderTime = reminderTime,
        category = category,
        reminderIntervalMillis = reminderIntervalMillis,
        isArchived = isArchived,
        isBuiltIn = isBuiltIn,
        templateKey = templateKey,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    // ---------------- Tasks ----------------

    @Test
    fun `empty task list returns empty`() {
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(emptyList(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unique tasks are not flagged`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L),
            task(2, "Walk dog", dueDate = 100L),
            task(3, "Buy milk", dueDate = 200L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `exact duplicate same title same due date drops one`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L),
            task(2, "Buy milk", dueDate = 100L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertEquals(1, result.size)
    }

    @Test
    fun `title match normalizes whitespace and case`() {
        val tasks = listOf(
            task(1, "Buy Milk", dueDate = 100L),
            task(2, "  buy milk  ", dueDate = 100L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertEquals(1, result.size)
    }

    @Test
    fun `same title different due date are not duplicates`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L),
            task(2, "Buy milk", dueDate = 200L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `same title both null due date are duplicates`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = null),
            task(2, "Buy milk", dueDate = null)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertEquals(1, result.size)
    }

    @Test
    fun `most complete task is kept`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L),
            task(2, "Buy milk", dueDate = 100L, description = "2 gallons"),
            task(3, "Buy milk", dueDate = 100L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        // Task 2 has a description so it scores higher and is kept.
        assertEquals(listOf<Long>(1L, 3L).sorted(), result.sorted())
    }

    @Test
    fun `extras like subtasks and tags boost completeness score`() {
        val tasks = listOf(
            task(1, "Plan trip", dueDate = 100L, description = "Italy"),
            task(2, "Plan trip", dueDate = 100L)
        )
        val extras = mapOf(
            2L to DuplicateCleanupPlanner.TaskExtras(subtaskCount = 5, tagCount = 2)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, extras)
        // Task 2's extras (+7) outweigh task 1's description (+1).
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `tie on completeness keeps oldest by createdAt`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L, createdAt = 2_000L),
            task(2, "Buy milk", dueDate = 100L, createdAt = 1_000L),
            task(3, "Buy milk", dueDate = 100L, createdAt = 3_000L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        // Oldest (id=2, createdAt=1000) wins.
        assertEquals(listOf<Long>(1L, 3L).sorted(), result.sorted())
    }

    @Test
    fun `archived tasks are excluded from duplicate detection`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L, archivedAt = 500L),
            task(2, "Buy milk", dueDate = 100L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `completed tasks are excluded from duplicate detection`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L, isCompleted = true),
            task(2, "Buy milk", dueDate = 100L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `subtasks are excluded from duplicate detection`() {
        val tasks = listOf(
            task(1, "Step one", dueDate = 100L, parentTaskId = 99L),
            task(2, "Step one", dueDate = 100L, parentTaskId = 99L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `three identical tasks drop two`() {
        val tasks = listOf(
            task(1, "Buy milk", dueDate = 100L, createdAt = 1_000L),
            task(2, "Buy milk", dueDate = 100L, createdAt = 2_000L),
            task(3, "Buy milk", dueDate = 100L, createdAt = 3_000L)
        )
        val result = DuplicateCleanupPlanner.findTaskDuplicatesToDelete(tasks, emptyMap())
        assertEquals(listOf<Long>(2L, 3L).sorted(), result.sorted())
    }

    // ---------------- Habits ----------------

    @Test
    fun `unique habits are not flagged`() {
        val habits = listOf(
            habit(1, "Meditate"),
            habit(2, "Exercise")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `exact duplicate habit is dropped`() {
        val habits = listOf(
            habit(1, "Meditate"),
            habit(2, "Meditate")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertEquals(1, result.size)
    }

    @Test
    fun `habit with different frequency is not duplicate`() {
        val habits = listOf(
            habit(1, "Meditate", frequencyPeriod = "daily"),
            habit(2, "Meditate", frequencyPeriod = "weekly")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `habit with more completions is kept`() {
        val habits = listOf(
            habit(1, "Meditate"),
            habit(2, "Meditate")
        )
        val extras = mapOf(
            1L to DuplicateCleanupPlanner.HabitExtras(completionCount = 30, logCount = 0),
            2L to DuplicateCleanupPlanner.HabitExtras(completionCount = 1, logCount = 0)
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, extras)
        assertEquals(listOf(2L), result)
    }

    @Test
    fun `archived habits are excluded from duplicate detection`() {
        val habits = listOf(
            habit(1, "Meditate", isArchived = true),
            habit(2, "Meditate")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `habit tie breaks on oldest created`() {
        val habits = listOf(
            habit(1, "Meditate", createdAt = 5_000L),
            habit(2, "Meditate", createdAt = 2_000L)
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `habit description and category count toward completeness`() {
        val habits = listOf(
            habit(1, "Read", description = "30 minutes", category = "Learning"),
            habit(2, "Read")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertEquals(listOf(2L), result)
    }

    // --- Built-in habit templateKey-based detection ---

    @Test
    fun `built-in habits with same templateKey are duplicates even with different names`() {
        val habits = listOf(
            habit(1, "Drink Water", isBuiltIn = true, templateKey = "drink_water"),
            habit(2, "Drink 8 Glasses", isBuiltIn = true, templateKey = "drink_water")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertEquals(1, result.size)
    }

    @Test
    fun `built-in habits with different templateKeys are not duplicates`() {
        val habits = listOf(
            habit(1, "Meditate", isBuiltIn = true, templateKey = "meditate"),
            habit(2, "Exercise", isBuiltIn = true, templateKey = "exercise")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `non-built-in habits with same name as built-in are not grouped by templateKey`() {
        val habits = listOf(
            habit(1, "Meditate", isBuiltIn = true, templateKey = "meditate"),
            habit(2, "Meditate", isBuiltIn = false, templateKey = null)
        )
        // Same name → caught by name-based pass, not templateKey pass
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertEquals(1, result.size)
    }

    @Test
    fun `planHabitDuplicates exposes keeperId for built-in templateKey group`() {
        val habits = listOf(
            habit(1, "Drink Water", isBuiltIn = true, templateKey = "drink_water"),
            habit(2, "Drink 8 Glasses", isBuiltIn = true, templateKey = "drink_water",
                description = "stay hydrated")
        )
        // Habit 2 has a description so it scores higher and is the keeper.
        val merges = DuplicateCleanupPlanner.planHabitDuplicates(habits, emptyMap())
        assertEquals(1, merges.size)
        assertEquals(2L, merges[0].keeperId)
        assertEquals(listOf(1L), merges[0].loserIds)
    }

    @Test
    fun `built-in habits with same templateKey not double-counted in name pass`() {
        // Both habits share templateKey AND same name — should produce exactly one merge group.
        val habits = listOf(
            habit(1, "Meditate", isBuiltIn = true, templateKey = "meditate"),
            habit(2, "Meditate", isBuiltIn = true, templateKey = "meditate")
        )
        val merges = DuplicateCleanupPlanner.planHabitDuplicates(habits, emptyMap())
        assertEquals(1, merges.size)
        assertEquals(1, merges[0].loserIds.size)
    }

    @Test
    fun `archived built-in habit with same templateKey is excluded`() {
        val habits = listOf(
            habit(1, "Drink Water", isBuiltIn = true, templateKey = "drink_water", isArchived = true),
            habit(2, "Drink 8 Glasses", isBuiltIn = true, templateKey = "drink_water")
        )
        val result = DuplicateCleanupPlanner.findHabitDuplicatesToDelete(habits, emptyMap())
        assertTrue(result.isEmpty())
    }

    // ---------------- Projects ----------------

    private fun project(
        id: Long,
        name: String,
        description: String? = null,
        themeColorKey: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        archivedAt: Long? = null,
        createdAt: Long = 1_000L
    ): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        description = description,
        themeColorKey = themeColorKey,
        startDate = startDate,
        endDate = endDate,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun `empty project list returns empty`() {
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unique projects are not flagged`() {
        val projects = listOf(
            project(1, "Work"),
            project(2, "Personal")
        )
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(projects)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `exact duplicate project is dropped`() {
        val projects = listOf(
            project(1, "Work"),
            project(2, "Work")
        )
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(projects)
        assertEquals(1, result.size)
    }

    @Test
    fun `project name match normalizes whitespace and case`() {
        val projects = listOf(
            project(1, "My Project"),
            project(2, "  my project  ")
        )
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(projects)
        assertEquals(1, result.size)
    }

    @Test
    fun `archived projects are excluded from duplicate detection`() {
        val projects = listOf(
            project(1, "Work", archivedAt = 500L),
            project(2, "Work")
        )
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(projects)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `most complete project is kept`() {
        val projects = listOf(
            project(1, "Work"),
            project(2, "Work", description = "Day job tasks", themeColorKey = "blue")
        )
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(projects)
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `project tie breaks on oldest createdAt`() {
        val projects = listOf(
            project(1, "Work", createdAt = 3_000L),
            project(2, "Work", createdAt = 1_000L)
        )
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(projects)
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `three duplicate projects drop two`() {
        val projects = listOf(
            project(1, "Work", createdAt = 1_000L),
            project(2, "Work", createdAt = 2_000L),
            project(3, "Work", createdAt = 3_000L)
        )
        val result = DuplicateCleanupPlanner.findProjectDuplicatesToDelete(projects)
        assertEquals(listOf<Long>(2L, 3L).sorted(), result.sorted())
    }
}
