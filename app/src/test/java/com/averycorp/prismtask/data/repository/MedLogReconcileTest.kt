package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure [reconcileMedLogsForTierChange] helper that backs
 * [SelfCareRepository.setTierForTime]. Regression coverage for the bug where
 * tapping "Night → Complete" was also materializing an "Evening" log for
 * medications scheduled for both blocks whenever a legacy blank-time-of-day
 * log existed for that step.
 */
class MedLogReconcileTest {
    private fun step(
        id: String,
        tier: String = "essential",
        timeOfDay: String = "morning"
    ) = SelfCareStepEntity(
        stepId = id,
        routineType = "medication",
        label = id,
        duration = "",
        tier = tier,
        note = "",
        phase = "Medications",
        sortOrder = 0,
        timeOfDay = timeOfDay
    )

    @Test
    fun tappingNightWithLegacyBlankLog_doesNotMaterializeEvening() {
        val med = step(id = "vitD", tier = "essential", timeOfDay = "evening,night")
        val legacyBlank = MedStepLog(id = "vitD", timeOfDay = "")

        val result = reconcileMedLogsForTierChange(
            steps = listOf(med),
            existingLogs = listOf(legacyBlank),
            tiersByTime = mapOf("night" to "complete"),
            touchedTod = "night",
            now = 1_700_000_000_000L
        )

        // Only the night entry should exist — evening must not be auto-logged.
        assertEquals(1, result.size)
        assertEquals("vitD", result[0].id)
        assertEquals("night", result[0].timeOfDay)
    }

    @Test
    fun tappingNight_freshState_onlyLogsNight() {
        val eveningMed = step(id = "melatonin", tier = "essential", timeOfDay = "evening")
        val nightMed = step(id = "vitD", tier = "essential", timeOfDay = "night")

        val result = reconcileMedLogsForTierChange(
            steps = listOf(eveningMed, nightMed),
            existingLogs = emptyList(),
            tiersByTime = mapOf("night" to "complete"),
            touchedTod = "night",
            now = 1L
        )

        assertEquals(1, result.size)
        assertEquals("vitD", result[0].id)
        assertEquals("night", result[0].timeOfDay)
    }

    @Test
    fun tappingNight_preservesLegacyBlankForMedWithNoOverlap() {
        val morningMed = step(id = "vitamin", tier = "essential", timeOfDay = "morning")
        val legacy = MedStepLog(id = "vitamin", timeOfDay = "")

        val result = reconcileMedLogsForTierChange(
            steps = listOf(morningMed),
            existingLogs = listOf(legacy),
            tiersByTime = mapOf("night" to "complete"),
            touchedTod = "night",
            now = 1L
        )

        // Morning med wasn't touched by the night tier tap, so its legacy
        // "done" state should stay intact.
        assertEquals(1, result.size)
        assertEquals("", result[0].timeOfDay)
    }

    @Test
    fun deselectingNight_removesExplicitNightLog() {
        val med = step(id = "vitD", tier = "essential", timeOfDay = "night")
        val existing = MedStepLog(id = "vitD", timeOfDay = "night", at = 42L)

        val result = reconcileMedLogsForTierChange(
            steps = listOf(med),
            existingLogs = listOf(existing),
            // Tier cleared → tiersByTime no longer has "night".
            tiersByTime = emptyMap(),
            touchedTod = "night",
            now = 1L
        )

        assertTrue("Deselecting night should remove its explicit log", result.isEmpty())
    }

    @Test
    fun deselectingNight_withMultiBlockMed_keepsEveningLog() {
        val med = step(id = "vitD", tier = "essential", timeOfDay = "evening,night")
        val eveningLog = MedStepLog(id = "vitD", timeOfDay = "evening", at = 1L)
        val nightLog = MedStepLog(id = "vitD", timeOfDay = "night", at = 2L)

        val result = reconcileMedLogsForTierChange(
            steps = listOf(med),
            existingLogs = listOf(eveningLog, nightLog),
            // Evening stays complete, night is being cleared.
            tiersByTime = mapOf("evening" to "complete"),
            touchedTod = "night",
            now = 10L
        )

        val blocks = result.map { it.timeOfDay }.toSet()
        assertEquals(setOf("evening"), blocks)
    }

    @Test
    fun tappingNight_keepsManualEveningToggleUnrelatedMed() {
        val multiBlock = step(id = "vitD", tier = "essential", timeOfDay = "evening,night")
        // Manual toggle log for a different med on evening, from toggleStep.
        val manual = MedStepLog(id = "manual", timeOfDay = "evening", at = 5L)
        val unrelatedStep = step(id = "manual", tier = "essential", timeOfDay = "evening")

        val result = reconcileMedLogsForTierChange(
            steps = listOf(multiBlock, unrelatedStep),
            existingLogs = listOf(manual),
            tiersByTime = mapOf("night" to "complete"),
            touchedTod = "night",
            now = 10L
        )

        // Manual evening log stays untouched; a new night log exists for vitD.
        val nightIds = result.filter { it.timeOfDay == "night" }.map { it.id }.toSet()
        val eveningIds = result.filter { it.timeOfDay == "evening" }.map { it.id }.toSet()
        assertTrue("vitD should be logged for night", "vitD" in nightIds)
        assertTrue("manual should remain logged for evening", "manual" in eveningIds)
        assertFalse("vitD should NOT be logged for evening", "vitD" in eveningIds)
    }
}
