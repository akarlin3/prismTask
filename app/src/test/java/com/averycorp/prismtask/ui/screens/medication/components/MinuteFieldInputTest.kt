package com.averycorp.prismtask.ui.screens.medication.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression gate for [applyMinuteFieldEdit] — the helper that drives the
 * medication custom interval and drift TextFields.
 *
 * The pre-fix implementation called `mins.coerceIn(min, max)` and wrote the
 * coerced value back to a state field that was the key for the TextField's
 * `remember(...)` block. Typing any digit below the minimum (e.g., "1" while
 * typing toward "120") silently rewrote the field to the floor value. See
 * `docs/audits/MEDICATION_CUSTOM_INTERVAL_INPUT_AUDIT.md` for the full
 * trace; the cases below pin the post-fix contract.
 */
class MinuteFieldInputTest {

    @Test
    fun intermediateDigitBelowMinimum_isHeld_notCoercedUp() {
        // The headline regression: user has cleared the field and types "1"
        // intending "120". The helper must echo "1" and refuse to commit
        // — committing a coerced 60 would re-key the parent's remember and
        // overwrite the user's keystroke on the next recomposition.
        val update = applyMinuteFieldEdit(raw = "1", minMinutes = 60, maxMinutes = 1440)
        assertEquals("1", update.text)
        assertNull(update.newMinutes)
        assertTrue(update.outOfRange)
    }

    @Test
    fun intermediateTwoDigit_belowMinimum_isHeld() {
        // After "1", the user types "2" → field shows "12", still below 60.
        val update = applyMinuteFieldEdit(raw = "12", minMinutes = 60, maxMinutes = 1440)
        assertEquals("12", update.text)
        assertNull(update.newMinutes)
        assertTrue(update.outOfRange)
    }

    @Test
    fun thirdDigit_inRange_commits() {
        // After "12", the user types "0" → "120" parses inside [60, 1440],
        // so we commit the value upstream.
        val update = applyMinuteFieldEdit(raw = "120", minMinutes = 60, maxMinutes = 1440)
        assertEquals("120", update.text)
        assertEquals(120, update.newMinutes)
        assertFalse(update.outOfRange)
    }

    @Test
    fun valueAboveMaximum_isHeld_notCoercedDown() {
        // Typing "9999" must NOT silently clamp to 1440 — the same round-trip
        // that broke sub-minimum input would also break this.
        val update = applyMinuteFieldEdit(raw = "9999", minMinutes = 60, maxMinutes = 1440)
        assertEquals("9999", update.text)
        assertNull(update.newMinutes)
        assertTrue(update.outOfRange)
    }

    @Test
    fun emptyInput_doesNotCommit() {
        // Backspacing the field clear must leave the field empty without
        // committing anything (and without flagging an out-of-range error;
        // empty is "still typing", not "wrong").
        val update = applyMinuteFieldEdit(raw = "", minMinutes = 60, maxMinutes = 1440)
        assertEquals("", update.text)
        assertNull(update.newMinutes)
        assertFalse(update.outOfRange)
    }

    @Test
    fun nonDigitCharacters_areStripped() {
        // The TextField doesn't restrict the keyboard, so we must drop
        // non-digits ourselves — otherwise paste flows ("120m") would land
        // garbage in `text`.
        val update = applyMinuteFieldEdit(raw = "1a2b0", minMinutes = 60, maxMinutes = 1440)
        assertEquals("120", update.text)
        assertEquals(120, update.newMinutes)
        assertFalse(update.outOfRange)
    }

    @Test
    fun overlongInput_isCappedAtFourDigits() {
        // Defensive cap matches the pre-fix `.take(4)` so paste of a
        // ten-digit value can't overflow Int and crash `toIntOrNull()`.
        val update = applyMinuteFieldEdit(raw = "1234567890", minMinutes = 60, maxMinutes = 1440)
        assertEquals("1234", update.text)
        assertEquals(1234, update.newMinutes)
        assertFalse(update.outOfRange)
    }

    @Test
    fun driftPicker_lowerBoundOne_acceptsSingleDigit() {
        // The drift field has min=1, so "5" is in range and commits — this
        // pins the difference from the interval picker (min=60).
        val update = applyMinuteFieldEdit(raw = "5", minMinutes = 1, maxMinutes = 1440)
        assertEquals("5", update.text)
        assertEquals(5, update.newMinutes)
        assertFalse(update.outOfRange)
    }

    @Test
    fun driftPicker_zero_isOutOfRange() {
        // Drift's lower bound is 1; "0" must be rejected (held in text, not
        // committed) — same protection against silent coercion to 1.
        val update = applyMinuteFieldEdit(raw = "0", minMinutes = 1, maxMinutes = 1440)
        assertEquals("0", update.text)
        assertNull(update.newMinutes)
        assertTrue(update.outOfRange)
    }

    @Test
    fun exactBoundsCommit() {
        // Floor and ceiling values themselves are valid commits.
        val low = applyMinuteFieldEdit(raw = "60", minMinutes = 60, maxMinutes = 1440)
        assertEquals(60, low.newMinutes)
        assertFalse(low.outOfRange)

        val high = applyMinuteFieldEdit(raw = "1440", minMinutes = 60, maxMinutes = 1440)
        assertEquals(1440, high.newMinutes)
        assertFalse(high.outOfRange)
    }
}
