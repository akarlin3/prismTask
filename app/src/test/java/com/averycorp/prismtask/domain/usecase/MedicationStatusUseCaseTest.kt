package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.core.time.LocalDateFlow
import com.averycorp.prismtask.core.time.TimeProvider
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.preferences.StartOfDay
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

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

    // ── Flow-integration: re-keys the DAO query when the wall-clock crosses SoD ──

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDueDosesToday_reKeysDao_acrossSoDBoundary() = runTest {
        // 23:00 UTC on Apr 25 with SoD = 4am → logical date "2026-04-25".
        // Advance 6h → 05:00 UTC Apr 26 → logical date "2026-04-26".
        val testScope = this
        val base = Instant.parse("2026-04-25T23:00:00Z")
        val virtualClock = object : TimeProvider {
            override fun now(): Instant =
                base.plusMillis(testScope.testScheduler.currentTime)
            override fun zone(): ZoneId = ZoneId.of("UTC")
        }
        val sod = MutableStateFlow(StartOfDay(hour = 4, minute = 0, hasBeenSet = true))

        val medication = MedicationEntity(
            id = 1L,
            name = "Adderall",
            tier = "ESSENTIAL",
            scheduleMode = "INTERVAL",
            dosesPerDay = 2
        )
        val yesterdayDose = MedicationDoseEntity(
            id = 100L,
            medicationId = 1L,
            slotKey = MedicationSlotGrouper.ANYTIME_KEY,
            takenAt = base.toEpochMilli() - 1_000L,
            takenDateLocal = "2026-04-25"
        )

        val medicationDao: MedicationDao = mockk(relaxed = true)
        val medicationDoseDao: MedicationDoseDao = mockk(relaxed = true)
        every { medicationDao.getActive() } returns flowOf(listOf(medication))
        every { medicationDoseDao.getForDate("2026-04-25") } returns
            flowOf(listOf(yesterdayDose, yesterdayDose)) // 2 doses → satisfied
        every { medicationDoseDao.getForDate("2026-04-26") } returns
            flowOf(emptyList()) // fresh slate

        val prefs: TaskBehaviorPreferences = mockk(relaxed = true)
        every { prefs.getStartOfDay() } returns sod

        val useCase = MedicationStatusUseCase(
            medicationDao = medicationDao,
            medicationDoseDao = medicationDoseDao,
            taskBehaviorPreferences = prefs,
            localDateFlow = LocalDateFlow(virtualClock)
        )

        val emitted = mutableListOf<List<MedicationDose>>()
        val job = launch { useCase.observeDueDosesToday().collect { emitted.add(it) } }
        runCurrent()
        // Yesterday's logical day: 2 doses recorded, schedule satisfied → no
        // due-and-untaken entries.
        assertEquals(1, emitted.size)
        assertTrue(emitted.first().isEmpty())

        advanceTimeBy(6 * 60 * 60 * 1000L)
        runCurrent()

        // After the SoD boundary crossing the DAO query re-keys and the
        // fresh logical day shows the medication as due.
        assertEquals(2, emitted.size)
        assertEquals(1, emitted.last().size)
        assertEquals("Adderall", emitted.last().first().medicationName)
        assertFalse(emitted.last().first().takenToday)

        job.cancel()
        advanceUntilIdle()
    }
}
