package com.averycorp.prismtask.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DayBoundaryTest {

    private val utc = ZoneId.of("UTC")
    private val ny = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    // ---- logicalDate ----

    @Test
    fun `SoD 0000 means logical equals calendar`() {
        val noon = at(utc, 2026, 4, 19, 12)
        val midnight = at(utc, 2026, 4, 19, 0)
        assertEquals(LocalDate.of(2026, 4, 19), DayBoundary.logicalDate(noon, 0, 0, utc))
        assertEquals(LocalDate.of(2026, 4, 19), DayBoundary.logicalDate(midnight, 0, 0, utc))
    }

    @Test
    fun `SoD 0400 at 0359 yields previous calendar date`() {
        val t = at(utc, 2026, 4, 19, 3, 59)
        assertEquals(LocalDate.of(2026, 4, 18), DayBoundary.logicalDate(t, 4, 0, utc))
    }

    @Test
    fun `SoD 0400 at exactly 0400 yields today`() {
        val t = at(utc, 2026, 4, 19, 4, 0)
        assertEquals(LocalDate.of(2026, 4, 19), DayBoundary.logicalDate(t, 4, 0, utc))
    }

    @Test
    fun `SoD 0400 at 0401 yields today`() {
        val t = at(utc, 2026, 4, 19, 4, 1)
        assertEquals(LocalDate.of(2026, 4, 19), DayBoundary.logicalDate(t, 4, 0, utc))
    }

    @Test
    fun `SoD 0430 honors minutes`() {
        val before = at(utc, 2026, 4, 19, 4, 29)
        val exactly = at(utc, 2026, 4, 19, 4, 30)
        assertEquals(LocalDate.of(2026, 4, 18), DayBoundary.logicalDate(before, 4, 30, utc))
        assertEquals(LocalDate.of(2026, 4, 19), DayBoundary.logicalDate(exactly, 4, 30, utc))
    }

    // ---- logicalDayStart / nextLogicalDayStart ----

    @Test
    fun `logicalDayStart for SoD 0400 at 0200 is yesterday 0400`() {
        val t = at(utc, 2026, 4, 19, 2)
        val start = DayBoundary.logicalDayStart(t, 4, 0, utc)
        assertEquals(at(utc, 2026, 4, 18, 4), start)
    }

    @Test
    fun `nextLogicalDayStart is exactly 24h after logicalDayStart (UTC)`() {
        val t = at(utc, 2026, 4, 19, 10)
        val start = DayBoundary.logicalDayStart(t, 4, 0, utc)
        val next = DayBoundary.nextLogicalDayStart(t, 4, 0, utc)
        assertEquals(24 * 60 * 60L, next.epochSecond - start.epochSecond)
    }

    // ---- calendarDate unchanged ----

    @Test
    fun `calendarDate ignores SoD`() {
        val t = at(utc, 2026, 4, 19, 2)
        assertEquals(LocalDate.of(2026, 4, 19), DayBoundary.calendarDate(t, utc))
    }

    // ---- resolveAmbiguousTime ----

    @Test
    fun `resolveAmbiguousTime at 2AM with SoD 4AM resolves to next 2AM before SoD`() {
        // Now = Nov 15 01:00 UTC, SoD = 4AM, target = 2AM
        // Logical today = Nov 14 (starts Nov 14 04:00 UTC)
        // So "2 AM" should resolve to Nov 15 02:00 (inside the logical Nov 14 window).
        val now = at(utc, 2025, 11, 15, 1)
        val resolved = DayBoundary.resolveAmbiguousTime(now, 2, 0, 4, 0, utc)
        assertEquals(at(utc, 2025, 11, 15, 2), resolved)
    }

    @Test
    fun `resolveAmbiguousTime at 2AM with SoD 4AM when after SoD pushes to next logical day`() {
        // Now = Nov 15 10:00 UTC, SoD = 4AM, target = 2AM
        // Logical today = Nov 15 (Nov 15 04:00 .. Nov 16 04:00)
        // So "2 AM" = Nov 16 02:00 (still inside logical Nov 15).
        val now = at(utc, 2025, 11, 15, 10)
        val resolved = DayBoundary.resolveAmbiguousTime(now, 2, 0, 4, 0, utc)
        assertEquals(at(utc, 2025, 11, 16, 2), resolved)
    }

    @Test
    fun `resolveAmbiguousTime when target already passed today rolls forward`() {
        // Now = Nov 15 15:00 UTC, SoD = 0, target = 10AM (already passed today)
        // With SoD=0 logical == calendar, so next 10AM is tomorrow 10:00.
        val now = at(utc, 2025, 11, 15, 15)
        val resolved = DayBoundary.resolveAmbiguousTime(now, 10, 0, 0, 0, utc)
        assertEquals(at(utc, 2025, 11, 16, 10), resolved)
    }

    @Test
    fun `resolveAmbiguousTime with SoD 0 and target in future today returns today`() {
        val now = at(utc, 2025, 11, 15, 8)
        val resolved = DayBoundary.resolveAmbiguousTime(now, 17, 30, 0, 0, utc)
        assertEquals(at(utc, 2025, 11, 15, 17, 30), resolved)
    }

    // ---- Explicit timezone ----

    @Test
    fun `logicalDate respects explicit timezone`() {
        // 2026-03-08 07:00 UTC = 2026-03-08 02:00 NY (before spring-forward at 2AM)
        // Actually spring forward 2026 is March 8, 02:00 -> 03:00 in US zones, so we pick
        // a clearly non-ambiguous time: 2026-04-19 06:00 UTC = 2026-04-19 02:00 EDT.
        val t = LocalDateTime.of(2026, 4, 19, 2, 0).atZone(ny).toInstant()
        assertEquals(LocalDate.of(2026, 4, 18), DayBoundary.logicalDate(t, 4, 0, ny))
        assertEquals(LocalDate.of(2026, 4, 19), DayBoundary.logicalDate(t, 4, 0, ZoneOffset.UTC))
    }

    // ---- DST ----

    @Test
    fun `DST spring-forward at non-SoD hour still produces a valid logical date`() {
        // US spring-forward 2026: Sun Mar 8, local clock jumps 02:00 -> 03:00 in America/New_York.
        // SoD = 4 AM. At NY local 03:30 (= 1 hour after jump), we are still before SoD
        // so logical date = Mar 7.
        val t = LocalDateTime.of(2026, 3, 8, 3, 30).atZone(ny).toInstant()
        assertEquals(LocalDate.of(2026, 3, 7), DayBoundary.logicalDate(t, 4, 0, ny))
    }

    @Test
    fun `DST fall-back at SoD hour still produces a valid logical date`() {
        // US fall-back 2025: Sun Nov 2, local clock 02:00 -> 01:00 in America/New_York.
        // SoD = 4 AM at 2025-11-02 05:00 NY (well past SoD and past the repeated hour).
        val t = LocalDateTime.of(2025, 11, 2, 5, 0).atZone(ny).toInstant()
        assertEquals(LocalDate.of(2025, 11, 2), DayBoundary.logicalDate(t, 4, 0, ny))
    }

    // ---- Invariants ----

    @Test
    fun `logicalDayStart is always less than or equal to instant`() {
        val t = at(utc, 2026, 4, 19, 5)
        val start = DayBoundary.logicalDayStart(t, 4, 0, utc)
        assertTrue(!start.isAfter(t))
    }

    @Test
    fun `nextLogicalDayStart is always after instant`() {
        val t = at(utc, 2026, 4, 19, 5)
        val next = DayBoundary.nextLogicalDayStart(t, 4, 0, utc)
        assertTrue(next.isAfter(t))
    }
}
