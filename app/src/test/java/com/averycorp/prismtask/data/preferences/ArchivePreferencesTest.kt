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

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class ArchivePreferencesTest {
    private lateinit var prefs: ArchivePreferences

    @Before
    fun setUp() = runBlocking {
        prefs = ArchivePreferences(ApplicationProvider.getApplicationContext())
        prefs.clearAll()
    }

    @Test
    fun getAutoArchiveDays_defaultIsSevenDays() = runTest {
        assertEquals(7, prefs.getAutoArchiveDays().first())
    }

    @Test
    fun setAutoArchiveDays_roundTrips() = runTest {
        prefs.setAutoArchiveDays(30)
        assertEquals(30, prefs.getAutoArchiveDays().first())
    }

    @Test
    fun clearAll_resetsToDefault() = runTest {
        prefs.setAutoArchiveDays(90)
        prefs.clearAll()
        assertEquals(7, prefs.getAutoArchiveDays().first())
    }

    @Test
    fun setAutoArchiveDays_zeroIsPreserved() = runTest {
        // Zero is a valid "archive anything completed" value — the preference
        // shouldn't silently substitute in the default.
        prefs.setAutoArchiveDays(0)
        assertEquals(0, prefs.getAutoArchiveDays().first())
    }
}
