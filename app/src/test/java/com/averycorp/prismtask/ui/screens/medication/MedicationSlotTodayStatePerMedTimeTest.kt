package com.averycorp.prismtask.ui.screens.medication

import com.averycorp.prismtask.core.time.LocalDateFlow
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.preferences.StartOfDay
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import com.averycorp.prismtask.data.repository.MedicationRepository
import com.averycorp.prismtask.data.repository.MedicationSlotRepository
import com.averycorp.prismtask.notifications.MedicationClockRescheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests that [MedicationSlotTodayState.takenAtByMedicationId] is populated
 * from the latest non-synthetic dose `taken_at` per medication. This map
 * drives the inline "8:32 AM" label rendered next to each checked
 * medication row in [MedicationScreen.MedicationDoseRow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationSlotTodayStatePerMedTimeTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var medicationRepository: MedicationRepository
    private lateinit var slotRepository: MedicationSlotRepository
    private lateinit var taskBehaviorPreferences: TaskBehaviorPreferences
    private lateinit var batchOperationsRepository: BatchOperationsRepository
    private lateinit var localDateFlow: LocalDateFlow
    private lateinit var clockRescheduler: MedicationClockRescheduler

    private val today = "2026-04-27"

    private val morningSlot = MedicationSlotEntity(
        id = 10L,
        name = "Morning",
        idealTime = "09:00",
        driftMinutes = 60
    )

    private val medA = MedicationEntity(id = 1L, name = "Vitamin D", tier = "essential")
    private val medB = MedicationEntity(id = 2L, name = "Statin", tier = "prescription")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        medicationRepository = mockk(relaxed = true)
        slotRepository = mockk(relaxed = true)
        taskBehaviorPreferences = mockk(relaxed = true)
        batchOperationsRepository = mockk(relaxed = true)
        localDateFlow = mockk(relaxed = true)
        clockRescheduler = mockk(relaxed = true)

        every { taskBehaviorPreferences.getStartOfDay() } returns
            MutableStateFlow(StartOfDay(hour = 0, minute = 0, hasBeenSet = true))
        every { localDateFlow.observeIsoString(any()) } returns flowOf(today)

        every { medicationRepository.observeActive() } returns flowOf(listOf(medA, medB))
        every { slotRepository.observeActiveSlots() } returns flowOf(listOf(morningSlot))
        every { slotRepository.observeTierStatesForDate(any()) } returns flowOf(emptyList())

        coEvery { slotRepository.getMedicationIdsForSlotOnce(morningSlot.id) } returns
            listOf(medA.id, medB.id)
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
        localDateFlow = localDateFlow,
        clockRescheduler = clockRescheduler
    )

    /** See MedicationViewModelBulkMarkTest for the rationale of warmStateFlows. */
    private fun CoroutineScope.warmStateFlows(vm: MedicationViewModel) {
        launch { vm.slotTodayStates.collect {} }
        launch { vm.todayDate.collect {} }
    }

    private fun dose(medId: Long, takenAt: Long, isSyntheticSkip: Boolean = false) =
        MedicationDoseEntity(
            id = takenAt,
            medicationId = medId,
            slotKey = morningSlot.id.toString(),
            takenAt = takenAt,
            takenDateLocal = today,
            isSyntheticSkip = isSyntheticSkip
        )

    @Test
    fun perMedTakenAt_populatedFromLatestDoseForEachMedication() = runTest(dispatcher) {
        val medATime = 1_777_624_200_000L // 08:30 UTC
        val medBTime = 1_777_628_700_000L // 09:45 UTC
        every { medicationRepository.observeDosesForDate(any()) } returns
            flowOf(listOf(dose(medA.id, medATime), dose(medB.id, medBTime)))

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        val state = vm.slotTodayStates.value.single()
        assertEquals(medATime, state.takenAtByMedicationId[medA.id])
        assertEquals(medBTime, state.takenAtByMedicationId[medB.id])
    }

    @Test
    fun perMedTakenAt_collapsesDuplicateDosesToTheLatestTakenAt() = runTest(dispatcher) {
        // Toggle-untoggle-retoggle can leave multiple rows for the same
        // (med, slot, date) triple. The inline label should always show
        // the most recent tap, not the earliest one.
        val earlier = 1_777_624_200_000L
        val later = 1_777_628_700_000L
        every { medicationRepository.observeDosesForDate(any()) } returns
            flowOf(listOf(dose(medA.id, earlier), dose(medA.id, later)))

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        val state = vm.slotTodayStates.value.single()
        assertEquals(later, state.takenAtByMedicationId[medA.id])
    }

    @Test
    fun perMedTakenAt_excludesSyntheticSkipDoses() = runTest(dispatcher) {
        // Synthetic-skip rows are scheduling anchors, not "taken" events —
        // they must not surface in the inline time label.
        val skipTime = 1_777_624_200_000L
        every { medicationRepository.observeDosesForDate(any()) } returns
            flowOf(listOf(dose(medA.id, skipTime, isSyntheticSkip = true)))

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        val state = vm.slotTodayStates.value.single()
        assertNull(state.takenAtByMedicationId[medA.id])
    }

    @Test
    fun perMedTakenAt_emptyMapWhenNoDosesExist() = runTest(dispatcher) {
        every { medicationRepository.observeDosesForDate(any()) } returns flowOf(emptyList())

        val vm = newViewModel()
        backgroundScope.warmStateFlows(vm)
        advanceUntilIdle()

        val state = vm.slotTodayStates.value.single()
        assertEquals(emptyMap<Long, Long>(), state.takenAtByMedicationId)
    }
}
