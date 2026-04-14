package com.averycorp.prismtask.domain

import com.averycorp.prismtask.domain.usecase.QuietHoursDeferrer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Unit tests for [QuietHoursDeferrer]. Uses a fixed UTC zone so the
 * assertions don't drift with the host's local time zone.
 */
class QuietHoursDeferrerTest {
    private val zone = ZoneId.of("UTC")

    private fun millisOf(date: String, time: String): Long =
        LocalDateTime
            .parse("${date}T$time")
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun isInQuietWindow_returnsFalseForEqualStartEnd() {
        assertFalse(
            QuietHoursDeferrer.isInQuietWindow(
                LocalTime.NOON,
                LocalTime.NOON,
                LocalTime.NOON
            )
        )
    }

    @Test
    fun isInQuietWindow_detectsSameDayWindow() {
        // 14:00-17:00 window
        assertTrue(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(15, 0), LocalTime.of(14, 0), LocalTime.of(17, 0)))
        assertFalse(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(17, 0)))
        assertFalse(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(17, 0), LocalTime.of(14, 0), LocalTime.of(17, 0)))
    }

    @Test
    fun isInQuietWindow_detectsOvernightWindow() {
        // 22:00-07:00 window — late-evening AND early-morning inside.
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        assertTrue(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(23, 30), start, end))
        assertTrue(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(1, 0), start, end))
        assertTrue(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(6, 59), start, end))
        assertFalse(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(7, 0), start, end))
        assertFalse(QuietHoursDeferrer.isInQuietWindow(LocalTime.of(15, 0), start, end))
    }

    @Test
    fun defer_returnsOriginalWhenOutsideWindow() {
        val fireAt = millisOf("2026-04-11", "15:00:00")
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = zone
        )
        assertEquals(fireAt, deferred)
    }

    @Test
    fun defer_lateEveningMovesToNextMorning() {
        // Fire at 23:00 on 2026-04-11, quiet 22:00-07:00 → defer to 07:00 on 2026-04-12
        val fireAt = millisOf("2026-04-11", "23:00:00")
        val expected = millisOf("2026-04-12", "07:00:00")
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = zone
        )
        assertEquals(expected, deferred)
    }

    @Test
    fun defer_earlyMorningMovesToSameDayEnd() {
        // Fire at 03:00 on 2026-04-11, quiet 22:00-07:00 → defer to 07:00 on 2026-04-11
        val fireAt = millisOf("2026-04-11", "03:00:00")
        val expected = millisOf("2026-04-11", "07:00:00")
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = zone
        )
        assertEquals(expected, deferred)
    }
}
