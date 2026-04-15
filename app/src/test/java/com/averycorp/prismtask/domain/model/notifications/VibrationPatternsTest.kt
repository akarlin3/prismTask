package com.averycorp.prismtask.domain.model.notifications

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPatternsTest {
    @Test
    fun `pattern for NONE is null`() {
        assertNull(VibrationPatterns.patternFor(VibrationPreset.NONE))
    }

    @Test
    fun `pattern for CUSTOM is null (caller provides its own)`() {
        assertNull(VibrationPatterns.patternFor(VibrationPreset.CUSTOM))
    }

    @Test
    fun `each built-in preset returns a non-empty pattern starting with zero`() {
        VibrationPreset.values()
            .filterNot { it == VibrationPreset.NONE || it == VibrationPreset.CUSTOM }
            .forEach { preset ->
                val p = VibrationPatterns.patternFor(preset)
                assertNotNull("pattern for $preset", p)
                assertTrue("non-empty pattern for $preset", p!!.isNotEmpty())
                assertEquals("initial delay is 0 for $preset", 0L, p[0])
            }
    }

    @Test
    fun `repeat with count 1 returns a copy of the base pattern`() {
        val base = longArrayOf(0L, 200L, 100L, 200L)
        val out = VibrationPatterns.repeat(base, 1)
        assertArrayEquals(base, out)
    }

    @Test
    fun `repeat with count N produces N copies of the on-off pairs`() {
        val base = longArrayOf(0L, 100L, 50L)
        val out = VibrationPatterns.repeat(base, 3)
        // [0, 100, 50, 100, 50, 100, 50]
        assertArrayEquals(longArrayOf(0L, 100L, 50L, 100L, 50L, 100L, 50L), out)
    }

    @Test
    fun `repeat clamps count to 10`() {
        val base = longArrayOf(0L, 50L)
        val out = VibrationPatterns.repeat(base, 50)
        // 10 copies of the single 50ms pulse
        assertEquals(11, out.size)
    }

    @Test
    fun `encode and decode CSV round-trips`() {
        val pattern = longArrayOf(0L, 500L, 100L, 500L)
        val csv = VibrationPatterns.encodeCsv(pattern)
        val decoded = VibrationPatterns.decodeCsv(csv)
        assertArrayEquals(pattern, decoded)
    }

    @Test
    fun `decode rejects non-numeric tokens silently`() {
        val decoded = VibrationPatterns.decodeCsv("0,hello,,200,world,50")
        assertArrayEquals(longArrayOf(0L, 200L, 50L), decoded)
    }

    @Test
    fun `decode blank returns empty`() {
        assertArrayEquals(longArrayOf(), VibrationPatterns.decodeCsv(""))
        assertArrayEquals(longArrayOf(), VibrationPatterns.decodeCsv(null))
    }

    @Test
    fun `totalDurationMs sums all segments`() {
        val pattern = longArrayOf(0L, 300L, 200L, 500L)
        assertEquals(1000L, VibrationPatterns.totalDurationMs(pattern))
    }

    @Test
    fun `totalDurationMs of empty is zero`() {
        assertEquals(0L, VibrationPatterns.totalDurationMs(longArrayOf()))
    }
}
