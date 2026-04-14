package com.averycorp.prismtask.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-backed tests covering the shake-to-screenshot/report preferences.
 * Each test starts from a cleared DataStore so the defaults are observable.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class ShakePreferencesTest {

    private lateinit var prefs: ShakePreferences

    @Before
    fun setUp() = runBlocking {
        prefs = ShakePreferences(ApplicationProvider.getApplicationContext())
        prefs.clearAll()
    }

    @Test
    fun `default enabled is true`() = runTest {
        assertTrue(prefs.getEnabled().first())
    }

    @Test
    fun `default sensitivity is medium`() = runTest {
        assertEquals(ShakePreferences.SENSITIVITY_MEDIUM, prefs.getSensitivity().first())
    }

    @Test
    fun `setEnabled persists false`() = runTest {
        prefs.setEnabled(false)
        assertEquals(false, prefs.getEnabled().first())
    }

    @Test
    fun `setEnabled round-trips true after false`() = runTest {
        prefs.setEnabled(false)
        prefs.setEnabled(true)
        assertEquals(true, prefs.getEnabled().first())
    }

    @Test
    fun `setSensitivity low persists`() = runTest {
        prefs.setSensitivity(ShakePreferences.SENSITIVITY_LOW)
        assertEquals(ShakePreferences.SENSITIVITY_LOW, prefs.getSensitivity().first())
    }

    @Test
    fun `setSensitivity high persists`() = runTest {
        prefs.setSensitivity(ShakePreferences.SENSITIVITY_HIGH)
        assertEquals(ShakePreferences.SENSITIVITY_HIGH, prefs.getSensitivity().first())
    }

    @Test
    fun `setSensitivity rejects unknown values and falls back to default`() = runTest {
        prefs.setSensitivity("bogus")
        assertEquals(ShakePreferences.DEFAULT_SENSITIVITY, prefs.getSensitivity().first())
    }

    @Test
    fun `clearAll resets enabled and sensitivity to defaults`() = runTest {
        prefs.setEnabled(false)
        prefs.setSensitivity(ShakePreferences.SENSITIVITY_HIGH)

        prefs.clearAll()

        assertEquals(ShakePreferences.DEFAULT_ENABLED, prefs.getEnabled().first())
        assertEquals(ShakePreferences.DEFAULT_SENSITIVITY, prefs.getSensitivity().first())
    }

    @Test
    fun `ALL_SENSITIVITIES includes the three documented presets`() {
        assertTrue(ShakePreferences.SENSITIVITY_LOW in ShakePreferences.ALL_SENSITIVITIES)
        assertTrue(ShakePreferences.SENSITIVITY_MEDIUM in ShakePreferences.ALL_SENSITIVITIES)
        assertTrue(ShakePreferences.SENSITIVITY_HIGH in ShakePreferences.ALL_SENSITIVITIES)
        assertEquals(3, ShakePreferences.ALL_SENSITIVITIES.size)
    }
}
