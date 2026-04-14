package com.averycorp.prismtask.data.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CalendarSyncPreferencesTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: CalendarSyncPreferences

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmpFolder.root, "gcal_sync_test.preferences_pb") }
        )
        prefs = CalendarSyncPreferences(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaults_returnExpectedValues() = runTest {
        assertFalse(prefs.isCalendarSyncEnabled().first())
        assertEquals("primary", prefs.getSyncCalendarId().first())
        assertEquals(DIRECTION_BOTH, prefs.getSyncDirection().first())
        assertTrue(prefs.getShowCalendarEvents().first())
        assertEquals(emptySet<String>(), prefs.getSelectedDisplayCalendarIds().first())
        assertEquals(FREQUENCY_15MIN, prefs.getSyncFrequency().first())
        assertEquals(0L, prefs.getLastSyncTimestamp().first())
        assertFalse(prefs.getSyncCompletedTasks().first())
    }

    @Test
    fun setAndGet_calendarSyncEnabled() = runTest {
        prefs.setCalendarSyncEnabled(true)
        assertTrue(prefs.isCalendarSyncEnabled().first())
        assertTrue(prefs.getCalendarSyncEnabled())

        prefs.setCalendarSyncEnabled(false)
        assertFalse(prefs.isCalendarSyncEnabled().first())
    }

    @Test
    fun setAndGet_syncCalendarId() = runTest {
        prefs.setSyncCalendarId("user@gmail.com")
        assertEquals("user@gmail.com", prefs.getSyncCalendarId().first())
        assertEquals("user@gmail.com", prefs.getSyncCalendarIdOnce())
    }

    @Test
    fun setAndGet_syncDirection() = runTest {
        prefs.setSyncDirection(DIRECTION_PUSH)
        assertEquals(DIRECTION_PUSH, prefs.getSyncDirection().first())

        prefs.setSyncDirection(DIRECTION_PULL)
        assertEquals(DIRECTION_PULL, prefs.getSyncDirection().first())

        prefs.setSyncDirection(DIRECTION_BOTH)
        assertEquals(DIRECTION_BOTH, prefs.getSyncDirectionOnce())
    }

    @Test
    fun setAndGet_showCalendarEvents() = runTest {
        prefs.setShowCalendarEvents(false)
        assertFalse(prefs.getShowCalendarEvents().first())

        prefs.setShowCalendarEvents(true)
        assertTrue(prefs.getShowCalendarEvents().first())
    }

    @Test
    fun setAndGet_selectedDisplayCalendarIds() = runTest {
        val ids = setOf("cal1", "cal2", "cal3")
        prefs.setSelectedDisplayCalendarIds(ids)
        assertEquals(ids, prefs.getSelectedDisplayCalendarIds().first())

        prefs.setSelectedDisplayCalendarIds(emptySet())
        assertEquals(emptySet<String>(), prefs.getSelectedDisplayCalendarIds().first())
    }

    @Test
    fun setAndGet_syncFrequency() = runTest {
        prefs.setSyncFrequency(FREQUENCY_REALTIME)
        assertEquals(FREQUENCY_REALTIME, prefs.getSyncFrequency().first())

        prefs.setSyncFrequency(FREQUENCY_HOURLY)
        assertEquals(FREQUENCY_HOURLY, prefs.getSyncFrequency().first())

        prefs.setSyncFrequency(FREQUENCY_MANUAL)
        assertEquals(FREQUENCY_MANUAL, prefs.getSyncFrequency().first())
    }

    @Test
    fun setAndGet_lastSyncTimestamp() = runTest {
        val timestamp = 1712345678000L
        prefs.setLastSyncTimestamp(timestamp)
        assertEquals(timestamp, prefs.getLastSyncTimestamp().first())
    }

    @Test
    fun setAndGet_syncCompletedTasks() = runTest {
        prefs.setSyncCompletedTasks(true)
        assertTrue(prefs.getSyncCompletedTasks().first())

        prefs.setSyncCompletedTasks(false)
        assertFalse(prefs.getSyncCompletedTasks().first())
    }

    @Test
    fun clearAll_resetsToDefaults() = runTest {
        // Set everything to non-default values
        prefs.setCalendarSyncEnabled(true)
        prefs.setSyncCalendarId("custom-cal")
        prefs.setSyncDirection(DIRECTION_PUSH)
        prefs.setShowCalendarEvents(false)
        prefs.setSelectedDisplayCalendarIds(setOf("a", "b"))
        prefs.setSyncFrequency(FREQUENCY_HOURLY)
        prefs.setLastSyncTimestamp(999L)
        prefs.setSyncCompletedTasks(true)

        // Clear
        prefs.clearAll()

        // Verify all defaults
        assertFalse(prefs.isCalendarSyncEnabled().first())
        assertEquals("primary", prefs.getSyncCalendarId().first())
        assertEquals(DIRECTION_BOTH, prefs.getSyncDirection().first())
        assertTrue(prefs.getShowCalendarEvents().first())
        assertEquals(emptySet<String>(), prefs.getSelectedDisplayCalendarIds().first())
        assertEquals(FREQUENCY_15MIN, prefs.getSyncFrequency().first())
        assertEquals(0L, prefs.getLastSyncTimestamp().first())
        assertFalse(prefs.getSyncCompletedTasks().first())
    }
}
