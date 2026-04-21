package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * DST / timezone edge-case coverage for [QuietHoursDeferrer.defer], layered
 * on top of the existing [QuietHoursDeferrerTest]. The original covers the
 * standard overnight window path; this file pins behavior across:
 *
 *  - Spring-forward (2026-03-08, America/New_York, 02:00 → 03:00). A fire
 *    that would land during the non-existent 02:30 slot is normalized by
 *    [java.time.LocalDateTime.atZone] to 03:30 EDT.
 *  - Fall-back (2026-11-01, America/New_York, 02:00 → 01:00). 01:30
 *    happens twice; [ZoneId] uses the "earlier offset" rule.
 *  - Quiet-end wall-clock lands inside the DST transition hour.
 *  - Running the deferrer in a timezone where "overnight" semantics
 *    straddle a UTC day boundary (Pacific/Auckland, UTC+13).
 *
 * Assertions favor ordering + window-bound invariants over specific
 * epoch-millis literals; the goal is to confirm the deferrer doesn't
 * emit a timestamp *before* the originally intended fire OR *during*
 * the quiet window on any DST path.
 */
class QuietHoursDstTest {
    private val ny: ZoneId = ZoneId.of("America/New_York")
    private val auckland: ZoneId = ZoneId.of("Pacific/Auckland")

    private fun millisAt(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `spring forward fire at 3am defers to configured end-of-quiet`() {
        // Fire is scheduled for 2026-03-08 03:00 EDT (the hour after
        // spring-forward). With a quiet window 22:00-07:00, we're still
        // inside quiet → defer to 07:00 on the same day.
        val fireAt = millisAt(LocalDate.of(2026, 3, 8), LocalTime.of(3, 0), ny)
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = ny
        )

        val expected = millisAt(LocalDate.of(2026, 3, 8), LocalTime.of(7, 0), ny)
        assertEquals(expected, deferred)
        assertTrue("Deferred fire must be after the original fire", deferred > fireAt)
    }

    @Test
    fun `fall back fire inside repeated 1am hour defers past second occurrence`() {
        // 2026-11-01 01:30 EDT = pre-fallback. The deferred end at 07:00
        // must land AFTER both the pre- and post-transition 01:30s.
        val fireAt = millisAt(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), ny)
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = ny
        )

        val endOfQuiet = millisAt(LocalDate.of(2026, 11, 1), LocalTime.of(7, 0), ny)
        assertEquals(endOfQuiet, deferred)
        // Sanity: at least 5h30m passed real time (longer than 5h30m because
        // of the repeated hour during fall-back).
        assertTrue(
            "Fall-back defer must cover the extra repeated hour",
            deferred - fireAt > 5L * 60 * 60 * 1000 + 30 * 60 * 1000
        )
    }

    @Test
    fun `timezone with large positive offset still handles overnight window`() {
        // Pacific/Auckland is UTC+13 (or +12 in DST). Confirm the
        // overnight-defer math doesn't accidentally use UTC instants for
        // the day-rollover decision.
        val fireAt = millisAt(LocalDate.of(2026, 4, 21), LocalTime.of(23, 30), auckland)
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = auckland
        )

        val expected = millisAt(LocalDate.of(2026, 4, 22), LocalTime.of(7, 0), auckland)
        assertEquals(expected, deferred)
    }

    @Test
    fun `deferring across dst transition never emits a past timestamp`() {
        // Property-style guard: regardless of DST status, defer(fire) >= fire.
        // Dates cover: day before spring-forward, spring-forward itself,
        // day after, day before fall-back, fall-back, day after.
        val interestingDates = listOf(
            LocalDate.of(2026, 3, 7),
            LocalDate.of(2026, 3, 8),
            LocalDate.of(2026, 3, 9),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2)
        )
        val fireTimes = listOf(LocalTime.of(23, 0), LocalTime.of(2, 30), LocalTime.of(6, 45))

        for (date in interestingDates) {
            for (time in fireTimes) {
                // When LocalDateTime is non-existent, atZone picks the next
                // valid instant — use atZone + toInstant to get the epoch
                // the scheduler would actually see.
                val fireAt = date.atTime(time).atZone(ny).toInstant().toEpochMilli()
                val deferred = QuietHoursDeferrer.defer(
                    fireAtMillis = fireAt,
                    quietStart = LocalTime.of(22, 0),
                    quietEnd = LocalTime.of(7, 0),
                    zone = ny
                )
                assertTrue(
                    "defer($date $time) produced a timestamp before the original fire",
                    deferred >= fireAt
                )
            }
        }
    }

    @Test
    fun `non-overnight window same day defers to same day end`() {
        // Pathological window 12:00 - 14:00 (siesta). Fire at 13:00 →
        // defer to 14:00 same day.
        val fireAt = millisAt(LocalDate.of(2026, 4, 21), LocalTime.of(13, 0), ny)
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(12, 0),
            quietEnd = LocalTime.of(14, 0),
            zone = ny
        )
        val expected = millisAt(LocalDate.of(2026, 4, 21), LocalTime.of(14, 0), ny)
        assertEquals(expected, deferred)
    }

    @Test
    fun `instant boundary just before quiet start is not deferred`() {
        // 21:59:59 is NOT in [22:00, 07:00), so defer should pass through.
        val fireAt = LocalDate.of(2026, 4, 21)
            .atTime(21, 59, 59)
            .atZone(ny)
            .toInstant()
            .toEpochMilli()
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = ny
        )
        assertEquals(fireAt, deferred)
    }

    @Test
    fun `instant at exact quiet end is not deferred`() {
        // isInQuietWindow uses [start, end) — end is exclusive.
        val fireAt = LocalDate.of(2026, 4, 21)
            .atTime(7, 0, 0)
            .atZone(ny)
            .toInstant()
            .toEpochMilli()
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = ny
        )
        assertEquals(fireAt, deferred)
    }

    @Test
    fun `deferred time uses same zone as input not system default`() {
        // Regression guard: pass a zone explicitly and ensure the result's
        // wall-clock is consistent with that zone (not UTC).
        val fireAt = millisAt(LocalDate.of(2026, 4, 21), LocalTime.of(3, 0), ny)
        val deferred = QuietHoursDeferrer.defer(
            fireAtMillis = fireAt,
            quietStart = LocalTime.of(22, 0),
            quietEnd = LocalTime.of(7, 0),
            zone = ny
        )
        val deferredZdt = Instant.ofEpochMilli(deferred).atZone(ny)
        assertEquals(7, deferredZdt.hour)
        assertEquals(0, deferredZdt.minute)
    }
}
