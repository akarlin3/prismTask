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
    fun `today config defaults show everything with max 8 tasks`() {
        // Default raised from 5 → 8 in the A3 fix so a default-config large
        // widget renders 8 rows again. TodayWidget's size-tier-cap then
        // collapses to 3 for small/medium. Restores prior behavior from
        // before PR #987 commit 4b9fdc21 removed `if (isLarge) 8 else 3`.
        val cfg = WidgetConfigDataStore.TodayConfig()
        assertTrue(cfg.showProgress)
        assertTrue(cfg.showTaskList)
        assertTrue(cfg.showHabitSummary)
        assertEquals(8, cfg.maxTasks)
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

    // --- New tests for W11 ---

    @Test
    fun `today config maxTasks valid range`() {
        val cfg1 = WidgetConfigDataStore.TodayConfig(maxTasks = 1)
        assertEquals(1, cfg1.maxTasks)
        val cfg20 = WidgetConfigDataStore.TodayConfig(maxTasks = 20)
        assertEquals(20, cfg20.maxTasks)
    }

    @Test
    fun `today config opacity valid range`() {
        val cfg = WidgetConfigDataStore.TodayConfig(backgroundOpacityPercent = 60)
        assertEquals(60, cfg.backgroundOpacityPercent)
        val full = WidgetConfigDataStore.TodayConfig(backgroundOpacityPercent = 100)
        assertEquals(100, full.backgroundOpacityPercent)
    }

    @Test
    fun `habit streak config grid layout toggle`() {
        val row = WidgetConfigDataStore.HabitStreakConfig(layoutGrid = false)
        assertFalse(row.layoutGrid)
        val grid = WidgetConfigDataStore.HabitStreakConfig(layoutGrid = true)
        assertTrue(grid.layoutGrid)
    }

    @Test
    fun `quick add config custom placeholder`() {
        val cfg = WidgetConfigDataStore.QuickAddConfig(placeholder = "What needs doing?")
        assertEquals("What needs doing?", cfg.placeholder)
    }

    @Test
    fun `habit streak config selectedHabitIds round trip`() {
        val ids = listOf(10L, 20L, 30L)
        val cfg = WidgetConfigDataStore.HabitStreakConfig(selectedHabitIds = ids)
        assertEquals(listOf(10L, 20L, 30L), cfg.selectedHabitIds)
    }

    @Test
    fun `today config data class copy preserves all fields`() {
        val original = WidgetConfigDataStore.TodayConfig(
            showProgress = false,
            showTaskList = false,
            showHabitSummary = false,
            maxTasks = 3,
            showOverdueBadge = false,
            backgroundOpacityPercent = 80
        )
        val copy = original.copy(maxTasks = 10)
        assertFalse(copy.showProgress) // preserved
        assertEquals(10, copy.maxTasks) // updated
        assertEquals(80, copy.backgroundOpacityPercent) // preserved
    }
}
