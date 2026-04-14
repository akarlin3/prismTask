package com.averycorp.prismtask.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for [WidgetActions]: parameter bundle construction and key validity.
 * Action callback execution requires a running Glance host, so only the
 * pure helper functions are unit-testable.
 */
class WidgetActionsTest {
    @Test
    fun `taskIdParams builds correct parameter bundle`() {
        val params = taskIdParams(42L)
        val taskId = params[WidgetActionKeys.TASK_ID]
        assertNotNull(taskId)
        assertEquals(42L, taskId)
    }

    @Test
    fun `habitIdParams builds correct parameter bundle`() {
        val params = habitIdParams(99L)
        val habitId = params[WidgetActionKeys.HABIT_ID]
        assertNotNull(habitId)
        assertEquals(99L, habitId)
    }

    @Test
    fun `taskIdParams different ids produce different bundles`() {
        val params1 = taskIdParams(1L)
        val params2 = taskIdParams(2L)
        assertEquals(1L, params1[WidgetActionKeys.TASK_ID])
        assertEquals(2L, params2[WidgetActionKeys.TASK_ID])
    }

    @Test
    fun `habitIdParams different ids produce different bundles`() {
        val params1 = habitIdParams(10L)
        val params2 = habitIdParams(20L)
        assertEquals(10L, params1[WidgetActionKeys.HABIT_ID])
        assertEquals(20L, params2[WidgetActionKeys.HABIT_ID])
    }

    @Test
    fun `task id key name is stable`() {
        assertEquals("prismtask-widget-task-id", WidgetActionKeys.TASK_ID.name)
    }

    @Test
    fun `habit id key name is stable`() {
        assertEquals("prismtask-widget-habit-id", WidgetActionKeys.HABIT_ID.name)
    }
}
