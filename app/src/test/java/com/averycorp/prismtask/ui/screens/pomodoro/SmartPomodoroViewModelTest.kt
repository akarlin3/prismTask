package com.averycorp.prismtask.ui.screens.pomodoro

import android.content.Context
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.preferences.TimerPreferences
import com.averycorp.prismtask.data.remote.api.PomodoroResponse
import com.averycorp.prismtask.data.remote.api.PomodoroSessionResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.SessionTaskResponse
import com.averycorp.prismtask.data.repository.MoodEnergyRepository
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

    private lateinit var appContext: Context
    private lateinit var taskDao: TaskDao
    private lateinit var api: PrismTaskApi
    private lateinit var proFeatureGate: ProFeatureGate
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var moodEnergyRepository: MoodEnergyRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        appContext = mockk(relaxed = true)
        taskDao = mockk(relaxed = true)
        api = mockk(relaxed = true)
        proFeatureGate = mockk(relaxed = true)
        every { proFeatureGate.userTier } returns MutableStateFlow(UserTier.PRO)
        timerPreferences = mockk(relaxed = true)
        every { timerPreferences.getPomodoroAvailableMinutes() } returns flowOf(120)
        every { timerPreferences.getWorkDurationSeconds() } returns flowOf(25 * 60)
        every { timerPreferences.getBreakDurationSeconds() } returns flowOf(5 * 60)
        every { timerPreferences.getLongBreakDurationSeconds() } returns flowOf(15 * 60)
        every { timerPreferences.getPomodoroFocusPreference() } returns flowOf("balanced")
        moodEnergyRepository = mockk(relaxed = true)
        coEvery { moodEnergyRepository.getRange(any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = SmartPomodoroViewModel(
        appContext,
        taskDao,
        api,
        proFeatureGate,
        moodEnergyRepository,
        timerPreferences
    )

    @Test
    fun initialState_isPlanning() {
        val vm = newViewModel()
        assertEquals(PomodoroState.PLANNING, vm.screenState.value)
    }

    @Test
    fun config_reflectsTimerPreferences() = runTest(dispatcher) {
        every { timerPreferences.getPomodoroAvailableMinutes() } returns flowOf(90)
        every { timerPreferences.getWorkDurationSeconds() } returns flowOf(45 * 60)
        every { timerPreferences.getPomodoroFocusPreference() } returns flowOf("deep_work")

        val vm = newViewModel()
        // Start a subscriber so the WhileSubscribed upstream activates.
        val job = launch { vm.config.collect {} }
        advanceUntilIdle()

        assertEquals(90, vm.config.value.availableMinutes)
        assertEquals(45, vm.config.value.sessionLength)
        assertEquals("deep_work", vm.config.value.focusPreference)

        job.cancel()
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
    fun startSession_transitionsToActiveStateWithFullTimer() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
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
