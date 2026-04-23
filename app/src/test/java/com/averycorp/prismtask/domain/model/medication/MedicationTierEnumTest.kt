package com.averycorp.prismtask.domain.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationTierEnumTest {

    @Test
    fun medicationTier_fromStorage_acceptsLowercaseTokens() {
        assertEquals(MedicationTier.ESSENTIAL, MedicationTier.fromStorage("essential"))
        assertEquals(MedicationTier.PRESCRIPTION, MedicationTier.fromStorage("prescription"))
        assertEquals(MedicationTier.COMPLETE, MedicationTier.fromStorage("complete"))
    }

    @Test
    fun medicationTier_fromStorage_acceptsUppercase() {
        assertEquals(MedicationTier.PRESCRIPTION, MedicationTier.fromStorage("PRESCRIPTION"))
    }

    @Test
    fun medicationTier_fromStorage_defaultsOnUnknownOrBlank() {
        assertEquals(MedicationTier.ESSENTIAL, MedicationTier.fromStorage(null))
        assertEquals(MedicationTier.ESSENTIAL, MedicationTier.fromStorage(""))
        assertEquals(MedicationTier.ESSENTIAL, MedicationTier.fromStorage("bogus"))
    }

    @Test
    fun medicationTier_toStorage_isLowercaseMatchingLegacyColumn() {
        assertEquals("essential", MedicationTier.ESSENTIAL.toStorage())
        assertEquals("prescription", MedicationTier.PRESCRIPTION.toStorage())
        assertEquals("complete", MedicationTier.COMPLETE.toStorage())
    }

    @Test
    fun medicationTier_ladderIsOrdered() {
        assertEquals(
            listOf(MedicationTier.ESSENTIAL, MedicationTier.PRESCRIPTION, MedicationTier.COMPLETE),
            MedicationTier.LADDER
        )
    }

    @Test
    fun achievedTier_fromStorage_supportsSkipped() {
        assertEquals(AchievedTier.SKIPPED, AchievedTier.fromStorage("skipped"))
        assertEquals(AchievedTier.ESSENTIAL, AchievedTier.fromStorage("essential"))
        assertEquals(AchievedTier.COMPLETE, AchievedTier.fromStorage("COMPLETE"))
    }

    @Test
    fun achievedTier_fromStorage_defaultsOnUnknown() {
        assertEquals(AchievedTier.SKIPPED, AchievedTier.fromStorage(null))
        assertEquals(AchievedTier.SKIPPED, AchievedTier.fromStorage(""))
        assertEquals(AchievedTier.SKIPPED, AchievedTier.fromStorage("oops"))
    }

    @Test
    fun achievedTier_from_mapsMedicationTier() {
        assertEquals(AchievedTier.ESSENTIAL, AchievedTier.from(MedicationTier.ESSENTIAL))
        assertEquals(AchievedTier.PRESCRIPTION, AchievedTier.from(MedicationTier.PRESCRIPTION))
        assertEquals(AchievedTier.COMPLETE, AchievedTier.from(MedicationTier.COMPLETE))
    }

    @Test
    fun tierSource_roundTrips() {
        assertEquals(TierSource.COMPUTED, TierSource.fromStorage("computed"))
        assertEquals(TierSource.USER_SET, TierSource.fromStorage("user_set"))
        assertEquals(TierSource.USER_SET, TierSource.fromStorage("USER_SET"))
        assertEquals("computed", TierSource.COMPUTED.toStorage())
        assertEquals("user_set", TierSource.USER_SET.toStorage())
    }

    @Test
    fun tierSource_defaultsToComputedOnUnknown() {
        assertEquals(TierSource.COMPUTED, TierSource.fromStorage(null))
        assertEquals(TierSource.COMPUTED, TierSource.fromStorage(""))
        assertEquals(TierSource.COMPUTED, TierSource.fromStorage("nonsense"))
    }
}
