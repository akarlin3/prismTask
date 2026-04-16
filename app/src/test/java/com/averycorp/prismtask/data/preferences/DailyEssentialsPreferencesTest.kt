package com.averycorp.prismtask.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class DailyEssentialsPreferencesTest {
    private lateinit var prefs: DailyEssentialsPreferences

    @Before
    fun setUp() = runBlocking {
        prefs = DailyEssentialsPreferences(ApplicationProvider.getApplicationContext())
        prefs.setHouseworkHabit(null)
        prefs.setSchoolworkHabit(null)
    }

    @Test
    fun housework_habit_defaults_to_null() = runTest {
        assertNull(prefs.houseworkHabitId.first())
    }

    @Test
    fun schoolwork_habit_defaults_to_null() = runTest {
        assertNull(prefs.schoolworkHabitId.first())
    }

    @Test
    fun setHouseworkHabit_roundTrips() = runTest {
        prefs.setHouseworkHabit(42L)
        assertEquals(42L, prefs.houseworkHabitId.first())
    }

    @Test
    fun setSchoolworkHabit_roundTrips() = runTest {
        prefs.setSchoolworkHabit(99L)
        assertEquals(99L, prefs.schoolworkHabitId.first())
    }

    @Test
    fun setHouseworkHabit_null_clears_previous_value() = runTest {
        prefs.setHouseworkHabit(7L)
        prefs.setHouseworkHabit(null)
        assertNull(prefs.houseworkHabitId.first())
    }

    @Test
    fun setSchoolworkHabit_null_clears_previous_value() = runTest {
        prefs.setSchoolworkHabit(7L)
        prefs.setSchoolworkHabit(null)
        assertNull(prefs.schoolworkHabitId.first())
    }

    @Test
    fun hasSeenHint_defaults_to_false() = runTest {
        assertFalse(prefs.hasSeenHint.first())
    }

    @Test
    fun markHintSeen_flipsFlag() = runTest {
        prefs.markHintSeen()
        assertTrue(prefs.hasSeenHint.first())
    }
}
