package com.averycorp.prismtask.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCardDisplayConfigTest {
    private val gson = Gson()

    @Test
    fun `default config has sensible defaults`() {
        val cfg = TaskCardDisplayConfig()
        assertTrue(cfg.showDueDate)
        assertTrue(cfg.showTime)
        assertTrue(cfg.showPriorityDot)
        assertTrue(cfg.showProjectBadge)
        assertTrue(cfg.showTagChips)
        assertEquals(3, cfg.maxTagChips)
        assertTrue(cfg.showSubtaskCount)
        assertTrue(cfg.showRecurrenceIcon)
        assertFalse(cfg.showUrgencyIndicator)
        assertFalse(cfg.showNotesPreview)
        assertFalse(cfg.showCreationDate)
        assertFalse(cfg.showDurationBadge)
        assertTrue(cfg.showFlagIndicator)
        assertFalse(cfg.showTimeTracked)
        assertFalse(cfg.minimalStyle)
    }

    @Test
    fun `minimal style retains only essentials`() {
        val minimal = TaskCardDisplayConfig().toMinimal()
        assertTrue(minimal.minimalStyle)
        assertTrue(minimal.showDueDate)
        assertTrue(minimal.showPriorityDot)
        assertTrue(minimal.showFlagIndicator)
        assertFalse(minimal.showTime)
        assertFalse(minimal.showTagChips)
        assertFalse(minimal.showSubtaskCount)
        assertFalse(minimal.showProjectBadge)
    }

    @Test
    fun `max tag chips clamped below 1`() {
        val cfg = TaskCardDisplayConfig(maxTagChips = 0).withClampedTagLimit()
        assertEquals(1, cfg.maxTagChips)
    }

    @Test
    fun `max tag chips clamped above 5`() {
        val cfg = TaskCardDisplayConfig(maxTagChips = 99).withClampedTagLimit()
        assertEquals(5, cfg.maxTagChips)
    }

    @Test
    fun `max tag chips inside range is untouched`() {
        val cfg = TaskCardDisplayConfig(maxTagChips = 3).withClampedTagLimit()
        assertEquals(3, cfg.maxTagChips)
    }

    @Test
    fun `json round trip preserves all fields`() {
        val original = TaskCardDisplayConfig(
            showDueDate = false,
            showTime = false,
            showPriorityDot = false,
            maxTagChips = 2,
            showUrgencyIndicator = true,
            showNotesPreview = true,
            minimalStyle = true
        )
        val json = gson.toJson(original)
        val parsed = gson.fromJson(json, TaskCardDisplayConfig::class.java)
        assertEquals(original, parsed)
    }
}
