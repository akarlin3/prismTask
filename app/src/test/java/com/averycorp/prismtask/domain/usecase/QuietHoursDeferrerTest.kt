package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class QuietHoursDeferrerTest {
    private val zone = ZoneId.of("America/Los_Angeles")

    private fun millisAt(date: LocalDate, time: LocalTime): Long =
        date
            .atTime(time)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `is in quiet window simple same day range`() {
        assertTrue(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(12, 0), LocalTime.of(10, 0), LocalTime.of(14, 0)))
        assertFalse(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(14, 0)))
    }

    @Test
    fun `is in quiet window overnight range`() {
        // 22:00 - 07:00: 23:00 is inside, 08:00 is outside.
        assertTrue(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(23, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        assertTrue(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(3, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        assertFalse(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(8, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)))
    }

    @Test
    fun `fire outside quiet window returns unchanged`() {
        val date = LocalDate.of(2026, 4, 11)
        val fireAt = millisAt(date, LocalTime.of(12, 0))
        val result = QuietHoursDeferrer.defer(
            fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = zone
        )
        assertEquals(fireAt, result)
    }

    @Test
    fun `fire inside early morning quiet deferred to end same day`() {
        val date = LocalDate.of(2026, 4, 11)
        val fireAt = millisAt(date, LocalTime.of(3, 0))
        val result = QuietHoursDeferrer.defer(
            fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = zone
        )
        assertEquals(millisAt(date, LocalTime.of(7, 0)), result)
    }

    @Test
    fun `fire inside late evening quiet deferred to end next day`() {
        val date = LocalDate.of(2026, 4, 11)
        val fireAt = millisAt(date, LocalTime.of(23, 0))
        val result = QuietHoursDeferrer.defer(
            fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = zone
        )
        assertEquals(millisAt(date.plusDays(1), LocalTime.of(7, 0)), result)
    }

    @Test
    fun `start equals end means no quiet window`() {
        val t = LocalTime.of(12, 0)
        assertFalse(QuietHoursDeferrer.isInQuietWindow(t, t, t))
    }
}
