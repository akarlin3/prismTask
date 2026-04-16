package com.averycorp.prismtask.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.averycorp.prismtask.domain.model.UiComplexityTier
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
 * Unit tests for UI complexity tier and tier onboarding in [UserPreferencesDataStore].
 */
class UiComplexityTierDataStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: UserPreferencesDataStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val file = File(tmpFolder.root, "ui_tier_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        prefs = UserPreferencesDataStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `default tier is STANDARD`() = runTest {
        assertEquals(UiComplexityTier.STANDARD, prefs.uiComplexityTier.first())
    }

    @Test
    fun `set and read BASIC tier`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.BASIC)
        assertEquals(UiComplexityTier.BASIC, prefs.uiComplexityTier.first())
    }

    @Test
    fun `set and read STANDARD tier`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.STANDARD)
        assertEquals(UiComplexityTier.STANDARD, prefs.uiComplexityTier.first())
    }

    @Test
    fun `set and read POWER tier`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        assertEquals(UiComplexityTier.POWER, prefs.uiComplexityTier.first())
    }

    @Test
    fun `tier survives overwrite cycle`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        assertEquals(UiComplexityTier.POWER, prefs.uiComplexityTier.first())
        prefs.setUiComplexityTier(UiComplexityTier.BASIC)
        assertEquals(UiComplexityTier.BASIC, prefs.uiComplexityTier.first())
        prefs.setUiComplexityTier(UiComplexityTier.STANDARD)
        assertEquals(UiComplexityTier.STANDARD, prefs.uiComplexityTier.first())
    }

    @Test
    fun `tier onboarding shown defaults to false`() = runTest {
        assertFalse(prefs.tierOnboardingShown.first())
    }

    @Test
    fun `markTierOnboardingShown sets flag to true`() = runTest {
        prefs.markTierOnboardingShown()
        assertTrue(prefs.tierOnboardingShown.first())
    }

    @Test
    fun `clearAll resets tier to default STANDARD`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        prefs.markTierOnboardingShown()
        prefs.clearAll()
        assertEquals(UiComplexityTier.STANDARD, prefs.uiComplexityTier.first())
        assertFalse(prefs.tierOnboardingShown.first())
    }

    @Test
    fun `DataStore recreated from same file retains tier`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        assertEquals(UiComplexityTier.POWER, prefs.uiComplexityTier.first())

        // DataStore officially supports at most one instance per file per
        // process, so cancel the first scope before opening another — this
        // also matches what an app restart does.
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val file = File(tmpFolder.root, "ui_tier_test.preferences_pb")
        val dataStore2 = PreferenceDataStoreFactory.create(scope = scope) { file }
        val prefs2 = UserPreferencesDataStore(dataStore2)
        assertEquals(UiComplexityTier.POWER, prefs2.uiComplexityTier.first())
    }
}
