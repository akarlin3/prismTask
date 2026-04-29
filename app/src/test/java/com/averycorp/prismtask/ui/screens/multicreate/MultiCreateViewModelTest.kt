package com.averycorp.prismtask.ui.screens.multicreate

import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.ExtractedTask
import com.averycorp.prismtask.domain.usecase.NaturalLanguageParser
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MultiCreateViewModel] (Phase B / PR-C of the
 * multi-task creation audit). Covers the audit's enumerated VM
 * surface: extract → preview → toggle → create-selected → emit count,
 * plus the empty-selection no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiCreateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var parser: NaturalLanguageParser
    private lateinit var taskRepository: TaskRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var proFeatureGate: ProFeatureGate
    private lateinit var viewModel: MultiCreateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        parser = mockk()
        taskRepository = mockk()
        projectRepository = mockk()
        proFeatureGate = mockk()

        every { proFeatureGate.hasAccess(any()) } returns true
        coEvery { taskRepository.addTask(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1L
        every { projectRepository.getAllProjects() } returns flowOf(emptyList())
        coEvery { projectRepository.addProject(any(), any(), any()) } returns 99L

        viewModel = MultiCreateViewModel(
            parser = parser,
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            proFeatureGate = proFeatureGate
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun extract_populatesCandidates_withSelectedTrueByDefault() = runTest(dispatcher) {
        coEvery { parser.extractFromText(any(), any(), any()) } returns listOf(
            ExtractedTask(title = "Email Bob", confidence = 0.9f),
            ExtractedTask(title = "Call Mary", confidence = 0.8f)
        )

        viewModel.setInput("email Bob today, call Mary tomorrow, write notes")
        viewModel.extract()
        advanceUntilIdle()

        val candidates = viewModel.candidates.value
        assertEquals(2, candidates.size)
        assertEquals("Email Bob", candidates[0].title)
        assertTrue(candidates[0].selected)
        assertTrue(candidates[1].selected)
    }

    @Test
    fun extract_blankInput_doesNotCallParser() = runTest(dispatcher) {
        viewModel.setInput("   ")
        viewModel.extract()
        advanceUntilIdle()

        assertTrue(viewModel.candidates.value.isEmpty())
        coVerify(exactly = 0) { parser.extractFromText(any(), any(), any()) }
    }

    @Test
    fun toggle_flipsSelectedState() = runTest(dispatcher) {
        coEvery { parser.extractFromText(any(), any(), any()) } returns listOf(
            ExtractedTask(title = "A", confidence = 0.5f),
            ExtractedTask(title = "B", confidence = 0.5f)
        )
        viewModel.setInput("a today, b tomorrow, c friday")
        viewModel.extract()
        advanceUntilIdle()

        viewModel.toggle(0)

        assertFalse(viewModel.candidates.value[0].selected)
        assertTrue(viewModel.candidates.value[1].selected)
    }

    @Test
    fun editTitle_updatesTitleInPlace() = runTest(dispatcher) {
        coEvery { parser.extractFromText(any(), any(), any()) } returns listOf(
            ExtractedTask(title = "Original", confidence = 0.5f)
        )
        viewModel.setInput("original today, second tomorrow, third friday")
        viewModel.extract()
        advanceUntilIdle()

        viewModel.editTitle(0, "Renamed")

        assertEquals("Renamed", viewModel.candidates.value[0].title)
    }

    @Test
    fun createSelected_persistsSelectedTasks_and_emitsCount() = runTest(dispatcher) {
        coEvery { parser.extractFromText(any(), any(), any()) } returns listOf(
            ExtractedTask(
                title = "Email Bob",
                confidence = 0.9f,
                suggestedDueDate = 1_700_000_000_000L,
                suggestedPriority = 2,
                suggestedProject = null
            ),
            ExtractedTask(title = "Call Mary", confidence = 0.8f),
            ExtractedTask(title = "Write Notes", confidence = 0.7f)
        )
        viewModel.setInput("email today, call tomorrow, notes friday")
        viewModel.extract()
        advanceUntilIdle()

        // Skip the middle one.
        viewModel.toggle(1)

        viewModel.createSelected()
        advanceUntilIdle()

        assertEquals(2, viewModel.createdCount.value)
        coVerify(exactly = 2) {
            taskRepository.addTask(
                title = any(),
                description = any(),
                dueDate = any(),
                dueTime = any(),
                priority = any(),
                projectId = any(),
                parentTaskId = any(),
                lifeCategory = any(),
                reminderOffset = any(),
                recurrenceRule = any(),
                estimatedDuration = any()
            )
        }
        coVerify(exactly = 1) {
            taskRepository.addTask(title = "Email Bob", priority = 2, dueDate = 1_700_000_000_000L)
        }
    }

    @Test
    fun createSelected_emptySelection_emitsZero_and_noTasks() = runTest(dispatcher) {
        coEvery { parser.extractFromText(any(), any(), any()) } returns listOf(
            ExtractedTask(title = "A", confidence = 0.5f),
            ExtractedTask(title = "B", confidence = 0.5f)
        )
        viewModel.setInput("a today, b tomorrow, c friday")
        viewModel.extract()
        advanceUntilIdle()

        // Deselect everything.
        viewModel.toggle(0)
        viewModel.toggle(1)

        viewModel.createSelected()
        advanceUntilIdle()

        assertEquals(0, viewModel.createdCount.value)
        coVerify(exactly = 0) {
            taskRepository.addTask(
                title = any(),
                description = any(),
                dueDate = any(),
                dueTime = any(),
                priority = any(),
                projectId = any(),
                parentTaskId = any(),
                lifeCategory = any(),
                reminderOffset = any(),
                recurrenceRule = any(),
                estimatedDuration = any()
            )
        }
    }

    @Test
    fun createSelected_resolvesProjectByName_caseInsensitive() = runTest(dispatcher) {
        every { projectRepository.getAllProjects() } returns flowOf(
            listOf(
                ProjectEntity(id = 42L, name = "Work")
            )
        )
        coEvery { parser.extractFromText(any(), any(), any()) } returns listOf(
            ExtractedTask(
                title = "Slide deck",
                confidence = 0.8f,
                suggestedProject = "WORK"
            )
        )
        viewModel.setInput("a today, b tomorrow, c friday")
        viewModel.extract()
        advanceUntilIdle()

        val captured = slot<Long?>()
        coEvery {
            taskRepository.addTask(
                title = any(),
                description = any(),
                dueDate = any(),
                dueTime = any(),
                priority = any(),
                projectId = capture(captured),
                parentTaskId = any(),
                lifeCategory = any(),
                reminderOffset = any(),
                recurrenceRule = any(),
                estimatedDuration = any()
            )
        } returns 7L

        viewModel.createSelected()
        advanceUntilIdle()

        assertEquals(42L, captured.captured)
        // Existing project — should NOT have called addProject.
        coVerify(exactly = 0) { projectRepository.addProject(any(), any(), any()) }
    }

    @Test
    fun reset_clearsState() = runTest(dispatcher) {
        coEvery { parser.extractFromText(any(), any(), any()) } returns listOf(
            ExtractedTask(title = "A", confidence = 0.5f)
        )
        viewModel.setInput("a today, b tomorrow, c friday")
        viewModel.extract()
        advanceUntilIdle()

        viewModel.reset()

        assertEquals("", viewModel.input.value)
        assertTrue(viewModel.candidates.value.isEmpty())
        assertNull(viewModel.createdCount.value)
    }
}
