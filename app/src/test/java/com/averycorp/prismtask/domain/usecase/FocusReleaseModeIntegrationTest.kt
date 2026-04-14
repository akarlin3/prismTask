package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.CelebrationIntensity
import com.averycorp.prismtask.data.preferences.GoodEnoughEscalation
import com.averycorp.prismtask.data.preferences.NdPreferences
import com.averycorp.prismtask.data.preferences.effectiveCelebrationIntensity
import com.averycorp.prismtask.data.preferences.shouldFireShipItCelebration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests that verify multiple Focus & Release Mode subsystems
 * compose correctly when combined with each other and with ADHD/Calm modes.
 */
class FocusReleaseModeIntegrationTest {
    // region Mode activation

    @Test
    fun `enabling FR mode activates sub-features based on their toggles`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            goodEnoughTimersEnabled = true,
            antiReworkEnabled = true,
            shipItCelebrationsEnabled = true,
            paralysisBreakersEnabled = true
        )
        assertTrue(prefs.focusReleaseModeEnabled)
        assertTrue(prefs.goodEnoughTimersEnabled)
        assertTrue(prefs.antiReworkEnabled)
        assertTrue(prefs.shipItCelebrationsEnabled)
        assertTrue(prefs.paralysisBreakersEnabled)
    }

    @Test
    fun `disabling FR mode means sub-features inactive`() {
        val prefs = NdPreferences(focusReleaseModeEnabled = false)
        // Even though sub-settings default to true, FR mode is off
        // Use case functions check focusReleaseModeEnabled first
        assertNull(
            ShipItCelebrationManager.createCelebration(
                CelebrationTrigger.NORMAL_COMPLETION,
                prefs
            )
        )
        val task = TaskEntity(id = 1, title = "Test", isCompleted = true)
        val decision = AntiReworkGuard.evaluate(task, prefs)
        assertTrue(decision is ReworkDecision.Allow)
    }

    // endregion

    // region FR + ADHD combined

    @Test
    fun `FR plus ADHD both active with no conflicts`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            adhdModeEnabled = true,
            goodEnoughTimersEnabled = true,
            taskDecompositionEnabled = true,
            completionAnimations = true,
            shipItCelebrationsEnabled = true
        )
        // Ship-It should take priority over ADHD completion
        assertTrue(shouldFireShipItCelebration(prefs))
        // Paralysis breaker should prefer smallest tasks
        val tasks = listOf(
            TaskEntity(id = 1, title = "Big", priority = 4, estimatedDuration = 120),
            TaskEntity(id = 2, title = "Small", priority = 1, estimatedDuration = 5)
        )
        val suggested = ParalysisBreaker.suggestNextTask(tasks, prefs)
        assertEquals(2L, suggested!!.id) // Smallest task for ADHD momentum
    }

    @Test
    fun `FR plus ADHD anti-rework includes ADHD suggestion`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            adhdModeEnabled = true,
            antiReworkEnabled = true,
            softWarningEnabled = true
        )
        val task = TaskEntity(id = 1, title = "Test", isCompleted = true, completedAt = System.currentTimeMillis())
        val decision = AntiReworkGuard.evaluate(task, prefs)
        assertTrue(decision is ReworkDecision.SoftWarning)
        assertTrue((decision as ReworkDecision.SoftWarning).adhdModeActive)
    }

    // endregion

    // region FR + Calm combined

    @Test
    fun `FR plus Calm forces LOW celebration intensity`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            calmModeEnabled = true,
            shipItCelebrationsEnabled = true,
            celebrationIntensity = CelebrationIntensity.HIGH
        )
        assertEquals(CelebrationIntensity.LOW, effectiveCelebrationIntensity(prefs))

        val celebration = ShipItCelebrationManager.createCelebration(
            CelebrationTrigger.NORMAL_COMPLETION,
            prefs
        )
        assertNotNull(celebration)
        assertEquals(CelebrationIntensity.LOW, celebration!!.intensity)
    }

    // endregion

    // region FR + ADHD + Calm combined

    @Test
    fun `all three modes compose correctly`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            adhdModeEnabled = true,
            calmModeEnabled = true,
            goodEnoughTimersEnabled = true,
            antiReworkEnabled = true,
            softWarningEnabled = true,
            shipItCelebrationsEnabled = true,
            celebrationIntensity = CelebrationIntensity.HIGH,
            paralysisBreakersEnabled = true,
            autoSuggestEnabled = true,
            completionAnimations = true
        )
        // Calm mode forces LOW intensity
        assertEquals(CelebrationIntensity.LOW, effectiveCelebrationIntensity(prefs))
        // Ship-It takes priority over ADHD completion
        assertTrue(shouldFireShipItCelebration(prefs))
        // Paralysis breaker prefers smallest tasks (ADHD interaction)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Big", priority = 4, estimatedDuration = 120),
            TaskEntity(id = 2, title = "Small", priority = 1, estimatedDuration = 5)
        )
        val suggested = ParalysisBreaker.suggestNextTask(tasks, prefs)
        assertEquals(2L, suggested!!.id)
    }

    // endregion

    // region Good Enough Timer -> Ship-It Celebration flow

    @Test
    fun `timer expires then ship triggers celebration`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            goodEnoughTimersEnabled = true,
            defaultGoodEnoughMinutes = 30,
            goodEnoughEscalation = GoodEnoughEscalation.DIALOG,
            shipItCelebrationsEnabled = true,
            celebrationIntensity = CelebrationIntensity.MEDIUM
        )

        val timer = GoodEnoughTimerManager()
        timer.startTracking(35) // Past threshold
        timer.pause()

        val event = timer.check(prefs, null)
        assertNotNull(event)
        assertTrue(event is TimerEvent.Dialog)

        // User clicks "Ship it" → celebration fires
        val celebration = ShipItCelebrationManager.createCelebration(
            CelebrationTrigger.GOOD_ENOUGH_SHIP,
            prefs
        )
        assertNotNull(celebration)
        assertEquals(CelebrationTrigger.GOOD_ENOUGH_SHIP, celebration!!.trigger)
    }

    // endregion

    // region Anti-Rework -> Ship-It Celebration flow

    @Test
    fun `resisting re-edit triggers celebration`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            antiReworkEnabled = true,
            softWarningEnabled = true,
            shipItCelebrationsEnabled = true
        )

        val task = TaskEntity(id = 1, title = "Test", isCompleted = true, completedAt = System.currentTimeMillis())
        val decision = AntiReworkGuard.evaluate(task, prefs)
        assertTrue(decision is ReworkDecision.SoftWarning)

        // User taps "You're right, leave it" → celebration fires
        val celebration = ShipItCelebrationManager.createCelebration(
            CelebrationTrigger.RESISTED_REWORK,
            prefs
        )
        assertNotNull(celebration)
        assertEquals(CelebrationTrigger.RESISTED_REWORK, celebration!!.trigger)
    }

    // endregion

    // region Revision Counter -> Lock -> Celebration flow

    @Test
    fun `revision limit reached then lock triggers celebration`() {
        val prefs = NdPreferences(
            focusReleaseModeEnabled = true,
            antiReworkEnabled = true,
            revisionCounterEnabled = true,
            maxRevisions = 3,
            shipItCelebrationsEnabled = true
        )

        val task = TaskEntity(
            id = 1,
            title = "Test",
            isCompleted = true,
            completedAt = System.currentTimeMillis(),
            revisionCount = 3
        )
        val decision = AntiReworkGuard.evaluate(task, prefs)
        assertTrue(decision is ReworkDecision.MaxRevisionsReached)

        // User clicks "It's done — lock it" → celebration fires
        val celebration = ShipItCelebrationManager.createCelebration(
            CelebrationTrigger.LOCKED_AT_MAX_REVISIONS,
            prefs
        )
        assertNotNull(celebration)
        assertEquals(CelebrationTrigger.LOCKED_AT_MAX_REVISIONS, celebration!!.trigger)
    }

    // endregion
}
