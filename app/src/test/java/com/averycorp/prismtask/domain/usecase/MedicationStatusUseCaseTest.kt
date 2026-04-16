package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure dedup rule exposed by [MedicationStatusUseCase].
 * The flow composition itself is exercised via instrumentation tests that
 * hit the real Room database; this class focuses on the deterministic
 * priority logic that's the easiest place for regressions to creep in.
 */
class MedicationStatusUseCaseTest {

    @Test
    fun `specific-time dose wins over interval and self-care duplicates`() {
        val doses = listOf(
            dose("Adderall", DoseSource.SELF_CARE_STEP, taken = true),
            dose("Adderall", DoseSource.INTERVAL_HABIT, taken = true),
            dose("Adderall", DoseSource.SPECIFIC_TIME, taken = false)
        )

        val result = MedicationStatusUseCase.dedupByName(doses)

        assertEquals(1, result.size)
        assertEquals(DoseSource.SPECIFIC_TIME, result.first().source)
        assertFalse(result.first().takenToday)
    }

    @Test
    fun `interval wins over self-care when no specific time present`() {
        val doses = listOf(
            dose("Zoloft", DoseSource.SELF_CARE_STEP, taken = false),
            dose("Zoloft", DoseSource.INTERVAL_HABIT, taken = false)
        )

        val result = MedicationStatusUseCase.dedupByName(doses)

        assertEquals(1, result.size)
        assertEquals(DoseSource.INTERVAL_HABIT, result.first().source)
    }

    @Test
    fun `name matching is case-insensitive and trims whitespace`() {
        val doses = listOf(
            dose("  adderall ", DoseSource.INTERVAL_HABIT, taken = true),
            dose("ADDERALL", DoseSource.INTERVAL_HABIT, taken = false)
        )

        val result = MedicationStatusUseCase.dedupByName(doses)

        assertEquals(1, result.size)
        // pending dose wins when priority ties so the UI keeps unfinished work visible
        assertFalse(result.first().takenToday)
    }

    @Test
    fun `distinct medication names are all preserved`() {
        val doses = listOf(
            dose("Adderall", DoseSource.INTERVAL_HABIT, taken = false),
            dose("Vitamin D", DoseSource.SELF_CARE_STEP, taken = false)
        )

        val result = MedicationStatusUseCase.dedupByName(doses)

        assertEquals(2, result.size)
        assertTrue(result.any { it.medicationName == "Adderall" })
        assertTrue(result.any { it.medicationName == "Vitamin D" })
    }

    @Test
    fun `empty input yields empty result`() {
        assertTrue(MedicationStatusUseCase.dedupByName(emptyList()).isEmpty())
    }

    private fun dose(name: String, source: DoseSource, taken: Boolean): MedicationDose =
        MedicationDose(
            medicationName = name,
            displayLabel = name,
            source = source,
            scheduledAt = null,
            takenToday = taken,
            linkedHabitId = null
        )
}
