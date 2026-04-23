package com.averycorp.prismtask.ui.screens.timeline

import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.remote.api.ScheduleBlockResponse
import com.averycorp.prismtask.data.remote.api.TimeBlockResponse
import com.averycorp.prismtask.data.remote.api.TimeBlockStatsResponse
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.AiTimeBlockUseCase
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import com.averycorp.prismtask.domain.usecase.TimeBlockHorizon
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TimelineViewModel] focused on the v1.4.40 Auto-Block My Day
 * flow: horizon selection, preview sheet gating, commit-on-approve, and the
 * cancel path. Legacy drag-and-drop / unscheduled interactions are covered by
 * existing UI tests and deliberately excluded here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var taskDao: TaskDao
    private lateinit var taskRepository: TaskRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var sortPreferences: SortPreferences
    private lateinit var aiTimeBlockUseCase: AiTimeBlockUseCase
    private lateinit var proFeatureGate: ProFeatureGate

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskDao = mockk(relaxed = true)
        every { taskDao.getIncompleteRootTasks() } returns flowOf(emptyList())
        every { taskDao.getTasksDueOnDate(any(), any()) } returns flowOf(emptyList())
        taskRepository = mockk(relaxed = true)
        projectRepository = mockk(relaxed = true)
        every { projectRepository.getAllProjects() } returns flowOf(emptyList())
        sortPreferences = mockk(relaxed = true)
        every {
            sortPreferences.observeSortMode(any())
        } returns flowOf(SortPreferences.SortModes.DEFAULT)
        aiTimeBlockUseCase = mockk()
        proFeatureGate = mockk(relaxed = true)
        every { proFeatureGate.userTier } returns MutableStateFlow(UserTier.PRO)
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_TIME_BLOCK) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = TimelineViewModel(
        taskDao,
        taskRepository,
        projectRepository,
        sortPreferences,
        aiTimeBlockUseCase,
        proFeatureGate
    )

    @Test
    fun selectHorizon_updatesState() {
        val vm = newViewModel()
        assertEquals(TimeBlockHorizon.TODAY, vm.selectedHorizon.value)
        vm.selectHorizon(TimeBlockHorizon.WEEK)
        assertEquals(TimeBlockHorizon.WEEK, vm.selectedHorizon.value)
    }

    @Test
    fun showAutoBlockMyDaySheet_gated_by_pro() {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_TIME_BLOCK) } returns false
        val vm = newViewModel()
        vm.showAutoBlockMyDaySheet()
        assertFalse(vm.showHorizonSheet.value)
        assertTrue(vm.showUpgradePrompt.value)
    }

    @Test
    fun runAutoBlockMyDay_success_opensPreview_without_writing() = runTest(dispatcher) {
        val response = TimeBlockResponse(
            schedule = listOf(
                ScheduleBlockResponse(
                    start = "09:00",
                    end = "09:30",
                    type = "task",
                    taskId = 42L,
                    title = "Write design doc",
                    reason = "Q2 morning slot",
                    date = "2026-04-22"
                )
            ),
            unscheduledTasks = emptyList(),
            stats = TimeBlockStatsResponse(30, 0, 510, 1, 0),
            proposed = true,
            horizonDays = 1
        )
        coEvery {
            aiTimeBlockUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns response

        val vm = newViewModel()
        vm.selectHorizon(TimeBlockHorizon.TODAY)
        vm.runAutoBlockMyDay()
        advanceUntilIdle()

        // Preview sheet is open; Room writes have NOT happened yet.
        assertTrue(vm.showPreviewSheet.value)
        val success = vm.scheduleUiState.value as AiScheduleUiState.Success
        assertEquals(1, success.schedule.blocks.size)
        assertEquals("2026-04-22", success.schedule.blocks[0].date)
        coVerify(exactly = 0) { taskDao.update(any()) }
    }

    @Test
    fun commitProposedSchedule_writes_each_task_block() = runTest(dispatcher) {
        val response = TimeBlockResponse(
            schedule = listOf(
                ScheduleBlockResponse(
                    start = "09:00",
                    end = "10:00",
                    type = "task",
                    taskId = 7L,
                    title = "Deep work",
                    reason = "Morning focus",
                    date = "2026-04-22"
                ),
                ScheduleBlockResponse(
                    start = "10:15",
                    end = "10:30",
                    type = "break",
                    taskId = null,
                    title = "Break",
                    reason = "Recovery",
                    date = "2026-04-22"
                )
            ),
            unscheduledTasks = emptyList(),
            stats = TimeBlockStatsResponse(60, 15, 435, 1, 0),
            proposed = true,
            horizonDays = 1
        )
        coEvery {
            aiTimeBlockUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns response

        val existingTask = TaskEntity(id = 7L, title = "Deep work")
        coEvery { taskDao.getTaskByIdOnce(7L) } returns existingTask
        val updateSlot = slot<TaskEntity>()
        coEvery { taskDao.update(capture(updateSlot)) } just Runs

        val vm = newViewModel()
        vm.runAutoBlockMyDay()
        advanceUntilIdle()
        vm.commitProposedSchedule()
        advanceUntilIdle()

        // Exactly one task row written (the break has no taskId).
        coVerify(exactly = 1) { taskDao.update(any()) }
        val saved = updateSlot.captured
        assertEquals(60, saved.estimatedDuration)
        // scheduled_start_time should be a non-null epoch millis pointing at
        // 09:00 on 2026-04-22 in the system zone.
        assertTrue((saved.scheduledStartTime ?: 0L) > 0L)
        // Preview sheet closed on commit.
        assertFalse(vm.showPreviewSheet.value)
    }

    @Test
    fun cancelProposedSchedule_does_not_write() = runTest(dispatcher) {
        val response = TimeBlockResponse(
            schedule = listOf(
                ScheduleBlockResponse(
                    start = "09:00",
                    end = "09:30",
                    type = "task",
                    taskId = 42L,
                    title = "Write",
                    reason = "...",
                    date = "2026-04-22"
                )
            ),
            unscheduledTasks = emptyList(),
            stats = TimeBlockStatsResponse(30, 0, 510, 1, 0),
            proposed = true,
            horizonDays = 1
        )
        coEvery {
            aiTimeBlockUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns response

        val vm = newViewModel()
        vm.runAutoBlockMyDay()
        advanceUntilIdle()
        vm.cancelProposedSchedule()
        advanceUntilIdle()

        coVerify(exactly = 0) { taskDao.update(any()) }
        assertFalse(vm.showPreviewSheet.value)
        assertEquals(AiScheduleUiState.Idle, vm.scheduleUiState.value)
    }

    @Test
    fun runAutoBlockMyDay_emptyResult_emitsEmptyState() = runTest(dispatcher) {
        val response = TimeBlockResponse(
            schedule = emptyList(),
            unscheduledTasks = emptyList(),
            stats = TimeBlockStatsResponse(0, 0, 0, 0, 0),
            proposed = true,
            horizonDays = 1
        )
        coEvery {
            aiTimeBlockUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns response

        val vm = newViewModel()
        vm.runAutoBlockMyDay()
        advanceUntilIdle()

        assertTrue(vm.scheduleUiState.value is AiScheduleUiState.Empty)
        assertFalse(vm.showPreviewSheet.value)
    }

    @Test
    fun runAutoBlockMyDay_error_surfaces_user_facing_message() = runTest(dispatcher) {
        coEvery {
            aiTimeBlockUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("boom")

        val vm = newViewModel()
        vm.runAutoBlockMyDay()
        advanceUntilIdle()

        val err = vm.scheduleUiState.value as AiScheduleUiState.Error
        // Non-HTTP errors fall through to the generic "can't reach" bucket.
        assertEquals("Couldn't reach the scheduling service. Check your connection.", err.message)
        assertFalse(vm.showPreviewSheet.value)
    }
}
