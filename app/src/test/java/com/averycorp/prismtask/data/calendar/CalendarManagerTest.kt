package com.averycorp.prismtask.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CalendarInfo] data class and calendar sync constants.
 * CalendarManager itself requires Android context (GoogleSignIn), so we test
 * the data model and constants here as pure JVM unit tests.
 */
class CalendarManagerTest {
    @Test
    fun calendarInfo_holdsAllProperties() {
        val info = CalendarInfo(
            id = "test@gmail.com",
            name = "My Calendar",
            color = "#4285F4",
            isPrimary = true
        )
        assertEquals("test@gmail.com", info.id)
        assertEquals("My Calendar", info.name)
        assertEquals("#4285F4", info.color)
        assertTrue(info.isPrimary)
    }

    @Test
    fun calendarInfo_nonPrimaryCalendar() {
        val info = CalendarInfo(
            id = "holidays@group.v.calendar.google.com",
            name = "US Holidays",
            color = "#009688",
            isPrimary = false
        )
        assertFalse(info.isPrimary)
        assertEquals("US Holidays", info.name)
    }

    @Test
    fun calendarInfo_equality() {
        val a = CalendarInfo("id1", "Cal A", "#FF0000", true)
        val b = CalendarInfo("id1", "Cal A", "#FF0000", true)
        val c = CalendarInfo("id2", "Cal B", "#00FF00", false)

        assertEquals(a, b)
        assertFalse(a == c)
    }

    @Test
    fun calendarInfo_copy() {
        val original = CalendarInfo("id1", "Cal A", "#FF0000", true)
        val copied = original.copy(name = "Cal B", isPrimary = false)

        assertEquals("id1", copied.id)
        assertEquals("Cal B", copied.name)
        assertEquals("#FF0000", copied.color)
        assertFalse(copied.isPrimary)
    }

    @Test
    fun calendarScopeRequiredException_message() {
        val exception = CalendarScopeRequiredException("Need calendar scope")
        assertEquals("Need calendar scope", exception.message)
    }

    @Test
    fun syncDirection_constants() {
        assertEquals("push", DIRECTION_PUSH)
        assertEquals("pull", DIRECTION_PULL)
        assertEquals("both", DIRECTION_BOTH)
    }

    @Test
    fun syncFrequency_constants() {
        assertEquals("realtime", FREQUENCY_REALTIME)
        assertEquals("15min", FREQUENCY_15MIN)
        assertEquals("hourly", FREQUENCY_HOURLY)
        assertEquals("manual", FREQUENCY_MANUAL)
    }
}
