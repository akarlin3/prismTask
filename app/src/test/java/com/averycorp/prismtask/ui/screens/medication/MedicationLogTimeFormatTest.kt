package com.averycorp.prismtask.ui.screens.medication

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

/**
 * Locks in the medication log row's date-aware time label.
 *
 * The day card header groups doses by `taken_date_local` (computed at log
 * time using the user's Start-of-Day). When the dose's actual wall-clock
 * calendar date differs from that grouped date — typical after a late-night
 * dose with SoD = 04:00 — the row prefixes the calendar date so the gap
 * stays legible.
 */
class MedicationLogTimeFormatTest {

    private val defaultLocale = Locale.getDefault()
    private val defaultTz = TimeZone.getDefault()
    private val zone = ZoneId.of("America/New_York")

    @Before
    fun pinLocale() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTz)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun timeOnly_whenDoseCalendarDateMatchesGroupedDate() {
        // Dose at 09:00 on Apr 28; grouped under Apr 28 — no date prefix.
        val ts = millis(2026, 4, 28, 9, 0)
        val label = formatTimeWithDateIfDifferent(ts, LocalDate.of(2026, 4, 28))
        assertEquals("9:00 AM", label)
    }

    @Test
    fun prefixesDate_whenDoseFallsAfterMidnightButGroupedToPriorDay() {
        // SoD = 04:00; dose at 02:00 on Apr 29 lands on Apr 28's card.
        // The row should surface the actual Apr 29 date alongside 2:00 AM.
        val ts = millis(2026, 4, 29, 2, 0)
        val label = formatTimeWithDateIfDifferent(ts, LocalDate.of(2026, 4, 28))
        assertEquals("Apr 29 · 2:00 AM", label)
    }

    @Test
    fun prefixesDate_whenDoseLandsOnPriorCalendarDayDueToShiftedSod() {
        // Symmetric edge: a late SoD (e.g. 18:00) groups an evening dose
        // under the next day's card. Surface the dose's actual prior date.
        val ts = millis(2026, 4, 28, 23, 30)
        val label = formatTimeWithDateIfDifferent(ts, LocalDate.of(2026, 4, 29))
        assertEquals("Apr 28 · 11:30 PM", label)
    }

    @Test
    fun includesYear_whenDoseAndGroupedDateAreInDifferentYears() {
        // Dec 31 23:30 with a Jan 1 grouped date — different year, so the
        // label includes "yyyy" to keep the gap unambiguous.
        val ts = millis(2025, 12, 31, 23, 30)
        val label = formatTimeWithDateIfDifferent(ts, LocalDate.of(2026, 1, 1))
        assertEquals("Dec 31, 2025 · 11:30 PM", label)
    }

    @Test
    fun timeOnly_whenGroupedDateIsNull() {
        // Defensive: log day with an unparseable date string falls back to
        // raw time-only formatting rather than dropping the row.
        val ts = millis(2026, 4, 28, 9, 0)
        val label = formatTimeWithDateIfDifferent(ts, null)
        assertEquals("9:00 AM", label)
    }
}
