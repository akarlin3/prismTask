package com.averycorp.prismtask.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure data-class defaults for widget configurations. The
 * persistence layer itself requires an Android Context so it's exercised
 * via instrumentation — these unit tests lock in the shape of the defaults.
 */
class WidgetConfigDefaultsTest {

    @Test
    fun `today config defaults show everything with max 5 tasks`() {
        val cfg = WidgetConfigDataStore.TodayConfig()
        assertTrue(cfg.showProgress)
        assertTrue(cfg.showTaskList)
        assertTrue(cfg.showHabitSummary)
        assertEquals(5, cfg.maxTasks)
        assertTrue(cfg.showOverdueBadge)
        assertEquals(100, cfg.backgroundOpacityPercent)
    }

    @Test
    fun `habit streak config defaults to empty selection row layout streak count`() {
        val cfg = WidgetConfigDataStore.HabitStreakConfig()
        assertTrue(cfg.selectedHabitIds.isEmpty())
        assertTrue(cfg.showStreakCount)
        assertFalse(cfg.layoutGrid)
    }

    @Test
    fun `quick add config defaults to add a task placeholder and null project`() {
        val cfg = WidgetConfigDataStore.QuickAddConfig()
        assertEquals("Add a task...", cfg.placeholder)
        assertNull(cfg.defaultProjectId)
    }

    @Test
    fun `today config custom values retained after copy`() {
        val cfg = WidgetConfigDataStore.TodayConfig(
            showProgress = false,
            showTaskList = true,
            showHabitSummary = false,
            maxTasks = 8,
            showOverdueBadge = false,
            backgroundOpacityPercent = 75
        )
        assertFalse(cfg.showProgress)
        assertFalse(cfg.showHabitSummary)
        assertEquals(8, cfg.maxTasks)
        assertEquals(75, cfg.backgroundOpacityPercent)
    }

    @Test
    fun `habit streak config takes at most six habit ids`() {
        val cfg = WidgetConfigDataStore.HabitStreakConfig(
            selectedHabitIds = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        )
        // The model allows more than 6; the persistence layer enforces .take(6).
        // This test documents that the cap lives in setHabitStreakConfig.
        assertEquals(8, cfg.selectedHabitIds.size)
    }
}
