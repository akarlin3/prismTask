package com.averycorp.prismtask

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the legacy-fallback behavior of the per-family backfill flags
 * added for SPEC_SELF_CARE_STEPS_SYNC_PIPELINE Part 2. Existing users
 * who already ran the single `new_entities_backfill_done` flag before
 * this patch must see their per-family flags default-read as `true`
 * so the upload loops don't re-run.
 */
@RunWith(AndroidJUnit4::class)
class BuiltInSyncPreferencesLegacyFallbackTest {
    private lateinit var prefs: BuiltInSyncPreferences

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Single app-wide DataStore means tests share state. Clear the keys
        // entirely (not `setXxx(false)`) so the legacy-fallback test path
        // is actually reachable — the fallback only activates when a
        // per-family key is unset.
        prefs = BuiltInSyncPreferences(context)
        runBlocking { prefs.clearAll() }
    }

    @After
    fun tearDown() {
        runBlocking { prefs.clearAll() }
    }

    @Test
    fun perFamilyFlag_defaultsFalseWhenNothingSet() = runTest {
        assertFalse(prefs.isCoursesBackfillDone())
        assertFalse(prefs.isCourseCompletionsBackfillDone())
        assertFalse(prefs.isLeisureLogsBackfillDone())
        assertFalse(prefs.isSelfCareStepsBackfillDone())
        assertFalse(prefs.isSelfCareLogsBackfillDone())
    }

    @Test
    fun perFamilyFlag_readsTrueWhenMasterFlagSet_legacyFallback() = runTest {
        prefs.setNewEntitiesBackfillDone(true)

        assertTrue(
            "legacy users with master flag must default per-family flags to true",
            prefs.isCoursesBackfillDone()
        )
        assertTrue(prefs.isCourseCompletionsBackfillDone())
        assertTrue(prefs.isLeisureLogsBackfillDone())
        assertTrue(prefs.isSelfCareStepsBackfillDone())
        assertTrue(prefs.isSelfCareLogsBackfillDone())
    }

    @Test
    fun perFamilyFlag_falseOverridesMasterFlagTrue() = runTest {
        // Emulate "user clears a per-family flag to force re-upload": the
        // explicit false on that family should win over the master flag's
        // legacy fallback.
        prefs.setNewEntitiesBackfillDone(true)
        prefs.setSelfCareStepsBackfillDone(false)

        assertFalse(
            "explicitly-set false must win over master-flag legacy fallback",
            prefs.isSelfCareStepsBackfillDone()
        )
        // Other families still fall back to master = true.
        assertTrue(prefs.isCoursesBackfillDone())
        assertTrue(prefs.isLeisureLogsBackfillDone())
    }

    @Test
    fun perFamilyFlag_independentOfOtherFamilies() = runTest {
        prefs.setCoursesBackfillDone(true)

        assertTrue(prefs.isCoursesBackfillDone())
        assertFalse(
            "setting courses must not affect leisure",
            prefs.isLeisureLogsBackfillDone()
        )
        assertFalse(
            "setting courses must not affect self_care_steps",
            prefs.isSelfCareStepsBackfillDone()
        )
    }

    @Test
    fun perFamilyFlag_trueStaysTrueEvenIfMasterNeverSet() = runTest {
        prefs.setCoursesBackfillDone(true)

        assertTrue(prefs.isCoursesBackfillDone())
        assertFalse(prefs.isNewEntitiesBackfillDone())
    }
}
