package com.averycorp.prismtask.ui.screens.medication

import com.averycorp.prismtask.core.time.LocalDateFlow
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.preferences.StartOfDay
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import com.averycorp.prismtask.data.repository.MedicationRepository
import com.averycorp.prismtask.data.repository.MedicationSlotRepository
import com.averycorp.prismtask.domain.model.BatchMutationType
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.domain.model.medication.BulkMarkScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the new tier-button → dose-log semantics: clicking a tier T
 * should mark every med at tier T or below as taken (real dose row), not
 * write a USER_SET tier-state row that "lies" about the dose log.
 *
 * Spec: `docs/superpowers/specs/2026-04-27-medication-tier-buttons-toggle-doses-design.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModelBulkMarkTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var medicationRepository: MedicationRepository
    private lateinit var slotRepository: MedicationSlotRepository
    private lateinit var taskBehaviorPreferences: TaskBehaviorPreferences
    private lateinit var batchOperationsRepository: BatchOperationsRepository
    private lateinit var localDateFlow: LocalDateFlow

    private val today = "2026-04-27"

    private val morningSlot = MedicationSlotEntity(
        id = 10L,
        name = "Morning",
        idealTime = "09:00",
        driftMinutes = 60
    )

    private val essMed = MedicationEntity(id = 1L, name = "Vitamin D", tier = "essential")
    private val rxMed = MedicationEntity(id = 2L, name = "Statin", tier = "prescription")
    private val completeMed = MedicationEntity(id = 3L, name = "Magnesium", tier = "complete")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        medicationRepository = mockk(relaxed = true)
        slotRepository = mockk(relaxed = true)
        taskBehaviorPreferences = mockk(relaxed = true)
        batchOperationsRepository = mockk(relaxed = true)
        localDateFlow = mockk(relaxed = true)

        every { taskBehaviorPreferences.getStartOfDay() } returns
            MutableStateFlow(StartOfDay(hour = 0, minute = 0, hasBeenSet = true))
        every { localDateFlow.observeIsoString(any()) } returns flowOf(today)

        every { medicationRepository.observeActive() } returns
            flowOf(listOf(essMed, rxMed, completeMed))
        every { slotRepository.observeActiveSlots() } returns flowOf(listOf(morningSlot))
        every { medicationRepository.observeDosesForDate(any()) } returns flowOf(emptyList())
        every { slotRepository.observeTierStatesForDate(any()) } returns flowOf(emptyList())

        coEvery { slotRepository.getMedicationIdsForSlotOnce(morningSlot.id) } returns
            listOf(essMed.id, rxMed.id, completeMed.id)

        coEvery {
            batchOperationsRepository.applyBatch(any(), any())
        } returns BatchOperationsRepository.BatchApplyResult(
            batchId = "test-batch",
            commandText = "test",
            appliedCount = 0,
            skipped = emptyList()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = MedicationViewModel(
        medicationRepository = medicationRepository,
        slotRepository = slotRepository,
        taskBehaviorPreferences = taskBehaviorPreferences,
        batchOperationsRepository = batchOperationsRepository,
        localDateFlow = localDateFlow
    )

    /**
     * `MedicationViewModel`'s public StateFlows are configured with
     * `SharingStarted.WhileSubscribed(5_000L)`, so reading `.value`
     * never warms them — they're stuck on the empty initial value
     * unless someone actively collects. The viewmodel's `bulkMark`
     * path reads `.value` to resolve targets, so the test has to
     * keep a live subscription open. `slotTodayStates` is a `combine`
     * over `activeSlots`, `medications`, `todaysDoses`, and
     * `todaysTierStates`, so subscribing to it transitively warms
     * every upstream we care about.
     */
    private fun CoroutineScope.warmStateFlows(vm: MedicationViewModel) {
        launch { vm.slotTodayStates.collect {} }
        launch { vm.todayDate.collect {} }
    }

    @Test
    fun bulkMark_essential_marksOnlyEssentialMedsAsCompleteDoses() = runTest(dispatcher) {
        val captured = slot<List<ProposedMutationResponse>>()
        coEvery {
            batchOperationsRepository.applyBatch(any(), capture(captured))
        } returns BatchOperationsRepository.BatchApplyResult("b", "c", 1, emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.ESSENTIAL)
        advanceUntilIdle()

        val mutations = captured.captured
        assertEquals(
            "Essential tier should produce one mutation — the essential med — " +
                "not state-change rows for every med in the slot",
            1,
            mutations.size
        )
        assertEquals(essMed.id.toString(), mutations[0].entityId)
        assertEquals(BatchMutationType.COMPLETE.name, mutations[0].mutationType)
    }

    @Test
    fun bulkMark_prescription_marksEssentialAndPrescriptionMedsAsCompleteDoses() = runTest(dispatcher) {
        val captured = slot<List<ProposedMutationResponse>>()
        coEvery {
            batchOperationsRepository.applyBatch(any(), capture(captured))
        } returns BatchOperationsRepository.BatchApplyResult("b", "c", 2, emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.PRESCRIPTION)
        advanceUntilIdle()

        val mutations = captured.captured
        assertEquals(2, mutations.size)
        assertEquals(
            "Should mark essential and prescription, not the complete-tier med",
            setOf(essMed.id.toString(), rxMed.id.toString()),
            mutations.map { it.entityId }.toSet()
        )
        assertTrue(
            "Every mutation should be COMPLETE (real dose insert), never STATE_CHANGE",
            mutations.all { it.mutationType == BatchMutationType.COMPLETE.name }
        )
    }

    @Test
    fun bulkMark_complete_marksEveryMedInSlotAsCompleteDoses() = runTest(dispatcher) {
        val captured = slot<List<ProposedMutationResponse>>()
        coEvery {
            batchOperationsRepository.applyBatch(any(), capture(captured))
        } returns BatchOperationsRepository.BatchApplyResult("b", "c", 3, emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.COMPLETE)
        advanceUntilIdle()

        val mutations = captured.captured
        assertEquals(3, mutations.size)
        assertEquals(
            setOf(essMed.id.toString(), rxMed.id.toString(), completeMed.id.toString()),
            mutations.map { it.entityId }.toSet()
        )
        assertTrue(mutations.all { it.mutationType == BatchMutationType.COMPLETE.name })
    }

    @Test
    fun bulkMark_complete_skipsMedsThatAlreadyHaveARealDose() = runTest(dispatcher) {
        // Essential med is already taken — bulkMark(COMPLETE) should not
        // re-log it. This protects against duplicate dose rows when the
        // user manually checks one med then taps "Done".
        val existingDose = MedicationDoseEntity(
            id = 100L,
            medicationId = essMed.id,
            slotKey = morningSlot.id.toString(),
            takenAt = 0L,
            takenDateLocal = today
        )
        every { medicationRepository.observeDosesForDate(any()) } returns
            flowOf(listOf(existingDose))

        val captured = slot<List<ProposedMutationResponse>>()
        coEvery {
            batchOperationsRepository.applyBatch(any(), capture(captured))
        } returns BatchOperationsRepository.BatchApplyResult("b", "c", 2, emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.COMPLETE)
        advanceUntilIdle()

        val mutations = captured.captured
        assertEquals(
            "Should mark only the 2 untaken meds; the already-taken essential med stays alone",
            setOf(rxMed.id.toString(), completeMed.id.toString()),
            mutations.map { it.entityId }.toSet()
        )
    }

    @Test
    fun bulkMark_skipped_emitsSkipMutationsForEveryMedInScope() = runTest(dispatcher) {
        // Skipped is the one tier that doesn't ladder-filter — skipping
        // the slot means skipping every med in it, regardless of tier.
        val captured = slot<List<ProposedMutationResponse>>()
        coEvery {
            batchOperationsRepository.applyBatch(any(), capture(captured))
        } returns BatchOperationsRepository.BatchApplyResult("b", "c", 3, emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.SKIPPED)
        advanceUntilIdle()

        val mutations = captured.captured
        assertEquals(3, mutations.size)
        assertTrue(
            "Skipped tier should emit SKIP mutations, not STATE_CHANGE",
            mutations.all { it.mutationType == BatchMutationType.SKIP.name }
        )
    }

    @Test
    fun bulkMark_complete_writesSlotKeyAsNumericIdSoPerMedCheckboxesUpdate() = runTest(dispatcher) {
        // Regression: the slot-card UI filters doses with
        // `it.slotKey == slot.id.toString()` (per-med toggle uses the same
        // convention). If bulk-mark wrote slot.name instead, the dose row
        // would be invisible — every checkbox would stay empty after a
        // tier-button tap.
        val captured = slot<List<ProposedMutationResponse>>()
        coEvery {
            batchOperationsRepository.applyBatch(any(), capture(captured))
        } returns BatchOperationsRepository.BatchApplyResult("b", "c", 3, emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.COMPLETE)
        advanceUntilIdle()

        val expectedSlotKey = morningSlot.id.toString()
        captured.captured.forEach { mutation ->
            assertEquals(
                "slot_key on COMPLETE mutation must be slot.id.toString() so the dose " +
                    "row is visible to the per-med checkbox filter",
                expectedSlotKey,
                mutation.proposedNewValues["slot_key"]
            )
        }
    }

    @Test
    fun bulkMark_skipped_writesSlotKeyAsNumericIdSoPerMedCheckboxesUpdate() = runTest(dispatcher) {
        val captured = slot<List<ProposedMutationResponse>>()
        coEvery {
            batchOperationsRepository.applyBatch(any(), capture(captured))
        } returns BatchOperationsRepository.BatchApplyResult("b", "c", 3, emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.SKIPPED)
        advanceUntilIdle()

        val expectedSlotKey = morningSlot.id.toString()
        captured.captured.forEach { mutation ->
            assertEquals(
                "slot_key on SKIP mutation must be slot.id.toString() so the synthetic-" +
                    "skip dose key matches the per-med checkbox filter",
                expectedSlotKey,
                mutation.proposedNewValues["slot_key"]
            )
        }
    }

    @Test
    fun bulkMark_complete_clearsUserSetTierStateOnAffectedSlot() = runTest(dispatcher) {
        // After bulkMark(COMPLETE) the displayTier should come from
        // auto-compute, not a stale USER_SET=SKIPPED from an earlier Skip.
        // The viewmodel guarantees this by deleting USER_SET tier-state
        // rows for every affected slot after the batch applies.
        val priorUserSet = com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity(
            id = 50L,
            medicationId = essMed.id,
            slotId = morningSlot.id,
            logDate = today,
            tier = "skipped",
            tierSource = "user_set"
        )
        coEvery { slotRepository.getTierStatesForDateOnce(today) } returns listOf(priorUserSet)

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        vm.bulkMarkInternal(BulkMarkScope.SLOT, morningSlot.id, AchievedTier.COMPLETE)
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            slotRepository.deleteTierState(
                match { it.id == priorUserSet.id && it.tierSource == "user_set" }
            )
        }
    }
}
