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
 * Unit tests for Focus & Release Mode preferences in [NdPreferencesDataStore].
 */
class FocusReleasePreferencesTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var ndPrefs: NdPreferencesDataStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val file = File(tmpFolder.root, "fr_prefs_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        ndPrefs = NdPreferencesDataStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // region Default values

    @Test
    fun `defaults have FR mode off`() = runTest {
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertFalse(prefs.focusReleaseModeEnabled)
    }

    @Test
    fun `FR sub-settings have correct defaults`() = runTest {
        val prefs = ndPrefs.ndPreferencesFlow.first()
        // Good Enough Timers
        assertTrue(prefs.goodEnoughTimersEnabled)
        assertEquals(30, prefs.defaultGoodEnoughMinutes)
        assertEquals(GoodEnoughEscalation.NUDGE, prefs.goodEnoughEscalation)
        // Anti-Rework Guards
        assertTrue(prefs.antiReworkEnabled)
        assertTrue(prefs.softWarningEnabled)
        assertFalse(prefs.coolingOffEnabled)
        assertEquals(30, prefs.coolingOffMinutes)
        assertFalse(prefs.revisionCounterEnabled)
        assertEquals(3, prefs.maxRevisions)
        // Ship-It Celebrations
        assertTrue(prefs.shipItCelebrationsEnabled)
        assertEquals(CelebrationIntensity.MEDIUM, prefs.celebrationIntensity)
        // Decision Paralysis Breakers
        assertTrue(prefs.paralysisBreakersEnabled)
        assertTrue(prefs.autoSuggestEnabled)
        assertTrue(prefs.simplifyChoicesEnabled)
        assertEquals(5, prefs.stuckDetectionMinutes)
    }

    // endregion

    // region FR Mode activation

    @Test
    fun `enabling FR mode flips all FR sub-settings on`() = runTest {
        ndPrefs.setFocusReleaseMode(true)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertTrue(prefs.focusReleaseModeEnabled)
        assertTrue(prefs.goodEnoughTimersEnabled)
        assertTrue(prefs.antiReworkEnabled)
        assertTrue(prefs.softWarningEnabled)
        assertTrue(prefs.shipItCelebrationsEnabled)
        assertTrue(prefs.paralysisBreakersEnabled)
        assertTrue(prefs.autoSuggestEnabled)
        assertTrue(prefs.simplifyChoicesEnabled)
        // Cooling-off and revision counter stay off by default
        assertFalse(prefs.coolingOffEnabled)
        assertFalse(prefs.revisionCounterEnabled)
    }

    @Test
    fun `disabling FR mode flips all FR sub-settings off`() = runTest {
        ndPrefs.setFocusReleaseMode(true)
        ndPrefs.setFocusReleaseMode(false)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertFalse(prefs.focusReleaseModeEnabled)
        assertFalse(prefs.goodEnoughTimersEnabled)
        assertFalse(prefs.antiReworkEnabled)
        assertFalse(prefs.softWarningEnabled)
        assertFalse(prefs.shipItCelebrationsEnabled)
        assertFalse(prefs.paralysisBreakersEnabled)
        assertFalse(prefs.autoSuggestEnabled)
        assertFalse(prefs.simplifyChoicesEnabled)
    }

    @Test
    fun `enabling FR mode does not affect ADHD sub-settings`() = runTest {
        ndPrefs.setFocusReleaseMode(true)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertFalse(prefs.adhdModeEnabled)
        assertFalse(prefs.taskDecompositionEnabled)
        assertFalse(prefs.focusGuardEnabled)
    }

    @Test
    fun `enabling FR mode does not affect Calm sub-settings`() = runTest {
        ndPrefs.setFocusReleaseMode(true)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertFalse(prefs.calmModeEnabled)
        assertFalse(prefs.reduceAnimations)
        assertFalse(prefs.mutedColorPalette)
    }

    // endregion

    // region Mode independence

    @Test
    fun `disabling FR mode does not affect active ADHD mode`() = runTest {
        ndPrefs.setAdhdMode(true)
        ndPrefs.setFocusReleaseMode(true)
        ndPrefs.setFocusReleaseMode(false)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertFalse(prefs.focusReleaseModeEnabled)
        assertTrue(prefs.adhdModeEnabled)
        assertTrue(prefs.taskDecompositionEnabled)
    }

    @Test
    fun `disabling FR mode does not affect active Calm mode`() = runTest {
        ndPrefs.setCalmMode(true)
        ndPrefs.setFocusReleaseMode(true)
        ndPrefs.setFocusReleaseMode(false)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertFalse(prefs.focusReleaseModeEnabled)
        assertTrue(prefs.calmModeEnabled)
        assertTrue(prefs.reduceAnimations)
    }

    @Test
    fun `all three modes can be active simultaneously`() = runTest {
        ndPrefs.setAdhdMode(true)
        ndPrefs.setCalmMode(true)
        ndPrefs.setFocusReleaseMode(true)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertTrue(prefs.adhdModeEnabled)
        assertTrue(prefs.calmModeEnabled)
        assertTrue(prefs.focusReleaseModeEnabled)
    }

    // endregion

    // region Individual sub-settings

    @Test
    fun `individual FR sub-setting change does not disable parent toggle`() = runTest {
        ndPrefs.setFocusReleaseMode(true)
        ndPrefs.setGoodEnoughTimersEnabled(false)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertTrue(prefs.focusReleaseModeEnabled) // Parent still on
        assertFalse(prefs.goodEnoughTimersEnabled)
    }

    @Test
    fun `good enough minutes clamped to range`() = runTest {
        ndPrefs.setDefaultGoodEnoughMinutes(2)
        assertEquals(5, ndPrefs.ndPreferencesFlow.first().defaultGoodEnoughMinutes)
        ndPrefs.setDefaultGoodEnoughMinutes(200)
        assertEquals(120, ndPrefs.ndPreferencesFlow.first().defaultGoodEnoughMinutes)
        ndPrefs.setDefaultGoodEnoughMinutes(45)
        assertEquals(45, ndPrefs.ndPreferencesFlow.first().defaultGoodEnoughMinutes)
    }

    @Test
    fun `cooling off minutes clamped to range`() = runTest {
        ndPrefs.setCoolingOffMinutes(5)
        assertEquals(15, ndPrefs.ndPreferencesFlow.first().coolingOffMinutes)
        ndPrefs.setCoolingOffMinutes(200)
        assertEquals(120, ndPrefs.ndPreferencesFlow.first().coolingOffMinutes)
    }

    @Test
    fun `max revisions clamped to range`() = runTest {
        ndPrefs.setMaxRevisions(0)
        assertEquals(1, ndPrefs.ndPreferencesFlow.first().maxRevisions)
        ndPrefs.setMaxRevisions(20)
        assertEquals(10, ndPrefs.ndPreferencesFlow.first().maxRevisions)
    }

    @Test
    fun `stuck detection minutes clamped to range`() = runTest {
        ndPrefs.setStuckDetectionMinutes(0)
        assertEquals(1, ndPrefs.ndPreferencesFlow.first().stuckDetectionMinutes)
        ndPrefs.setStuckDetectionMinutes(30)
        assertEquals(15, ndPrefs.ndPreferencesFlow.first().stuckDetectionMinutes)
    }

    @Test
    fun `escalation enum persists correctly`() = runTest {
        ndPrefs.setGoodEnoughEscalation(GoodEnoughEscalation.LOCK)
        assertEquals(GoodEnoughEscalation.LOCK, ndPrefs.ndPreferencesFlow.first().goodEnoughEscalation)
        ndPrefs.setGoodEnoughEscalation(GoodEnoughEscalation.DIALOG)
        assertEquals(GoodEnoughEscalation.DIALOG, ndPrefs.ndPreferencesFlow.first().goodEnoughEscalation)
    }

    @Test
    fun `celebration intensity enum persists correctly`() = runTest {
        ndPrefs.setCelebrationIntensity(CelebrationIntensity.HIGH)
        assertEquals(CelebrationIntensity.HIGH, ndPrefs.ndPreferencesFlow.first().celebrationIntensity)
        ndPrefs.setCelebrationIntensity(CelebrationIntensity.LOW)
        assertEquals(CelebrationIntensity.LOW, ndPrefs.ndPreferencesFlow.first().celebrationIntensity)
    }

    // endregion

    // region Persistence round-trip

    @Test
    fun `FR preferences survive DataStore round trip`() = runTest {
        ndPrefs.setFocusReleaseMode(true)
        ndPrefs.setDefaultGoodEnoughMinutes(45)
        ndPrefs.setGoodEnoughEscalation(GoodEnoughEscalation.DIALOG)
        ndPrefs.setCoolingOffEnabled(true)
        ndPrefs.setMaxRevisions(5)
        ndPrefs.setCelebrationIntensity(CelebrationIntensity.HIGH)

        val ndPrefs2 = NdPreferencesDataStore(dataStore)
        val prefs = ndPrefs2.ndPreferencesFlow.first()
        assertTrue(prefs.focusReleaseModeEnabled)
        assertEquals(45, prefs.defaultGoodEnoughMinutes)
        assertEquals(GoodEnoughEscalation.DIALOG, prefs.goodEnoughEscalation)
        assertTrue(prefs.coolingOffEnabled)
        assertEquals(5, prefs.maxRevisions)
        assertEquals(CelebrationIntensity.HIGH, prefs.celebrationIntensity)
    }

    // endregion

    // region Generic setter

    @Test
    fun `updateNdPreference with focus_release_mode_enabled triggers full mode activation`() = runTest {
        ndPrefs.updateNdPreference("focus_release_mode_enabled", true)
        val prefs = ndPrefs.ndPreferencesFlow.first()
        assertTrue(prefs.focusReleaseModeEnabled)
        assertTrue(prefs.goodEnoughTimersEnabled)
        assertTrue(prefs.shipItCelebrationsEnabled)
    }

    @Test
    fun `updateNdPreference sets FR boolean sub-settings`() = runTest {
        ndPrefs.updateNdPreference("good_enough_timers_enabled", false)
        assertFalse(ndPrefs.ndPreferencesFlow.first().goodEnoughTimersEnabled)
        ndPrefs.updateNdPreference("anti_rework_enabled", false)
        assertFalse(ndPrefs.ndPreferencesFlow.first().antiReworkEnabled)
    }

    @Test
    fun `updateNdPreference sets FR int values`() = runTest {
        ndPrefs.updateNdPreference("default_good_enough_minutes", 60)
        assertEquals(60, ndPrefs.ndPreferencesFlow.first().defaultGoodEnoughMinutes)
    }

    @Test
    fun `updateNdPreference sets FR enum values`() = runTest {
        ndPrefs.updateNdPreference("good_enough_escalation", "LOCK")
        assertEquals(GoodEnoughEscalation.LOCK, ndPrefs.ndPreferencesFlow.first().goodEnoughEscalation)
        ndPrefs.updateNdPreference("celebration_intensity", "HIGH")
        assertEquals(CelebrationIntensity.HIGH, ndPrefs.ndPreferencesFlow.first().celebrationIntensity)
    }

    // endregion

    // region Helper functions

    @Test
    fun `effectiveCelebrationIntensity returns LOW when calm mode active`() {
        val prefs = NdPreferences(
            calmModeEnabled = true,
            celebrationIntensity = CelebrationIntensity.HIGH
        )
        assertEquals(CelebrationIntensity.LOW, effectiveCelebrationIntensity(prefs))
    }

    @Test
    fun `effectiveCelebrationIntensity returns configured value when calm mode off`() {
        val prefs = NdPreferences(
            calmModeEnabled = false,
            celebrationIntensity = CelebrationIntensity.HIGH
        )
        assertEquals(CelebrationIntensity.HIGH, effectiveCelebrationIntensity(prefs))
    }

    @Test
    fun `shouldFireShipItCelebration true when FR active with celebrations`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            shipItCelebrationsEnabled = true
        )
        assertTrue(shouldFireShipItCelebration(prefs))
    }

    @Test
    fun `shouldFireShipItCelebration false when FR off`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = false,
            shipItCelebrationsEnabled = true
        )
        assertFalse(shouldFireShipItCelebration(prefs))
    }

    @Test
    fun `isAnyNdModeActive detects FR mode`() {
        val prefs = NdPreferences(focusReleaseModeEnabled = true)
        assertTrue(isAnyNdModeActive(prefs))
    }

    @Test
    fun `isAnyNdModeActive returns false when all modes off`() {
        assertFalse(isAnyNdModeActive(NdPreferences()))
    }

    // endregion
}
