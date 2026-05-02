package com.averycorp.prismtask.ui.screens.medication.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Pins [formatPickedTime] — the source-of-truth readout shown beneath the
 * Material 3 `TimePicker` in `MedicationTimeEditSheet` and
 * `LogCustomDoseSheet`. Per
 * `docs/audits/MED_TIME_PICKER_DISPLAY_DRIFT_AUDIT.md` § I1, this label
 * is the user's contract that what they see is what gets saved — so any
 * drift in the formatter would re-open the user-trust gap the picker
 * fix was meant to close.
 */
class MedicationTimePickerLabelTest {

    private val locale = Locale.US

    @Test
    fun formats12HourMorning() {
        assertEquals("8:30 AM", formatPickedTime(8, 30, is24Hour = false, locale = locale))
    }

    @Test
    fun formats12HourAfternoon() {
        // The case the audit calls out as the user's most likely confusion
        // point: `state.hour = 14` (PM) without an unambiguous label.
        assertEquals("2:30 PM", formatPickedTime(14, 30, is24Hour = false, locale = locale))
    }

    @Test
    fun formats12HourMidnightAsTwelve() {
        // Defensive: hour = 0 must read as "12 AM", not "0 AM".
        assertEquals("12:00 AM", formatPickedTime(0, 0, is24Hour = false, locale = locale))
    }

    @Test
    fun formats12HourNoonAsTwelve() {
        // Defensive: hour = 12 must read as "12 PM", not "0 PM" or "12 AM".
        assertEquals("12:00 PM", formatPickedTime(12, 0, is24Hour = false, locale = locale))
    }

    @Test
    fun formats24HourAfternoon() {
        assertEquals("14:30", formatPickedTime(14, 30, is24Hour = true, locale = locale))
    }

    @Test
    fun formats24HourSingleDigitHourAndMinuteWithLeadingZeros() {
        // Defensive: HH:mm must zero-pad — "08:05", not "8:5".
        assertEquals("08:05", formatPickedTime(8, 5, is24Hour = true, locale = locale))
    }

    @Test
    fun formats24HourMidnight() {
        assertEquals("00:00", formatPickedTime(0, 0, is24Hour = true, locale = locale))
    }
}
