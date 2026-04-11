package com.averycorp.prismtask.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic unit tests for the static helpers added to [ThemePreferences] as
 * part of the custom accent color picker. We avoid hitting the actual DataStore
 * here because the existing ThemePreferences implementation uses the
 * [android.content.Context] extension style which requires a real Context.
 */
class ThemePreferencesRecentColorsTest {

    @Test
    fun `valid 6 digit hex accepted`() {
        assertTrue(ThemePreferences.isValidHex("#FF0000"))
        assertTrue(ThemePreferences.isValidHex("#abcdef"))
        assertTrue(ThemePreferences.isValidHex("#2563EB"))
    }

    @Test
    fun `valid 8 digit hex accepted for alpha channel`() {
        assertTrue(ThemePreferences.isValidHex("#80FF0000"))
    }

    @Test
    fun `invalid hex rejected when missing hash`() {
        assertFalse(ThemePreferences.isValidHex("FF0000"))
    }

    @Test
    fun `invalid hex rejected when wrong length`() {
        assertFalse(ThemePreferences.isValidHex("#FFF"))
        assertFalse(ThemePreferences.isValidHex("#FFFFF"))
        assertFalse(ThemePreferences.isValidHex("#FFFFFFFFF"))
    }

    @Test
    fun `invalid hex rejected when non hex chars present`() {
        assertFalse(ThemePreferences.isValidHex("#GG0000"))
        assertFalse(ThemePreferences.isValidHex("#ZZZZZZ"))
    }

    @Test
    fun `addToRecentColors inserts at head`() {
        val result = ThemePreferences.addToRecentColors(listOf("#FF0000"), "#00FF00")
        assertEquals(listOf("#00FF00", "#FF0000"), result)
    }

    @Test
    fun `addToRecentColors caps at max of 5`() {
        val start = listOf("#A", "#B", "#C", "#D", "#E")
        val result = ThemePreferences.addToRecentColors(start, "#111111")
        assertEquals(5, result.size)
        assertEquals("#111111", result.first())
        // The oldest entry should have been evicted.
        assertFalse(result.contains("#E"))
    }

    @Test
    fun `addToRecentColors dedupes existing value`() {
        val start = listOf("#FF0000", "#00FF00", "#0000FF")
        val result = ThemePreferences.addToRecentColors(start, "#00FF00")
        // The repeat moves to the front and the duplicate is removed.
        assertEquals(listOf("#00FF00", "#FF0000", "#0000FF"), result)
        assertEquals(3, result.size)
    }

    @Test
    fun `addToRecentColors normalizes case`() {
        val result = ThemePreferences.addToRecentColors(emptyList(), "#ff00aa")
        assertEquals(listOf("#FF00AA"), result)
    }

    @Test
    fun `addToRecentColors with empty list`() {
        val result = ThemePreferences.addToRecentColors(emptyList(), "#123456")
        assertEquals(listOf("#123456"), result)
    }
}
