package com.averycorp.prismtask.domain.model

import com.averycorp.prismtask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TaskMenuActionTest {
    @Test
    fun `defaults returns eight actions in declared order`() {
        val defaults = TaskMenuAction.defaults()
        assertEquals(8, defaults.size)
        assertEquals(TaskMenuAction.QUICK_RESCHEDULE, defaults[0].id)
        assertEquals(TaskMenuAction.DELETE, defaults[7].id)
        // Sorted ascending by order
        assertEquals(defaults.sortedBy { it.order }, defaults)
    }

    @Test
    fun `defaults flag change priority edit tags share disabled by default`() {
        val defaults = TaskMenuAction.defaults().associateBy { it.id }
        assertFalse(defaults[TaskMenuAction.FLAG]!!.enabled)
        assertFalse(defaults[TaskMenuAction.CHANGE_PRIORITY]!!.enabled)
        assertFalse(defaults[TaskMenuAction.EDIT_TAGS]!!.enabled)
        assertFalse(defaults[TaskMenuAction.SHARE]!!.enabled)
        assertTrue(defaults[TaskMenuAction.QUICK_RESCHEDULE]!!.enabled)
        assertTrue(defaults[TaskMenuAction.DELETE]!!.enabled)
    }

    @Test
    fun `mergeWithDefaults drops unknown ids`() {
        val bogus = listOf(TaskMenuAction("nonsense", true, 0))
        val merged = TaskMenuAction.mergeWithDefaults(bogus)
        assertFalse(merged.any { it.id == "nonsense" })
        assertEquals(TaskMenuAction.defaults().size, merged.size)
    }

    @Test
    fun `mergeWithDefaults preserves user enabled state for known ids`() {
        val user = listOf(
            TaskMenuAction(TaskMenuAction.QUICK_RESCHEDULE, enabled = false, order = 0),
            TaskMenuAction(TaskMenuAction.FLAG, enabled = true, order = 1)
        )
        val merged = TaskMenuAction.mergeWithDefaults(user)
        val byId = merged.associateBy { it.id }
        assertFalse(byId[TaskMenuAction.QUICK_RESCHEDULE]!!.enabled)
        assertTrue(byId[TaskMenuAction.FLAG]!!.enabled)
    }

    @Test
    fun `mergeWithDefaults appends missing default actions at end`() {
        val user = listOf(TaskMenuAction(TaskMenuAction.DELETE, enabled = true, order = 0))
        val merged = TaskMenuAction.mergeWithDefaults(user)
        assertEquals(8, merged.size)
        // The single user entry keeps its position 0
        assertEquals(TaskMenuAction.DELETE, merged.first().id)
    }

    @Test
    fun `share text includes title and priority and project`() {
        val task = TaskEntity(
            id = 1,
            title = "Write report",
            priority = 3,
            dueDate = 1_700_000_000_000L
        )
        val text = TaskMenuAction.formatShareText(task, projectName = "Work", locale = Locale.US)
        assertTrue(text.contains("Write report"))
        assertTrue(text.contains("Priority: High"))
        assertTrue(text.contains("Project: Work"))
        assertTrue(text.contains("Due:"))
    }

    @Test
    fun `share text omits project when null`() {
        val task = TaskEntity(id = 1, title = "Walk dog")
        val text = TaskMenuAction.formatShareText(task, projectName = null, locale = Locale.US)
        assertTrue(text.contains("Walk dog"))
        assertFalse(text.contains("Project:"))
    }

    @Test
    fun `share text uses checked box for completed tasks`() {
        val task = TaskEntity(id = 1, title = "Done thing", isCompleted = true)
        val text = TaskMenuAction.formatShareText(task, projectName = null, locale = Locale.US)
        assertTrue(text.startsWith("\u2611"))
    }

    @Test
    fun `share text uses empty box for incomplete tasks`() {
        val task = TaskEntity(id = 1, title = "Pending")
        val text = TaskMenuAction.formatShareText(task, projectName = null, locale = Locale.US)
        assertTrue(text.startsWith("\u2610"))
    }

    @Test
    fun `label for unknown id returns id`() {
        assertEquals("custom_id", TaskMenuAction.labelFor("custom_id"))
        assertEquals("Delete", TaskMenuAction.labelFor(TaskMenuAction.DELETE))
    }
}
