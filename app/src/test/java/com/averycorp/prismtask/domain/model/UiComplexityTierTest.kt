package com.averycorp.prismtask.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UiComplexityTier] and [isAtLeast].
 */
class UiComplexityTierTest {
    @Test
    fun `enum has three entries in correct order`() {
        val entries = UiComplexityTier.entries
        assertEquals(3, entries.size)
        assertEquals(UiComplexityTier.BASIC, entries[0])
        assertEquals(UiComplexityTier.STANDARD, entries[1])
        assertEquals(UiComplexityTier.POWER, entries[2])
    }

    @Test
    fun `displayName values are correct`() {
        assertEquals("Basic", UiComplexityTier.BASIC.displayName)
        assertEquals("Standard", UiComplexityTier.STANDARD.displayName)
        assertEquals("Power User", UiComplexityTier.POWER.displayName)
    }

    @Test
    fun `description values are correct`() {
        assertEquals("Just the essentials. Clean and simple.", UiComplexityTier.BASIC.description)
        assertEquals("Full features with smart defaults.", UiComplexityTier.STANDARD.description)
        assertEquals("Everything exposed. Full control.", UiComplexityTier.POWER.description)
    }

    @Test
    fun `fromName returns correct tier for valid names`() {
        assertEquals(UiComplexityTier.BASIC, UiComplexityTier.fromName("BASIC"))
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName("STANDARD"))
        assertEquals(UiComplexityTier.POWER, UiComplexityTier.fromName("POWER"))
    }

    @Test
    fun `fromName defaults to STANDARD for unknown or null input`() {
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName(null))
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName("NONSENSE"))
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName(""))
    }

    // ---- isAtLeast: all 9 combinations ----

    @Test
    fun `BASIC isAtLeast BASIC returns true`() {
        assertTrue(UiComplexityTier.BASIC.isAtLeast(UiComplexityTier.BASIC))
    }

    @Test
    fun `BASIC isAtLeast STANDARD returns false`() {
        assertFalse(UiComplexityTier.BASIC.isAtLeast(UiComplexityTier.STANDARD))
    }

    @Test
    fun `BASIC isAtLeast POWER returns false`() {
        assertFalse(UiComplexityTier.BASIC.isAtLeast(UiComplexityTier.POWER))
    }

    @Test
    fun `STANDARD isAtLeast BASIC returns true`() {
        assertTrue(UiComplexityTier.STANDARD.isAtLeast(UiComplexityTier.BASIC))
    }

    @Test
    fun `STANDARD isAtLeast STANDARD returns true`() {
        assertTrue(UiComplexityTier.STANDARD.isAtLeast(UiComplexityTier.STANDARD))
    }

    @Test
    fun `STANDARD isAtLeast POWER returns false`() {
        assertFalse(UiComplexityTier.STANDARD.isAtLeast(UiComplexityTier.POWER))
    }

    @Test
    fun `POWER isAtLeast BASIC returns true`() {
        assertTrue(UiComplexityTier.POWER.isAtLeast(UiComplexityTier.BASIC))
    }

    @Test
    fun `POWER isAtLeast STANDARD returns true`() {
        assertTrue(UiComplexityTier.POWER.isAtLeast(UiComplexityTier.STANDARD))
    }

    @Test
    fun `POWER isAtLeast POWER returns true`() {
        assertTrue(UiComplexityTier.POWER.isAtLeast(UiComplexityTier.POWER))
    }
}
