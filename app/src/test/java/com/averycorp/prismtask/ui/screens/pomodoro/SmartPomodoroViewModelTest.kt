package com.averycorp.prismtask.ui.screens.pomodoro

import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.remote.api.PomodoroResponse
import com.averycorp.prismtask.data.remote.api.PomodoroSessionResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.SessionTaskResponse
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SmartPomodoroViewModel]. Validates the state-machine flow
 * between PLANNING / SESSION_ACTIVE / COMPLETE, the Pro feature gate, and
 * config mutation helpers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartPomodoroViewModelTest {

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
        every { proFeatureGate.userTier } returns MutableStateFlow(UserTier.PRO)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = SmartPomodoroViewModel(taskDao, api, proFeatureGate)

    @Test
    fun initialState_isPlanning() {
        val vm = newViewModel()
        assertEquals(PomodoroState.PLANNING, vm.screenState.value)
    }

    @Test
    fun updateAvailableMinutes_mutatesConfig() {
        val vm = newViewModel()
        vm.updateAvailableMinutes(90)
        assertEquals(90, vm.config.value.availableMinutes)
    }

    @Test
    fun updateSessionLength_mutatesConfig() {
        val vm = newViewModel()
        vm.updateSessionLength(45)
        assertEquals(45, vm.config.value.sessionLength)
    }

    @Test
    fun updateFocusPreference_mutatesConfig() {
        val vm = newViewModel()
        vm.updateFocusPreference("deep_work")
        assertEquals("deep_work", vm.config.value.focusPreference)
    }

    @Test
    fun generatePlan_freeTierShowsUpgradePrompt() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_POMODORO) } returns false
        val vm = newViewModel()

        vm.generatePlan()
        advanceUntilIdle()

        assertTrue(vm.showUpgradePrompt.value)
        assertNull(vm.plan.value)
        coVerify(exactly = 0) { api.planPomodoro(any()) }
    }

    @Test
    fun generatePlan_proTierCallsApiAndStoresPlan() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_POMODORO) } returns true
        coEvery { api.planPomodoro(any()) } returns PomodoroResponse(
            sessions = listOf(
                PomodoroSessionResponse(
                    sessionNumber = 1,
                    tasks = listOf(SessionTaskResponse(taskId = 1L, title = "Focus", allocatedMinutes = 25)),
                    rationale = "Warm up"
                )
            ),
            totalSessions = 1,
            totalWorkMinutes = 25,
            totalBreakMinutes = 5,
            skippedTasks = emptyList()
        )

        val vm = newViewModel()
        vm.generatePlan()
        advanceUntilIdle()

        val plan = vm.plan.value
        assertNotNull(plan)
        assertEquals(1, plan!!.sessions.size)
        assertEquals(25, plan.totalWorkMinutes)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun generatePlan_apiFailureSurfacesError() = runTest(dispatcher) {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_POMODORO) } returns true
        coEvery { api.planPomodoro(any()) } throws RuntimeException("upstream down")

        val vm = newViewModel()
        vm.generatePlan()
        advanceUntilIdle()

        assertFalse(vm.isLoading.value)
        assertEquals("upstream down", vm.error.value)
    }

    @Test
    fun startSession_transitionsToActiveStateWithFullTimer() {
        val vm = newViewModel()
        vm.updateSessionLength(25)
        vm.startSession()

        assertEquals(PomodoroState.SESSION_ACTIVE, vm.screenState.value)
        assertEquals(25 * 60, vm.timerSecondsRemaining.value)
        assertTrue(vm.isTimerRunning.value)
    }

    @Test
    fun pauseTimer_stopsTheTimer() {
        val vm = newViewModel()
        vm.startSession()
        vm.pauseTimer()
        assertFalse(vm.isTimerRunning.value)
    }

    @Test
    fun completeTask_delegatesToDaoAndMarksIdComplete() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.completeTask(42L)
        advanceUntilIdle()

        coVerify { taskDao.markCompleted(42L, any()) }
        assertTrue(42L in vm.completedTaskIds.value)
    }

    @Test
    fun resetToPlanning_clearsPlanAndStats() {
        val vm = newViewModel()
        vm.startSession()
        vm.resetToPlanning()

        assertEquals(PomodoroState.PLANNING, vm.screenState.value)
        assertNull(vm.plan.value)
        assertTrue(vm.completedTaskIds.value.isEmpty())
    }
}
