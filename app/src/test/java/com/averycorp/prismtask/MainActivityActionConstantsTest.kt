package com.averycorp.prismtask

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the widget deep-link contract: widget intents put the action
 * string under [MainActivity.EXTRA_LAUNCH_ACTION] and `NavGraph` routes
 * by string match. If the constant value drifts in either direction
 * the deep-link silently no-ops, which is exactly the bug
 * `docs/audits/TIMER_WIDGET_BROKEN_AUDIT.md` Item 1 caught.
 */
class MainActivityActionConstantsTest {
    @Test
    fun `action constants match the strings widgets put on intents`() {
        assertEquals("quick_add", MainActivity.ACTION_QUICK_ADD)
        assertEquals("open_templates", MainActivity.ACTION_OPEN_TEMPLATES)
        assertEquals("voice_input", MainActivity.ACTION_VOICE_INPUT)
        assertEquals("open_habits", MainActivity.ACTION_OPEN_HABITS)
        assertEquals("open_timer", MainActivity.ACTION_OPEN_TIMER)
    }

    @Test
    fun `extra key is stable for cross-process intent delivery`() {
        assertEquals(
            "com.averycorp.prismtask.LAUNCH_ACTION",
            MainActivity.EXTRA_LAUNCH_ACTION
        )
    }
}
