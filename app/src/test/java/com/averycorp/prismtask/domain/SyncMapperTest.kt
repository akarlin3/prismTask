package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMapperTest {
    // --- Task ---

    @Test
    fun task_roundTrip() {
        val task = TaskEntity(
            id = 1,
            title = "Test Task",
            description = "desc",
            dueDate = 1000L,
            priority = 3,
            isCompleted = false,
            createdAt = 500L,
            updatedAt = 600L
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
    fun task_sourceHabitId_roundTrip() {
        // sourceHabitId is the *local* row id; the sync map carries the habit's
        // cloud id so cross-device resolution can happen. SyncService translates
        // cloud↔local on push and pull; the mapper just ferries the fields.
        val task = TaskEntity(
            id = 1,
            title = "Habit Task",
            sourceHabitId = 42L,
            createdAt = 500L,
            updatedAt = 600L
        )
        val map = SyncMapper.taskToMap(task, sourceHabitCloudId = "cloudHabit42")
        assertEquals("cloudHabit42", map["sourceHabitId"])

        val restored = SyncMapper.mapToTask(map, localId = 1, sourceHabitLocalId = 42L)
        assertEquals(42L, restored.sourceHabitId)
    }

    @Test
    fun task_sourceHabitId_absentInMap_producesNullEntity() {
        val map = mapOf<String, Any?>("title" to "No habit", "createdAt" to 0L, "updatedAt" to 0L)
        val restored = SyncMapper.mapToTask(map, localId = 1)
        assertNull(restored.sourceHabitId)
    }

    @Test
    fun task_nullFields_handleGracefully() {
        val map = mapOf<String, Any?>("title" to "Test", "createdAt" to 100L, "updatedAt" to 200L)
        val task = SyncMapper.mapToTask(map)
        assertEquals("Test", task.title)
        assertNull(task.description)
        assertNull(task.dueDate)
        assertNull(task.sourceHabitId)
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
        val project =
            ProjectEntity(id = 1, name = "Work", color = "#FF0000", icon = "\uD83D\uDCC1", createdAt = 100, updatedAt = 200)
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
            id = 1,
            name = "Exercise",
            description = "Daily run",
            targetFrequency = 1,
            frequencyPeriod = "daily",
            color = "#4A90D9",
            icon = "\uD83C\uDFC3",
            category = "Fitness",
            createdAt = 100,
            updatedAt = 200
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
        val habit =
            HabitEntity(id = 1, name = "t", isArchived = true, createDailyTask = true, createdAt = 0, updatedAt = 0)
        val map = SyncMapper.habitToMap(habit)
        val restored = SyncMapper.mapToHabit(map, 1)
        assertEquals(true, restored.isArchived)
        assertEquals(true, restored.createDailyTask)
    }

    @Test
    fun habit_showStreak_roundTrip() {
        val habit = HabitEntity(id = 1, name = "t", showStreak = true, createdAt = 0, updatedAt = 0)
        val map = SyncMapper.habitToMap(habit)
        assertEquals(true, map["showStreak"])
        val restored = SyncMapper.mapToHabit(map, 1)
        assertEquals(true, restored.showStreak)
    }

    @Test
    fun habit_showStreak_defaultsFalse_whenMissing() {
        val map = mapOf<String, Any?>("name" to "Test")
        val habit = SyncMapper.mapToHabit(map)
        assertEquals(false, habit.showStreak)
    }

    // --- Habit Completion ---

    @Test
    fun habitCompletion_roundTrip() {
        val completion = HabitCompletionEntity(
            id = 1,
            habitId = 5,
            completedDate = 1000L,
            completedAt = 1100L,
            notes = "felt good"
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

    // --- Automation Rule (covers PR #1069 / #1070 test-coverage gap; see
    //     AUTOMATION_VALIDATION_T2_T4_AUDIT.md § B.6) ---

    @Test
    fun automationRule_roundTrip_preservesAllFields() {
        val rule = AutomationRuleEntity(
            id = 42L,
            cloudId = "cloud_rule_42",
            name = "Morning routine",
            description = "Trigger morning tasks at 07:00",
            enabled = true,
            priority = 5,
            isBuiltIn = false,
            templateKey = "starter.stay_on_top.morning_routine",
            triggerJson = """{"type":"TIME_OF_DAY","hour":7,"minute":0}""",
            conditionJson = """{"op":"EQ","field":"task.priority","value":3}""",
            actionJson = """[{"type":"notify","title":"Good morning"}]""",
            lastFiredAt = 1_700_000_000_000L,
            fireCount = 17,
            dailyFireCount = 1,
            dailyFireCountDate = "2026-05-04",
            createdAt = 1_690_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        val map = SyncMapper.automationRuleToMap(rule)
        // localId is what the remote round-trip carries; cloudId is supplied
        // out-of-band to mapToAutomationRule (mirrors how SyncService calls it).
        val restored = SyncMapper.mapToAutomationRule(map, localId = 42L, cloudId = "cloud_rule_42")

        assertEquals(rule.id, restored.id)
        assertEquals(rule.cloudId, restored.cloudId)
        assertEquals(rule.name, restored.name)
        assertEquals(rule.description, restored.description)
        assertEquals(rule.enabled, restored.enabled)
        assertEquals(rule.priority, restored.priority)
        assertEquals(rule.isBuiltIn, restored.isBuiltIn)
        assertEquals(rule.templateKey, restored.templateKey)
        assertEquals(rule.triggerJson, restored.triggerJson)
        assertEquals(rule.conditionJson, restored.conditionJson)
        assertEquals(rule.actionJson, restored.actionJson)
        assertEquals(rule.lastFiredAt, restored.lastFiredAt)
        assertEquals(rule.fireCount, restored.fireCount)
        assertEquals(rule.dailyFireCount, restored.dailyFireCount)
        assertEquals(rule.dailyFireCountDate, restored.dailyFireCountDate)
        assertEquals(rule.createdAt, restored.createdAt)
        assertEquals(rule.updatedAt, restored.updatedAt)
    }

    @Test
    fun automationRule_roundTrip_handlesNullableFields() {
        val rule = AutomationRuleEntity(
            id = 1L,
            cloudId = null,
            name = "Minimal",
            description = null,
            templateKey = null,
            triggerJson = """{"type":"MANUAL"}""",
            conditionJson = null,
            actionJson = "[]",
            lastFiredAt = null,
            dailyFireCountDate = null,
            createdAt = 0L,
            updatedAt = 0L
        )
        val map = SyncMapper.automationRuleToMap(rule)
        val restored = SyncMapper.mapToAutomationRule(map, localId = 1L, cloudId = null)
        assertNull(restored.description)
        assertNull(restored.templateKey)
        assertNull(restored.conditionJson)
        assertNull(restored.lastFiredAt)
        assertNull(restored.dailyFireCountDate)
        assertEquals("[]", restored.actionJson)
    }

    @Test
    fun automationRule_toMap_emitsUpdatedAtForLww() {
        val rule = AutomationRuleEntity(
            id = 1L,
            name = "n",
            triggerJson = "{}",
            actionJson = "[]",
            createdAt = 100L,
            updatedAt = 999L
        )
        val map = SyncMapper.automationRuleToMap(rule)
        // updatedAt must round-trip — without it, last-write-wins reconciliation
        // in pullRoomConfigFamily would treat every pull as fresher than local.
        assertEquals(999L, (map["updatedAt"] as? Number)?.toLong())
        assertTrue(
            "automation_rule map must carry updatedAt for LWW (audit § B.3)",
            map.containsKey("updatedAt")
        )
    }
}
