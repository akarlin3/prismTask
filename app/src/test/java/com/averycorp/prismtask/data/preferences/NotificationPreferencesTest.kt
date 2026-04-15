package com.averycorp.prismtask.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-backed tests for [NotificationPreferences]. Each test gets a
 * fresh DataStore by virtue of Robolectric's per-test app context isolation,
 * so the documented defaults are observable on first read.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class NotificationPreferencesTest {
    private lateinit var prefs: NotificationPreferences

    @Before
    fun setUp() {
        prefs = NotificationPreferences(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `task reminders default to true`() = runTest {
        assertTrue(prefs.taskRemindersEnabled.first())
    }

    @Test
    fun `setTaskRemindersEnabled false round-trips`() = runTest {
        prefs.setTaskRemindersEnabled(false)
        assertEquals(false, prefs.taskRemindersEnabled.first())
    }

    @Test
    fun `every per-type flag defaults to true`() = runTest {
        assertTrue(prefs.taskRemindersEnabled.first())
        assertTrue(prefs.timerAlertsEnabled.first())
        assertTrue(prefs.medicationRemindersEnabled.first())
        assertTrue(prefs.dailyBriefingEnabled.first())
        assertTrue(prefs.eveningSummaryEnabled.first())
        assertTrue(prefs.weeklySummaryEnabled.first())
        assertTrue(prefs.overloadAlertsEnabled.first())
        assertTrue(prefs.reengagementEnabled.first())
    }

    @Test
    fun `default importance is standard`() = runTest {
        assertEquals(NotificationPreferences.IMPORTANCE_STANDARD, prefs.importance.first())
    }

    @Test
    fun `setImportance urgent persists`() = runTest {
        prefs.setImportance(NotificationPreferences.IMPORTANCE_URGENT)
        assertEquals(NotificationPreferences.IMPORTANCE_URGENT, prefs.importance.first())
    }

    @Test
    fun `setImportance rejects unknown values and falls back to default`() = runTest {
        prefs.setImportance("bogus")
        assertEquals(NotificationPreferences.DEFAULT_IMPORTANCE, prefs.importance.first())
    }

    @Test
    fun `default reminder offset is 15 minutes`() = runTest {
        assertEquals(900_000L, prefs.defaultReminderOffset.first())
        assertEquals(NotificationPreferences.DEFAULT_REMINDER_OFFSET_MS, prefs.defaultReminderOffset.first())
    }

    @Test
    fun `setDefaultReminderOffset 1 hour persists`() = runTest {
        prefs.setDefaultReminderOffset(3_600_000L)
        assertEquals(3_600_000L, prefs.defaultReminderOffset.first())
    }

    @Test
    fun `OFFSET_NONE persists distinctly so callers can detect opt-out`() = runTest {
        prefs.setDefaultReminderOffset(NotificationPreferences.OFFSET_NONE)
        assertEquals(NotificationPreferences.OFFSET_NONE, prefs.defaultReminderOffset.first())
    }

    @Test
    fun `previous importance starts null until first recorded`() = runTest {
        assertNull(prefs.getPreviousImportanceOnce())
    }

    @Test
    fun `setPreviousImportance round-trips`() = runTest {
        prefs.setPreviousImportance(NotificationPreferences.IMPORTANCE_URGENT)
        assertEquals(NotificationPreferences.IMPORTANCE_URGENT, prefs.getPreviousImportanceOnce())
    }

    @Test
    fun `ALL_IMPORTANCES contains the three documented levels`() {
        assertEquals(3, NotificationPreferences.ALL_IMPORTANCES.size)
        assertTrue(NotificationPreferences.IMPORTANCE_MINIMAL in NotificationPreferences.ALL_IMPORTANCES)
        assertTrue(NotificationPreferences.IMPORTANCE_STANDARD in NotificationPreferences.ALL_IMPORTANCES)
        assertTrue(NotificationPreferences.IMPORTANCE_URGENT in NotificationPreferences.ALL_IMPORTANCES)
    }

    @Test
    fun `ALL_REMINDER_OFFSETS contains every documented option including OFFSET_NONE`() {
        assertTrue(0L in NotificationPreferences.ALL_REMINDER_OFFSETS)
        assertTrue(300_000L in NotificationPreferences.ALL_REMINDER_OFFSETS)
        assertTrue(900_000L in NotificationPreferences.ALL_REMINDER_OFFSETS)
        assertTrue(1_800_000L in NotificationPreferences.ALL_REMINDER_OFFSETS)
        assertTrue(3_600_000L in NotificationPreferences.ALL_REMINDER_OFFSETS)
        assertTrue(86_400_000L in NotificationPreferences.ALL_REMINDER_OFFSETS)
        assertTrue(NotificationPreferences.OFFSET_NONE in NotificationPreferences.ALL_REMINDER_OFFSETS)
    }
}
