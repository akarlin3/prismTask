package com.averycorp.prismtask.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.domain.model.UiComplexityTier
import com.averycorp.prismtask.domain.model.isAtLeast
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
 * Regression test suite for the UI Complexity Tier system.
 * Verifies persistence, tier switching, isAtLeast logic, and
 * onboarding state across all tiers.
 */
class UiComplexityTierRegressionTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: UserPreferencesDataStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val file = File(tmpFolder.root, "regression_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        prefs = UserPreferencesDataStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---- Persistence ----

    @Test
    fun `tier persists across DataStore recreation simulating app restart`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        assertEquals(UiComplexityTier.POWER, prefs.uiComplexityTier.first())

        // DataStore officially supports at most one instance per file per
        // process, so cancel the first scope before opening another — this
        // also matches what an app restart does.
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val file = File(tmpFolder.root, "regression_test.preferences_pb")
        val dataStore2 = PreferenceDataStoreFactory.create(scope = scope) { file }
        val prefs2 = UserPreferencesDataStore(dataStore2)
        assertEquals(UiComplexityTier.POWER, prefs2.uiComplexityTier.first())
    }

    // ---- Tier switching mid-session ----

    @Test
    fun `switching from BASIC to STANDARD works`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.BASIC)
        assertEquals(UiComplexityTier.BASIC, prefs.uiComplexityTier.first())
        prefs.setUiComplexityTier(UiComplexityTier.STANDARD)
        assertEquals(UiComplexityTier.STANDARD, prefs.uiComplexityTier.first())
    }

    @Test
    fun `switching from POWER to BASIC works`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        prefs.setUiComplexityTier(UiComplexityTier.BASIC)
        assertEquals(UiComplexityTier.BASIC, prefs.uiComplexityTier.first())
    }

    @Test
    fun `switching from STANDARD to POWER works`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.STANDARD)
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        assertEquals(UiComplexityTier.POWER, prefs.uiComplexityTier.first())
    }

    @Test
    fun `rapid tier cycling does not corrupt state`() = runTest {
        for (tier in UiComplexityTier.entries) {
            prefs.setUiComplexityTier(tier)
            assertEquals(tier, prefs.uiComplexityTier.first())
        }
        // Reverse
        for (tier in UiComplexityTier.entries.reversed()) {
            prefs.setUiComplexityTier(tier)
            assertEquals(tier, prefs.uiComplexityTier.first())
        }
    }

    // ---- isAtLeast: full 9-combination matrix ----

    @Test
    fun `isAtLeast all 9 combinations are correct`() {
        // BASIC
        assertTrue(UiComplexityTier.BASIC.isAtLeast(UiComplexityTier.BASIC))
        assertFalse(UiComplexityTier.BASIC.isAtLeast(UiComplexityTier.STANDARD))
        assertFalse(UiComplexityTier.BASIC.isAtLeast(UiComplexityTier.POWER))

        // STANDARD
        assertTrue(UiComplexityTier.STANDARD.isAtLeast(UiComplexityTier.BASIC))
        assertTrue(UiComplexityTier.STANDARD.isAtLeast(UiComplexityTier.STANDARD))
        assertFalse(UiComplexityTier.STANDARD.isAtLeast(UiComplexityTier.POWER))

        // POWER
        assertTrue(UiComplexityTier.POWER.isAtLeast(UiComplexityTier.BASIC))
        assertTrue(UiComplexityTier.POWER.isAtLeast(UiComplexityTier.STANDARD))
        assertTrue(UiComplexityTier.POWER.isAtLeast(UiComplexityTier.POWER))
    }

    // ---- Onboarding interaction with tier ----

    @Test
    fun `onboarding never re-appears after first completion regardless of tier changes`() = runTest {
        // Complete onboarding
        prefs.markTierOnboardingShown()
        assertTrue(prefs.tierOnboardingShown.first())

        // Switch tiers multiple times
        prefs.setUiComplexityTier(UiComplexityTier.BASIC)
        assertTrue(prefs.tierOnboardingShown.first())

        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        assertTrue(prefs.tierOnboardingShown.first())

        prefs.setUiComplexityTier(UiComplexityTier.STANDARD)
        assertTrue(prefs.tierOnboardingShown.first())
    }

    @Test
    fun `tier onboarding shown defaults to false for fresh install`() = runTest {
        assertFalse(prefs.tierOnboardingShown.first())
    }

    // ---- fromName robustness ----

    @Test
    fun `fromName handles invalid input gracefully`() {
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName(null))
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName(""))
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName("INVALID"))
        assertEquals(UiComplexityTier.STANDARD, UiComplexityTier.fromName("basic")) // case-sensitive
    }

    @Test
    fun `fromName returns correct tier for all valid names`() {
        UiComplexityTier.entries.forEach { tier ->
            assertEquals(tier, UiComplexityTier.fromName(tier.name))
        }
    }

    // ---- Gating behavior simulation ----

    @Test
    fun `BASIC tier gates settings correctly`() {
        val tier = UiComplexityTier.BASIC
        // Should see
        assertTrue(tier.isAtLeast(UiComplexityTier.BASIC))
        // Should NOT see STANDARD+ features
        assertFalse(tier.isAtLeast(UiComplexityTier.STANDARD))
        assertFalse(tier.isAtLeast(UiComplexityTier.POWER))
    }

    @Test
    fun `STANDARD tier shows standard features but not power features`() {
        val tier = UiComplexityTier.STANDARD
        assertTrue(tier.isAtLeast(UiComplexityTier.BASIC))
        assertTrue(tier.isAtLeast(UiComplexityTier.STANDARD))
        assertFalse(tier.isAtLeast(UiComplexityTier.POWER))
    }

    @Test
    fun `POWER tier shows all features`() {
        val tier = UiComplexityTier.POWER
        assertTrue(tier.isAtLeast(UiComplexityTier.BASIC))
        assertTrue(tier.isAtLeast(UiComplexityTier.STANDARD))
        assertTrue(tier.isAtLeast(UiComplexityTier.POWER))
    }

    @Test
    fun `tier clearAll resets to STANDARD default`() = runTest {
        prefs.setUiComplexityTier(UiComplexityTier.POWER)
        prefs.clearAll()
        assertEquals(UiComplexityTier.STANDARD, prefs.uiComplexityTier.first())
    }

    // ---- Display metadata ----

    @Test
    fun `all tiers have non-empty displayName and description`() {
        UiComplexityTier.entries.forEach { tier ->
            assertTrue(tier.displayName.isNotBlank())
            assertTrue(tier.description.isNotBlank())
        }
    }

    @Test
    fun `ordinal order is BASIC lt STANDARD lt POWER`() {
        assertTrue(UiComplexityTier.BASIC.ordinal < UiComplexityTier.STANDARD.ordinal)
        assertTrue(UiComplexityTier.STANDARD.ordinal < UiComplexityTier.POWER.ordinal)
    }
}
