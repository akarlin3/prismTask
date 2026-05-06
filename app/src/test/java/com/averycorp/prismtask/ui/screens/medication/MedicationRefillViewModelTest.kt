package com.averycorp.prismtask.ui.screens.medication

import app.cash.turbine.test
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.repository.MedicationRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for F.5b — `MedicationRefillViewModel` previously
 * launched its writes inside a bare `viewModelScope.launch` with no
 * `try/catch`. While today's pre-flight `getByNameOnce` exhausts the
 * known unique-name crash class on `addMedication`, any future
 * exception source from `repository.update` / `insert` would crash the
 * app. These tests pin the post-fix invariant: repository throws →
 * `errorMessages` emits → no crash. See
 * `docs/audits/F5_MEDICATION_HYGIENE_FOLLOWONS_AUDIT.md` § B.2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationRefillViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var repository: MedicationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.observeActive() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addMedication_repositoryThrows_emitsErrorAndDoesNotCrash() = runTest(dispatcher) {
        coEvery { repository.getByNameOnce("Lamotrigine") } returns null
        coEvery { repository.insert(any()) } throws IllegalStateException("boom")
        val viewModel = MedicationRefillViewModel(repository)

        viewModel.errorMessages.test {
            viewModel.addMedication(
                name = "Lamotrigine",
                pillCount = 30,
                pillsPerDose = 1,
                dosesPerDay = 1
            )
            advanceUntilIdle()
            assertEquals("Couldn't save medication. Please try again.", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recordDailyDose_repositoryThrows_emitsErrorAndDoesNotCrash() = runTest(dispatcher) {
        coEvery { repository.update(any()) } throws RuntimeException("write failed")
        val viewModel = MedicationRefillViewModel(repository)

        viewModel.errorMessages.test {
            viewModel.recordDailyDose(
                MedicationEntity(
                    id = 1L,
                    name = "Lamotrigine",
                    pillCount = 30,
                    pillsPerDose = 1,
                    dosesPerDay = 1
                )
            )
            advanceUntilIdle()
            assertEquals("Couldn't record dose. Please try again.", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recordRefill_repositoryThrows_emitsErrorAndDoesNotCrash() = runTest(dispatcher) {
        coEvery { repository.update(any()) } throws RuntimeException("write failed")
        val viewModel = MedicationRefillViewModel(repository)

        viewModel.errorMessages.test {
            viewModel.recordRefill(
                MedicationEntity(
                    id = 1L,
                    name = "Lamotrigine",
                    pillCount = 0,
                    pillsPerDose = 1,
                    dosesPerDay = 1
                ),
                newSupply = 60
            )
            advanceUntilIdle()
            assertEquals("Couldn't record refill. Please try again.", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun disableRefillTracking_repositoryThrows_emitsErrorAndDoesNotCrash() = runTest(dispatcher) {
        coEvery { repository.getByIdOnce(1L) } returns
            MedicationEntity(id = 1L, name = "Lamotrigine", pillCount = 30)
        coEvery { repository.update(any()) } throws RuntimeException("write failed")
        val viewModel = MedicationRefillViewModel(repository)

        viewModel.errorMessages.test {
            viewModel.disableRefillTracking(1L)
            advanceUntilIdle()
            assertEquals("Couldn't update tracking. Please try again.", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
