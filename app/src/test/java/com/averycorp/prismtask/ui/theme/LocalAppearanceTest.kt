package com.averycorp.prismtask.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the helper logic around the appearance CompositionLocals.
 * The CompositionLocal `current` properties themselves require a Compose runtime,
 * so we test the pure Kotlin helper (`compactOr`) which is the only behavior worth
 * asserting without Robolectric.
 */
class LocalAppearanceTest {
    @Test
    fun `compactOr returns compact value when compact true`() {
        assertEquals(8.dp, 16.dp.compactOr(compact = true, compactDp = 8.dp))
    }

    @Test
    fun `compactOr returns original value when compact false`() {
        assertEquals(16.dp, 16.dp.compactOr(compact = false, compactDp = 8.dp))
    }

    @Test
    fun `compactOr zero dp compact still returns compact value`() {
        assertEquals(0.dp, 16.dp.compactOr(compact = true, compactDp = 0.dp))
    }

    @Test
    fun `compactAware is identity`() {
        assertEquals(16.dp, 16.dp.compactAware)
        assertEquals(0.dp, 0.dp.compactAware)
    }

    @Test
    fun `compactOr handles negative compactDp by passing through`() {
        // We don't clamp in the helper; behavior is pass-through.
        assertEquals((-4).dp, 16.dp.compactOr(compact = true, compactDp = (-4).dp))
    }
}
