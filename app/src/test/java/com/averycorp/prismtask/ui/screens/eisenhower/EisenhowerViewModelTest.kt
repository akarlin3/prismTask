package com.averycorp.prismtask.ui.screens.eisenhower

import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.remote.api.EisenhowerCategorization
import com.averycorp.prismtask.data.remote.api.EisenhowerResponse
import com.averycorp.prismtask.data.remote.api.EisenhowerSummary
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EisenhowerViewModel]. Covers the Pro feature gate,
 * manual quadrant moves, completion, and AI categorize-task wiring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EisenhowerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var taskDao: TaskDao
    private lateinit var api: PrismTaskApi
    private lateinit var proFeatureGate: ProFeatureGate

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskDao = mockk(relaxed = true)
        api = mockk(relaxed = true)
        proFeatureGate = mockk(relaxed = true)

        coEvery { taskDao.getIncompleteRootTasks() } returns flowOf(emptyList())
        every { proFeatureGate.userTier } returns MutableStateFlow(UserTier.FREE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = EisenhowerViewModel(taskDao, api, proFeatureGate)

    @Test
    fun categorize_freeTierShowsUpgradePromptAndSkipsApiCall() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_EISENHOWER) } returns false

        val vm = newViewModel()
        advanceUntilIdle()

        vm.categorize()
        advanceUntilIdle()

        assertTrue(vm.showUpgradePrompt.value)
        coVerify(exactly = 0) { api.categorizeEisenhower(any()) }
    }

    @Test
    fun categorize_proTierCallsApiAndUpdatesTaskQuadrants() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_EISENHOWER) } returns true
        coEvery { api.categorizeEisenhower(any()) } returns EisenhowerResponse(
            categorizations = listOf(
                EisenhowerCategorization(taskId = 1L, quadrant = "Q1", reason = "Due today"),
                EisenhowerCategorization(taskId = 2L, quadrant = "Q2", reason = "Important")
            ),
            summary = EisenhowerSummary()
        )

        val vm = newViewModel()
        advanceUntilIdle()

        vm.categorize()
        advanceUntilIdle()

        coVerify {
            taskDao.updateEisenhowerQuadrant(id = 1L, quadrant = "Q1", reason = "Due today", updatedAt = any())
        }
        coVerify {
            taskDao.updateEisenhowerQuadrant(id = 2L, quadrant = "Q2", reason = "Important", updatedAt = any())
        }
        assertFalse(vm.isLoading.value)
        assertNull(vm.error.value)
    }

    @Test
    fun categorize_apiFailurePopulatesErrorAndClearsLoading() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_EISENHOWER) } returns true
        coEvery { api.categorizeEisenhower(any()) } throws RuntimeException("network down")

        val vm = newViewModel()
        advanceUntilIdle()

        vm.categorize()
        advanceUntilIdle()

        assertFalse(vm.isLoading.value)
        assertEquals("network down", vm.error.value)
    }

    @Test
    fun moveTaskToQuadrant_updatesEisenhowerQuadrantWithManualReason() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.moveTaskToQuadrant(taskId = 42L, quadrant = "Q3")
        advanceUntilIdle()

        coVerify {
            taskDao.updateEisenhowerQuadrant(
                id = 42L,
                quadrant = "Q3",
                reason = "Manually moved",
                updatedAt = any()
            )
        }
    }

    @Test
    fun completeTask_marksDaoCompleted() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.completeTask(42L)
        advanceUntilIdle()

        coVerify { taskDao.markCompleted(42L, any()) }
    }

    @Test
    fun expandQuadrant_updatesStateFlow() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.expandQuadrant("Q1")
        assertEquals("Q1", vm.expandedQuadrant.value)

        vm.expandQuadrant(null)
        assertNull(vm.expandedQuadrant.value)
    }

    @Test
    fun clearError_resetsErrorToNull() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_EISENHOWER) } returns true
        coEvery { api.categorizeEisenhower(any()) } throws RuntimeException("boom")

        val vm = newViewModel()
        advanceUntilIdle()
        vm.categorize()
        advanceUntilIdle()
        assertEquals("boom", vm.error.value)

        vm.clearError()
        assertNull(vm.error.value)
    }

    @Test
    fun dismissUpgradePrompt_clearsFlag() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_EISENHOWER) } returns false

        val vm = newViewModel()
        advanceUntilIdle()
        vm.categorize()
        advanceUntilIdle()
        assertTrue(vm.showUpgradePrompt.value)

        vm.dismissUpgradePrompt()
        assertFalse(vm.showUpgradePrompt.value)
    }
}
