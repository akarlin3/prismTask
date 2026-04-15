package com.averycorp.prismtask.data.preferences

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

/**
 * Covers the v1.4.0 additions to [NotificationPreferences]:
 *  - active profile id
 *  - category overrides
 *  - briefing schedule / tone / sections
 *  - streak alerts + lead time
 *  - collaborator digest + event toggles
 *  - watch sync mode + haptic intensity
 *  - snooze durations CSV
 */
class NotificationPreferencesExtendedTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: NotificationPreferences

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val file = File(tmp.root, "notif_ext.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        prefs = NotificationPreferences(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `active profile id defaults to -1`() = runTest {
        assertEquals(-1L, prefs.activeProfileId.first())
    }

    @Test
    fun `set and read active profile id`() = runTest {
        prefs.setActiveProfileId(42L)
        assertEquals(42L, prefs.activeProfileId.first())
    }

    @Test
    fun `category override round-trips`() = runTest {
        prefs.setCategoryProfileOverride("task", 5L)
        prefs.setCategoryProfileOverride("habit", 7L)
        val map = prefs.categoryProfileOverrides.first()
        assertEquals(5L, map["task"])
        assertEquals(7L, map["habit"])
    }

    @Test
    fun `category override can be cleared`() = runTest {
        prefs.setCategoryProfileOverride("task", 5L)
        prefs.setCategoryProfileOverride("task", null)
        val map = prefs.categoryProfileOverrides.first()
        assertFalse("task" in map)
    }

    @Test
    fun `streak defaults`() = runTest {
        assertTrue(prefs.streakAlertsEnabled.first())
        assertEquals(
            NotificationPreferences.DEFAULT_STREAK_AT_RISK_LEAD_HOURS,
            prefs.streakAtRiskLeadHours.first()
        )
    }

    @Test
    fun `streak lead hours clamps to 1-24 range`() = runTest {
        prefs.setStreakAtRiskLeadHours(-5)
        assertEquals(1, prefs.streakAtRiskLeadHours.first())
        prefs.setStreakAtRiskLeadHours(50)
        assertEquals(24, prefs.streakAtRiskLeadHours.first())
    }

    @Test
    fun `briefing morning hour clamps to 0-23`() = runTest {
        prefs.setBriefingMorningHour(99)
        assertEquals(23, prefs.briefingMorningHour.first())
        prefs.setBriefingMorningHour(-3)
        assertEquals(0, prefs.briefingMorningHour.first())
    }

    @Test
    fun `briefing tone rejects unknown values`() = runTest {
        prefs.setBriefingTone("random")
        assertEquals(NotificationPreferences.BRIEFING_TONE_CONCISE, prefs.briefingTone.first())
        prefs.setBriefingTone(NotificationPreferences.BRIEFING_TONE_MOTIVATIONAL)
        assertEquals(NotificationPreferences.BRIEFING_TONE_MOTIVATIONAL, prefs.briefingTone.first())
    }

    @Test
    fun `briefing sections default to core set`() = runTest {
        val sections = prefs.briefingSections.first()
        assertTrue(NotificationPreferences.BRIEFING_SECTION_TODAY_TASKS in sections)
        assertTrue(NotificationPreferences.BRIEFING_SECTION_OVERDUE in sections)
    }

    @Test
    fun `briefing sections can be replaced`() = runTest {
        prefs.setBriefingSections(setOf(NotificationPreferences.BRIEFING_SECTION_STREAK))
        val sections = prefs.briefingSections.first()
        assertEquals(1, sections.size)
        assertTrue(NotificationPreferences.BRIEFING_SECTION_STREAK in sections)
    }

    @Test
    fun `collab defaults are immediate plus all event types on`() = runTest {
        assertEquals(NotificationPreferences.COLLAB_DIGEST_IMMEDIATE, prefs.collabDigestMode.first())
        assertTrue(prefs.collabAssignedEnabled.first())
        assertTrue(prefs.collabMentionedEnabled.first())
    }

    @Test
    fun `watch sync mode persists`() = runTest {
        prefs.setWatchSyncMode(NotificationPreferences.WATCH_SYNC_DIFFERENTIATED)
        assertEquals(
            NotificationPreferences.WATCH_SYNC_DIFFERENTIATED,
            prefs.watchSyncMode.first()
        )
    }

    @Test
    fun `watch volume clamps 0-100`() = runTest {
        prefs.setWatchVolumePercent(200)
        assertEquals(100, prefs.watchVolumePercent.first())
        prefs.setWatchVolumePercent(-20)
        assertEquals(0, prefs.watchVolumePercent.first())
    }

    @Test
    fun `snooze durations csv round-trips`() = runTest {
        prefs.setSnoozeDurationsMinutes(listOf(2, 10, 20))
        assertEquals(listOf(2, 10, 20), prefs.snoozeDurationsMinutes.first())
    }

    @Test
    fun `snooze durations defaults to canonical set when unset`() = runTest {
        assertEquals(NotificationPreferences.DEFAULT_SNOOZE_MINUTES, prefs.snoozeDurationsMinutes.first())
    }
}
