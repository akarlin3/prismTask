package com.averycorp.prismtask.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end persistence tests for [ThemePreferences] via Robolectric so we
 * can exercise the real [Context]-backed DataStore without needing an
 * instrumentation device. Each test creates a fresh ThemePreferences against
 * the Robolectric Application context; DataStore writes land in a scratch dir
 * that the Robolectric runner reclaims between tests.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class ThemePreferencesTest {
    private lateinit var prefs: ThemePreferences

    @Before
    fun setUp() = runBlocking {
        prefs = ThemePreferences(ApplicationProvider.getApplicationContext())
        prefs.clearAll()
    }

    @Test
    fun getThemeMode_defaultIsSystem() = runTest {
        assertEquals("system", prefs.getThemeMode().first())
    }

    @Test
    fun setThemeMode_darkPersists() = runTest {
        prefs.setThemeMode("dark")
        assertEquals("dark", prefs.getThemeMode().first())
    }

    @Test
    fun setThemeMode_lightPersists() = runTest {
        prefs.setThemeMode("light")
        assertEquals("light", prefs.getThemeMode().first())
    }

    @Test
    fun setAccentColor_roundTrips() = runTest {
        prefs.setAccentColor("#2563EB")
        assertEquals("#2563EB", prefs.getAccentColor().first())
    }

    @Test
    fun setFontScale_roundTrips() = runTest {
        prefs.setFontScale(1.25f)
        assertEquals(1.25f, prefs.getFontScale().first(), 0.0001f)
    }

    @Test
    fun setPriorityColor_storesPerLevel() = runTest {
        prefs.setPriorityColor(0, "#000000")
        prefs.setPriorityColor(4, "#FF0000")
        assertEquals("#000000", prefs.getPriorityColorNone().first())
        assertEquals("#FF0000", prefs.getPriorityColorUrgent().first())
    }

    @Test
    fun addRecentCustomColor_validHexIsAppendedAndDeduped() = runTest {
        prefs.addRecentCustomColor("#FF00AA")
        prefs.addRecentCustomColor("#112233")
        prefs.addRecentCustomColor("#FF00AA") // duplicate; should move to head
        val colors = prefs.getRecentCustomColors().first()
        assertEquals(2, colors.size)
        assertEquals("#FF00AA", colors.first())
    }

    @Test
    fun addRecentCustomColor_invalidHexIsIgnored() = runTest {
        prefs.addRecentCustomColor("not a color")
        assertEquals(emptyList<String>(), prefs.getRecentCustomColors().first())
    }
}
