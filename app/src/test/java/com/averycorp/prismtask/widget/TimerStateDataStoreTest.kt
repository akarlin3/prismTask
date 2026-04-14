package com.averycorp.prismtask.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TimerWidgetState] data class defaults and transformations.
 * Actual DataStore persistence requires an Android Context; these tests
 * validate the pure model layer.
 */
class TimerStateDataStoreTest {
    @Test
    fun `default state is not running`() {
        val state = TimerWidgetState()
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(0, state.remainingSeconds)
        assertEquals(0, state.totalSeconds)
        assertEquals("work", state.sessionType)
        assertEquals(0, state.currentSession)
        assertEquals(4, state.totalSessions)
        assertNull(state.currentTaskTitle)
    }

    @Test
    fun `running work state`() {
        val state = TimerWidgetState(
            isRunning = true,
            isPaused = false,
            currentTaskTitle = "Write tests",
            remainingSeconds = 1500,
            totalSeconds = 1500,
            sessionType = "work",
            currentSession = 1,
            totalSessions = 4
        )
        assertTrue(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals("Write tests", state.currentTaskTitle)
        assertEquals(1500, state.remainingSeconds)
        assertEquals("work", state.sessionType)
        assertEquals(1, state.currentSession)
    }

    @Test
    fun `paused state`() {
        val state = TimerWidgetState(
            isRunning = false,
            isPaused = true,
            remainingSeconds = 750,
            totalSeconds = 1500,
            sessionType = "work",
            currentSession = 2,
            totalSessions = 4
        )
        assertFalse(state.isRunning)
        assertTrue(state.isPaused)
        assertEquals(750, state.remainingSeconds)
    }

    @Test
    fun `break state`() {
        val state = TimerWidgetState(
            isRunning = true,
            isPaused = false,
            remainingSeconds = 300,
            totalSeconds = 300,
            sessionType = "break",
            currentSession = 2,
            totalSessions = 4
        )
        assertEquals("break", state.sessionType)
        assertTrue(state.isRunning)
    }

    @Test
    fun `state copy preserves fields`() {
        val original = TimerWidgetState(
            isRunning = true,
            remainingSeconds = 1000,
            totalSeconds = 1500,
            sessionType = "work",
            currentSession = 3,
            totalSessions = 4
        )
        val paused = original.copy(isRunning = false, isPaused = true)
        assertFalse(paused.isRunning)
        assertTrue(paused.isPaused)
        assertEquals(1000, paused.remainingSeconds)
        assertEquals(3, paused.currentSession)
    }
}
