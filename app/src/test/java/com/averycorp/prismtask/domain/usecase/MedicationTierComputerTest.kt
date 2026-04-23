package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.domain.model.medication.MedicationTier
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationTierComputerTest {

    @Test
    fun emptySlot_isSkipped() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = emptyMap(),
            markedTaken = emptySet()
        )
        assertEquals(AchievedTier.SKIPPED, result)
    }

    @Test
    fun noTaken_isSkipped() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(1L to MedicationTier.ESSENTIAL, 2L to MedicationTier.PRESCRIPTION),
            markedTaken = emptySet()
        )
        assertEquals(AchievedTier.SKIPPED, result)
    }

    @Test
    fun allEssentialTaken_noOtherTiers_reachesComplete() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(1L to MedicationTier.ESSENTIAL, 2L to MedicationTier.ESSENTIAL),
            markedTaken = setOf(1L, 2L)
        )
        assertEquals(AchievedTier.COMPLETE, result)
    }

    @Test
    fun allEssentialTaken_prescriptionMissing_stopsAtEssential() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(
                1L to MedicationTier.ESSENTIAL,
                2L to MedicationTier.PRESCRIPTION
            ),
            markedTaken = setOf(1L)
        )
        assertEquals(AchievedTier.ESSENTIAL, result)
    }

    @Test
    fun prescriptionMet_completeMissing_stopsAtPrescription() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(
                1L to MedicationTier.ESSENTIAL,
                2L to MedicationTier.PRESCRIPTION,
                3L to MedicationTier.COMPLETE
            ),
            markedTaken = setOf(1L, 2L)
        )
        assertEquals(AchievedTier.PRESCRIPTION, result)
    }

    @Test
    fun allThreeTiers_allTaken_reachesComplete() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(
                1L to MedicationTier.ESSENTIAL,
                2L to MedicationTier.PRESCRIPTION,
                3L to MedicationTier.COMPLETE
            ),
            markedTaken = setOf(1L, 2L, 3L)
        )
        assertEquals(AchievedTier.COMPLETE, result)
    }

    @Test
    fun essentialPartial_isSkipped() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(
                1L to MedicationTier.ESSENTIAL,
                2L to MedicationTier.ESSENTIAL
            ),
            markedTaken = setOf(1L)
        )
        // One essential taken, one missing — essential rung not satisfied.
        assertEquals(AchievedTier.SKIPPED, result)
    }

    @Test
    fun onlyPrescriptionMed_taken_reachesComplete() {
        // Cumulative membership: the COMPLETE rung includes every med at
        // tier ≤ COMPLETE, which on this slot is just the one PRESCRIPTION
        // med. It's taken → both PRESCRIPTION and COMPLETE rungs satisfied,
        // so achieved = COMPLETE ("all meds taken").
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(1L to MedicationTier.PRESCRIPTION),
            markedTaken = setOf(1L)
        )
        assertEquals(AchievedTier.COMPLETE, result)
    }

    @Test
    fun onlyCompleteMed_taken_reachesComplete() {
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(1L to MedicationTier.COMPLETE),
            markedTaken = setOf(1L)
        )
        assertEquals(AchievedTier.COMPLETE, result)
    }

    @Test
    fun extraneousMarkedIds_areIgnored() {
        // Id 99 is not in the slot — should not elevate achieved.
        val result = MedicationTierComputer.computeAchievedTier(
            medsForSlot = mapOf(1L to MedicationTier.ESSENTIAL),
            markedTaken = setOf(1L, 99L)
        )
        assertEquals(AchievedTier.COMPLETE, result)
    }
}
