package com.averycorp.prismtask.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Calendar widget data — specifically the [WidgetCalendarEvent]
 * model and timeline merging logic.
 */
class CalendarWidgetTest {
    @Test
    fun `calendar event preserves title and time`() {
        val event = WidgetCalendarEvent(
            title = "Team Standup",
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            isAllDay = false,
            calendarColor = 0xFF1976D2.toInt()
        )
        assertEquals("Team Standup", event.title)
        assertEquals(1700000000000L, event.startTime)
        assertFalse(event.isAllDay)
    }

    @Test
    fun `all-day event flag`() {
        val event = WidgetCalendarEvent(
            title = "Holiday",
            startTime = 1700000000000L,
            endTime = 1700086400000L,
            isAllDay = true,
            calendarColor = null
        )
        assertTrue(event.isAllDay)
    }

    @Test
    fun `calendar event with null color`() {
        val event = WidgetCalendarEvent(
            title = "Meeting",
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            isAllDay = false,
            calendarColor = null
        )
        assertEquals(null, event.calendarColor)
    }

    @Test
    fun `empty calendar and empty tasks`() {
        val data = UpcomingWidgetData(emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(0, data.today.size)
        assertEquals(0, data.totalCount)
    }
}
