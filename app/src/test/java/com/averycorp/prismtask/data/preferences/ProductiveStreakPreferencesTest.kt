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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class ProductiveStreakPreferencesTest {
    private lateinit var prefs: ProductiveStreakPreferences

    @Before
    fun setUp() = runBlocking {
        prefs = ProductiveStreakPreferences(ApplicationProvider.getApplicationContext())
        prefs.clearForTest()
    }

    @Test
    fun `defaults are zero with no last date`() = runTest {
        val snapshot = prefs.observe().first()
        assertEquals(0, snapshot.currentDays)
        assertEquals(0, snapshot.longestDays)
        assertNull(snapshot.lastProductiveDate)
        assertFalse(snapshot.hasAnyHistory)
    }

    @Test
    fun `recordProductiveDay starts a streak at one`() = runTest {
        val day = LocalDate.of(2026, 4, 28)
        prefs.recordProductiveDay(day)

        val snapshot = prefs.observe().first()
        assertEquals(1, snapshot.currentDays)
        assertEquals(1, snapshot.longestDays)
        assertEquals(day, snapshot.lastProductiveDate)
        assertTrue(snapshot.hasAnyHistory)
    }

    @Test
    fun `consecutive days extend the streak and grow the longest`() = runTest {
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 28))
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 29))
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 30))

        val snapshot = prefs.observe().first()
        assertEquals(3, snapshot.currentDays)
        assertEquals(3, snapshot.longestDays)
    }

    @Test
    fun `same-day record is idempotent`() = runTest {
        val day = LocalDate.of(2026, 4, 28)
        prefs.recordProductiveDay(day)
        prefs.recordProductiveDay(day)
        prefs.recordProductiveDay(day)

        val snapshot = prefs.observe().first()
        assertEquals(1, snapshot.currentDays)
        assertEquals(1, snapshot.longestDays)
    }

    @Test
    fun `gap restarts the run but preserves longest`() = runTest {
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 25))
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 26))
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 27))
        // Skip 28 and 29 entirely.
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 30))

        val snapshot = prefs.observe().first()
        assertEquals(1, snapshot.currentDays)
        assertEquals(3, snapshot.longestDays)
    }

    @Test
    fun `resetCurrentStreakIfBroken returns broken length and zeroes current`() = runTest {
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 25))
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 26))
        prefs.recordProductiveDay(LocalDate.of(2026, 4, 27))

        val broken = prefs.resetCurrentStreakIfBroken(LocalDate.of(2026, 4, 28))

        assertEquals(3, broken)
        val snapshot = prefs.observe().first()
        assertEquals(0, snapshot.currentDays)
        // longest must be preserved — that's the whole point of forgiveness
        assertEquals(3, snapshot.longestDays)
    }

    @Test
    fun `resetCurrentStreakIfBroken on same day is a no-op`() = runTest {
        val day = LocalDate.of(2026, 4, 28)
        prefs.recordProductiveDay(day)

        val broken = prefs.resetCurrentStreakIfBroken(day)

        assertEquals(0, broken)
        val snapshot = prefs.observe().first()
        assertEquals(1, snapshot.currentDays)
    }

    @Test
    fun `resetCurrentStreakIfBroken on fresh state returns zero`() = runTest {
        val broken = prefs.resetCurrentStreakIfBroken(LocalDate.of(2026, 4, 28))
        assertEquals(0, broken)
    }
}
