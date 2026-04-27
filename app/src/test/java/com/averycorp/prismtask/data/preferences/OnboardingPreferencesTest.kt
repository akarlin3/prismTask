package com.averycorp.prismtask.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-backed tests for [OnboardingPreferences], with a focus on the
 * cross-platform canonical-flag hand-off added by the parity bundle so that
 * completing onboarding on web (writes `users/{uid}.onboardingCompletedAt`)
 * satisfies Android's onboarding gate.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class OnboardingPreferencesTest {
    private lateinit var prefs: OnboardingPreferences

    @Before
    fun setUp() = runBlocking {
        prefs = OnboardingPreferences(ApplicationProvider.getApplicationContext())
        prefs.resetOnboarding()
    }

    @Test
    fun `default has not completed onboarding`() = runTest {
        assertFalse(prefs.hasCompletedOnboarding().first())
        assertEquals(0L, prefs.getOnboardingCompletedAt().first())
    }

    @Test
    fun `setOnboardingCompleted with explicit timestamp round-trips`() = runTest {
        prefs.setOnboardingCompleted(timestampMs = 1_700_000_000_000L)
        assertTrue(prefs.hasCompletedOnboarding().first())
        assertEquals(1_700_000_000_000L, prefs.getOnboardingCompletedAt().first())
    }

    // --- hydrateFromCanonicalCloud --------------------------------------

    @Test
    fun `hydrate flips local mirror when canonical timestamp is set and local is empty`() = runTest {
        assertFalse(prefs.hasCompletedOnboarding().first())

        val updated = prefs.hydrateFromCanonicalCloud(canonicalCompletedAt = 1_725_000_000_000L)

        assertTrue("hydrate should report it updated the local mirror", updated)
        assertTrue(prefs.hasCompletedOnboarding().first())
        // The canonical timestamp must be preserved verbatim — we don't want
        // a freshly hydrated install to look like it just finished onboarding
        // (analytics, exporter round-trip).
        assertEquals(1_725_000_000_000L, prefs.getOnboardingCompletedAt().first())
    }

    @Test
    fun `hydrate is no-op when local already says completed`() = runTest {
        prefs.setOnboardingCompleted(timestampMs = 1_700_000_000_000L)

        val updated = prefs.hydrateFromCanonicalCloud(canonicalCompletedAt = 1_725_000_000_000L)

        assertFalse("hydrate must not overwrite a fresher local completion", updated)
        // Local timestamp untouched — DataStore stays the device-local fast path.
        assertEquals(1_700_000_000_000L, prefs.getOnboardingCompletedAt().first())
    }

    @Test
    fun `hydrate is no-op when canonical timestamp is null`() = runTest {
        val updated = prefs.hydrateFromCanonicalCloud(canonicalCompletedAt = null)

        assertFalse(updated)
        assertFalse(prefs.hasCompletedOnboarding().first())
    }

    @Test
    fun `hydrate is no-op when canonical timestamp is zero`() = runTest {
        // Zero is the DataStore "missing field" sentinel — it must not be
        // mistaken for a real completion or every signed-in user with no
        // user doc would suddenly look like they finished onboarding.
        val updated = prefs.hydrateFromCanonicalCloud(canonicalCompletedAt = 0L)

        assertFalse(updated)
        assertFalse(prefs.hasCompletedOnboarding().first())
    }

    @Test
    fun `hydrate is no-op when canonical timestamp is negative`() = runTest {
        val updated = prefs.hydrateFromCanonicalCloud(canonicalCompletedAt = -1L)

        assertFalse(updated)
        assertFalse(prefs.hasCompletedOnboarding().first())
    }

    @Test
    fun `hydrate is idempotent on repeated calls`() = runTest {
        val first = prefs.hydrateFromCanonicalCloud(1_725_000_000_000L)
        val second = prefs.hydrateFromCanonicalCloud(1_725_000_000_000L)
        val third = prefs.hydrateFromCanonicalCloud(1_726_000_000_000L)

        assertTrue("first hydrate writes", first)
        assertFalse("second hydrate is no-op", second)
        assertFalse("third hydrate with newer canonical is still no-op (local wins)", third)
        assertEquals(1_725_000_000_000L, prefs.getOnboardingCompletedAt().first())
    }

    @Test
    fun `setOnboardingCompleted no-arg uses current time and flips flag`() = runTest {
        val before = System.currentTimeMillis()
        prefs.setOnboardingCompleted()
        val after = System.currentTimeMillis()

        assertTrue(prefs.hasCompletedOnboarding().first())
        val stamped = prefs.getOnboardingCompletedAt().first()
        assertTrue(stamped in before..after)
    }
}
