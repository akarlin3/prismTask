package com.averycorp.prismtask.ui.a11y

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Smoke test for the accessibility CompositionLocals — verifies they
 * exist and can be referenced. The `.current` default values are
 * checked by the instrumentation suite since reading them requires a
 * composition.
 */
class AccessibilityTest {

    @Test
    fun `reduced motion composition local exists`() {
        assertNotNull(LocalReducedMotion)
    }

    @Test
    fun `high contrast composition local exists`() {
        assertNotNull(LocalHighContrast)
    }

    @Test
    fun `large touch targets composition local exists`() {
        assertNotNull(LocalLargeTouchTargets)
    }
}
