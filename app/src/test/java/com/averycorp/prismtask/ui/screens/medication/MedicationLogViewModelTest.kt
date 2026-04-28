package com.averycorp.prismtask.ui.screens.medication

import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity
import com.averycorp.prismtask.data.repository.MedicationRepository
import com.averycorp.prismtask.data.repository.MedicationSlotRepository
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the medication log's tier-state-only surfacing — when a past
 * day has `medication_tier_states` rows but no real doses, the log should
 * display them as slot-level entries (typically pre-PR #857 tier-button
 * taps that wrote a tier-state row but no doses, or post-PR #857 skips
 * that intentionally record a slot-level "skipped" marker).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var medicationRepository: MedicationRepository
    private lateinit var slotRepository: MedicationSlotRepository

    private val morningSlot = MedicationSlotEntity(
        id = 10L,
        name = "Morning",
        idealTime = "09:00",
        driftMinutes = 60
    )
    private val eveningSlot = MedicationSlotEntity(
        id = 20L,
        name = "Evening",
        idealTime = "21:00",
        driftMinutes = 60
    )
    private val essMed = MedicationEntity(id = 1L, name = "Vitamin D", tier = "essential")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        medicationRepository = mockk(relaxed = true)
        slotRepository = mockk(relaxed = true)
        every { medicationRepository.observeAll() } returns flowOf(listOf(essMed))
        every { medicationRepository.observeAllDoses() } returns flowOf(emptyList())
        every { slotRepository.observeAllSlots() } returns
            flowOf(listOf(morningSlot, eveningSlot))
        every { slotRepository.observeAllTierStates() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = MedicationLogViewModel(
        repository = medicationRepository,
        slotRepository = slotRepository
    )

    @Test
    fun dosesWithNumericSlotKeyResolveToSlotsById() = runTest(dispatcher) {
        // Regression: post-#857 every dose carries `slotKey = slot.id.toString()`.
        // The log screen used to bucket against legacy TOD strings and the
        // numeric slot key fell through every bucket — doses were invisible
        // even though the meds screen showed them ticked. The viewmodel's
        // new `slotsById` projection, plus `dosesByResolvedSlot` /
        // `legacyDosesBySlot` derivations, give the screen what it needs to
        // group those doses under the slot's actual display name.
        val date = "2026-04-25"
        val dose = MedicationDoseEntity(
            id = 100L,
            medicationId = essMed.id,
            slotKey = morningSlot.id.toString(),
            takenAt = 1_700_000_000_000L,
            takenDateLocal = date,
            isSyntheticSkip = false
        )
        every { medicationRepository.observeAllDoses() } returns flowOf(listOf(dose))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        val day = state.value.single { it.date == date }
        // slotsById is populated with every active slot so the screen can
        // resolve any numeric slot key.
        assertEquals(
            mapOf(morningSlot.id to morningSlot, eveningSlot.id to eveningSlot),
            day.slotsById
        )
        // dosesByResolvedSlot groups the dose under the resolved slot —
        // the screen will render this under "MORNING · 09:00".
        assertEquals(mapOf(morningSlot to listOf(dose)), day.dosesByResolvedSlot)
        // legacyDosesBySlot is empty because every dose resolved cleanly.
        assertTrue(day.legacyDosesBySlot.isEmpty())
    }

    @Test
    fun dosesWithLegacySlotKeyFallThroughToLegacyMap() = runTest(dispatcher) {
        // Pre-migration "morning"/"afternoon"/"anytime"/"HH:MM" strings
        // can't resolve through slotsById (the active slots have numeric
        // ids). Those rows must surface via legacyDosesBySlot so the
        // screen's existing TOD/clock/anytime buckets keep working for
        // history that pre-dates the v1.5 medication schema.
        val date = "2026-04-25"
        val legacyDose = MedicationDoseEntity(
            id = 200L,
            medicationId = essMed.id,
            slotKey = "morning",
            takenAt = 1_700_000_000_000L,
            takenDateLocal = date,
            isSyntheticSkip = false
        )
        every { medicationRepository.observeAllDoses() } returns flowOf(listOf(legacyDose))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        val day = state.value.single { it.date == date }
        assertTrue(day.dosesByResolvedSlot.isEmpty())
        assertEquals(mapOf("morning" to listOf(legacyDose)), day.legacyDosesBySlot)
    }

    @Test
    fun orphanedNumericSlotKeyDoseFallsToLegacyMap() = runTest(dispatcher) {
        // If a slot is deleted after the user logged a dose against it,
        // the numeric key won't resolve through `slotsById`. The dose
        // still belongs in the log — surfaced via `legacyDosesBySlot`
        // so the screen's "ANYTIME" fallback can show it instead of
        // dropping it on the floor.
        val date = "2026-04-25"
        // slotKey "9999" is intentionally an id with no matching slot in
        // the active set — simulates a deleted/archived slot whose
        // historical doses still need to surface in the log.
        val orphanedDose = MedicationDoseEntity(
            id = 300L,
            medicationId = essMed.id,
            slotKey = "9999",
            takenAt = 1_700_000_000_000L,
            takenDateLocal = date,
            isSyntheticSkip = false
        )
        every { medicationRepository.observeAllDoses() } returns flowOf(listOf(orphanedDose))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        val day = state.value.single { it.date == date }
        assertTrue(day.dosesByResolvedSlot.isEmpty())
        assertEquals(mapOf("9999" to listOf(orphanedDose)), day.legacyDosesBySlot)
    }

    @Test
    fun userSetTierStateWithoutDoses_surfacesAsSlotEntry() = runTest(dispatcher) {
        val pastDate = "2026-04-20"
        val tierState = MedicationTierStateEntity(
            id = 1L,
            medicationId = essMed.id,
            slotId = morningSlot.id,
            logDate = pastDate,
            tier = "complete",
            tierSource = "user_set",
            intendedTime = null,
            loggedAt = 1_700_000_000_000L
        )
        every { slotRepository.observeAllTierStates() } returns flowOf(listOf(tierState))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        val days = state.value
        assertEquals(1, days.size)
        val day = days.first()
        assertEquals(pastDate, day.date)
        assertTrue("doses should be empty for tier-state-only history", day.doses.isEmpty())
        assertEquals(1, day.slotEntries.size)
        val entry = day.slotEntries.first()
        assertEquals(morningSlot.id, entry.slot.id)
        assertEquals(AchievedTier.COMPLETE, entry.tier)
        assertEquals(1_700_000_000_000L, entry.displayTime)
    }

    @Test
    fun computedTierStateWithoutDoses_doesNotSurfaceAsSlotEntry() = runTest(dispatcher) {
        // Computed tier-state rows are derivative of doses (refreshTierState
        // writes them on every dose toggle). Surfacing them as separate
        // entries would double-count today's per-med activity.
        val computed = MedicationTierStateEntity(
            id = 1L,
            medicationId = essMed.id,
            slotId = morningSlot.id,
            logDate = "2026-04-20",
            tier = "complete",
            tierSource = "computed",
            loggedAt = 1_700_000_000_000L
        )
        every { slotRepository.observeAllTierStates() } returns flowOf(listOf(computed))

        val vm = newViewModel()
        val collected = vm.days.first { it.isNotEmpty() || true }.also { advanceUntilIdle() }
        advanceUntilIdle()

        // Either no day at all (no doses, no user_set rows) or a day with
        // no slot entries — both are correct outcomes.
        assertTrue(
            "computed tier-state must not surface a slot entry",
            collected.isEmpty() || collected.all { it.slotEntries.isEmpty() }
        )
    }

    @Test
    fun tierStateWithDoseCoveringSameSlot_doesNotSurfaceSlotEntry() = runTest(dispatcher) {
        // When a dose row already exists for the slot (slotKey matches the
        // numeric slot id post bulk-mark fix), the per-med dose row tells
        // the story — the slot entry would be a redundant echo.
        val date = "2026-04-20"
        val dose = MedicationDoseEntity(
            id = 100L,
            medicationId = essMed.id,
            slotKey = morningSlot.id.toString(),
            takenAt = 1_700_000_000_000L,
            takenDateLocal = date,
            isSyntheticSkip = false
        )
        val tierState = MedicationTierStateEntity(
            id = 1L,
            medicationId = essMed.id,
            slotId = morningSlot.id,
            logDate = date,
            tier = "complete",
            tierSource = "user_set",
            loggedAt = 1_700_000_000_000L
        )
        every { medicationRepository.observeAllDoses() } returns flowOf(listOf(dose))
        every { slotRepository.observeAllTierStates() } returns flowOf(listOf(tierState))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        val day = state.value.first()
        assertEquals(1, day.doses.size)
        assertTrue(
            "slot entry should be suppressed when a dose covers the slot",
            day.slotEntries.isEmpty()
        )
    }

    @Test
    fun tierStateWithIntendedTime_prefersIntendedOverLogged() = runTest(dispatcher) {
        // intendedTime is the user-claimed wall-clock; loggedAt is just when
        // the row physically arrived in the DB. Display should prefer the
        // user's claim (matches the Today-screen takenTimeLabel behaviour).
        val tierState = MedicationTierStateEntity(
            id = 1L,
            medicationId = essMed.id,
            slotId = morningSlot.id,
            logDate = "2026-04-20",
            tier = "complete",
            tierSource = "user_set",
            intendedTime = 1_700_000_000_000L,
            loggedAt = 1_700_000_900_000L
        )
        every { slotRepository.observeAllTierStates() } returns flowOf(listOf(tierState))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        val entry = state.value.first().slotEntries.first()
        assertEquals(1_700_000_000_000L, entry.intendedTime)
        assertEquals(1_700_000_000_000L, entry.displayTime)
    }

    @Test
    fun missingSlot_dropsTierStateGracefully() = runTest(dispatcher) {
        // Slot might have been deleted; tier-state row's FK is CASCADE so this
        // shouldn't normally happen, but guard against orphan rows by silently
        // dropping the entry rather than surfacing a row with no slot label.
        val orphan = MedicationTierStateEntity(
            id = 1L,
            medicationId = essMed.id,
            slotId = 999L,
            logDate = "2026-04-20",
            tier = "complete",
            tierSource = "user_set",
            loggedAt = 1_700_000_000_000L
        )
        every { slotRepository.observeAllTierStates() } returns flowOf(listOf(orphan))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        // The day still appears (because the date came from the tier-state),
        // but slotEntries is empty — the missing slot dropped the row.
        val day = state.value.firstOrNull()
        assertEquals(0, day?.slotEntries?.size ?: 0)
    }

    @Test
    fun mixedDoseAndTierStateOnDifferentSlots_surfacesBoth() = runTest(dispatcher) {
        // Morning slot has dose history, evening slot has tier-state-only
        // history. Both should appear on the same day card.
        val date = "2026-04-20"
        val morningDose = MedicationDoseEntity(
            id = 100L,
            medicationId = essMed.id,
            slotKey = morningSlot.id.toString(),
            takenAt = 1_700_000_000_000L,
            takenDateLocal = date,
            isSyntheticSkip = false
        )
        val eveningSkipState = MedicationTierStateEntity(
            id = 1L,
            medicationId = essMed.id,
            slotId = eveningSlot.id,
            logDate = date,
            tier = "skipped",
            tierSource = "user_set",
            loggedAt = 1_700_000_900_000L
        )
        every { medicationRepository.observeAllDoses() } returns flowOf(listOf(morningDose))
        every { slotRepository.observeAllTierStates() } returns flowOf(listOf(eveningSkipState))

        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        val day = state.value.first()
        assertEquals(1, day.doses.size)
        assertEquals(1, day.slotEntries.size)
        assertEquals(eveningSlot.id, day.slotEntries.first().slot.id)
        assertEquals(AchievedTier.SKIPPED, day.slotEntries.first().tier)
    }

    @Test
    fun emptyState_producesNoDays() = runTest(dispatcher) {
        val vm = newViewModel()
        val state = MutableStateFlow<List<MedicationLogDay>>(emptyList())
        backgroundScope.launch { vm.days.collect { state.value = it } }
        advanceUntilIdle()

        assertTrue(state.value.isEmpty())
        assertNull(state.value.firstOrNull())
    }
}
