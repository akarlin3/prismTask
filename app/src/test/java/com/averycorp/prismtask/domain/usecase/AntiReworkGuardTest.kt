package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.NdPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiReworkGuardTest {
    private val frPrefs = NdPreferences(
        focusReleaseModeEnabled = true,
        antiReworkEnabled = true,
        softWarningEnabled = true,
        coolingOffEnabled = false,
        coolingOffMinutes = 30,
        revisionCounterEnabled = false,
        maxRevisions = 3,
        adhdModeEnabled = false
    )

    private val completedTask = TaskEntity(
        id = 1,
        title = "Test Task",
        isCompleted = true,
        completedAt = System.currentTimeMillis() - 5 * 60 * 1000, // 5 min ago
        revisionCount = 0,
        revisionLocked = false
    )

    @Test
    fun `allow when FR mode is off`() {
        val prefs = frPrefs.copy(focusReleaseModeEnabled = false)
        val result = AntiReworkGuard.evaluate(completedTask, prefs)
        assertTrue(result is ReworkDecision.Allow)
    }

    @Test
    fun `allow when anti-rework is disabled`() {
        val prefs = frPrefs.copy(antiReworkEnabled = false)
        val result = AntiReworkGuard.evaluate(completedTask, prefs)
        assertTrue(result is ReworkDecision.Allow)
    }

    @Test
    fun `allow when task is not completed`() {
        val task = completedTask.copy(isCompleted = false)
        val result = AntiReworkGuard.evaluate(task, frPrefs)
        assertTrue(result is ReworkDecision.Allow)
    }

    @Test
    fun `soft warning for completed task when soft warning enabled`() {
        val result = AntiReworkGuard.evaluate(completedTask, frPrefs)
        assertTrue(result is ReworkDecision.SoftWarning)
        assertEquals(0, (result as ReworkDecision.SoftWarning).revisionCount)
    }

    @Test
    fun `soft warning includes ADHD mode flag`() {
        val prefs = frPrefs.copy(adhdModeEnabled = true)
        val result = AntiReworkGuard.evaluate(completedTask, prefs)
        assertTrue(result is ReworkDecision.SoftWarning)
        assertTrue((result as ReworkDecision.SoftWarning).adhdModeActive)
    }

    @Test
    fun `cooling off blocks editing within period`() {
        val prefs = frPrefs.copy(coolingOffEnabled = true, coolingOffMinutes = 30)
        // Task completed 5 minutes ago, cooling-off is 30 min
        val result = AntiReworkGuard.evaluate(completedTask, prefs)
        assertTrue(result is ReworkDecision.CoolingOff)
        assertTrue((result as ReworkDecision.CoolingOff).remainingMinutes > 0)
    }

    @Test
    fun `cooling off allows editing after period expires`() {
        val prefs = frPrefs.copy(coolingOffEnabled = true, coolingOffMinutes = 2)
        // Task completed 5 minutes ago, cooling-off is 2 min — should have expired
        val result = AntiReworkGuard.evaluate(completedTask, prefs)
        // Now falls through to soft warning check
        assertTrue(result is ReworkDecision.SoftWarning)
    }

    @Test
    fun `max revisions reached shows dialog`() {
        val prefs = frPrefs.copy(revisionCounterEnabled = true, maxRevisions = 3)
        val task = completedTask.copy(revisionCount = 3)
        val result = AntiReworkGuard.evaluate(task, prefs)
        assertTrue(result is ReworkDecision.MaxRevisionsReached)
        assertEquals(3, (result as ReworkDecision.MaxRevisionsReached).revisionCount)
        assertEquals(3, result.maxRevisions)
    }

    @Test
    fun `per-task max revisions override works`() {
        val prefs = frPrefs.copy(revisionCounterEnabled = true, maxRevisions = 3)
        val task = completedTask.copy(revisionCount = 5, maxRevisionsOverride = 5)
        val result = AntiReworkGuard.evaluate(task, prefs)
        assertTrue(result is ReworkDecision.MaxRevisionsReached)
    }

    @Test
    fun `per-task override allows higher limit`() {
        val prefs = frPrefs.copy(revisionCounterEnabled = true, maxRevisions = 3)
        val task = completedTask.copy(revisionCount = 3, maxRevisionsOverride = 10)
        val result = AntiReworkGuard.evaluate(task, prefs)
        // revisionCount (3) < maxRevisionsOverride (10), falls through to soft warning
        assertTrue(result is ReworkDecision.SoftWarning)
    }

    @Test
    fun `revision locked tasks return RevisionLocked`() {
        val task = completedTask.copy(revisionLocked = true)
        val result = AntiReworkGuard.evaluate(task, frPrefs)
        assertTrue(result is ReworkDecision.RevisionLocked)
    }

    @Test
    fun `revision locked takes priority over cooling off`() {
        val prefs = frPrefs.copy(coolingOffEnabled = true)
        val task = completedTask.copy(revisionLocked = true)
        val result = AntiReworkGuard.evaluate(task, prefs)
        assertTrue(result is ReworkDecision.RevisionLocked)
    }
}
