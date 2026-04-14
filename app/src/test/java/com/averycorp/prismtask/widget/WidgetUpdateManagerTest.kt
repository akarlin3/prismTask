package com.averycorp.prismtask.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [WidgetUpdateManager] constants and configuration.
 * Actual widget update calls require Glance runtime context; these tests
 * verify the debounce constants and public API surface.
 */
class WidgetUpdateManagerTest {
    @Test
    fun `debounce millis is 500ms`() {
        assertEquals(500L, WidgetUpdateManager.DEBOUNCE_MILLIS)
    }

    @Test
    fun `debounce window is positive`() {
        val debounce = WidgetUpdateManager.DEBOUNCE_MILLIS
        assert(debounce > 0) { "Debounce window must be positive" }
    }

    @Test
    fun `debounce window under 2 seconds for responsiveness`() {
        val debounce = WidgetUpdateManager.DEBOUNCE_MILLIS
        assert(debounce <= 2000) { "Debounce window should be under 2s for responsiveness" }
    }
}
