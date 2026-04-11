package com.averycorp.prismtask.ui.screens.tasklist

import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.preferences.SwipePrefs
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.UrgencyWeights
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.repository.AttachmentRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.TodoListParser
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TaskListViewModel] focused on the state-machine around
 * multi-select, sort changes, and toggled flags / archive actions. The
 * large reactive flow graph is stubbed with empty flows so the VM
 * constructs without blocking on any particular data source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var taskRepository: TaskRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var attachmentRepository: AttachmentRepository
    private lateinit var todoListParser: TodoListParser
    private lateinit var taskBehaviorPreferences: TaskBehaviorPreferences
    private lateinit var sortPreferences: SortPreferences
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskRepository = mockk(relaxed = true)
        projectRepository = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)
        attachmentRepository = mockk(relaxed = true)
        todoListParser = mockk(relaxed = true)
        taskBehaviorPreferences = mockk(relaxed = true)
        sortPreferences = mockk(relaxed = true)
        userPreferencesDataStore = mockk(relaxed = true)

        coEvery { taskRepository.getIncompleteRootTasks() } returns flowOf(emptyList())
        coEvery { taskRepository.getAllTasks() } returns flowOf(emptyList())
        coEvery { taskRepository.getSubtasks(any()) } returns flowOf(emptyList())
        coEvery { projectRepository.getAllProjects() } returns flowOf(emptyList())
        coEvery { tagRepository.getAllTags() } returns flowOf(emptyList())
        coEvery { tagRepository.getTagsForTask(any()) } returns flowOf(emptyList())
        coEvery { attachmentRepository.getAttachmentCount(any()) } returns flowOf(0)
        coEvery { taskBehaviorPreferences.getDayStartHour() } returns flowOf(0)
        coEvery { taskBehaviorPreferences.getDefaultSort() } returns flowOf("DUE_DATE")
        coEvery { taskBehaviorPreferences.getDefaultViewMode() } returns flowOf("UPCOMING")
        coEvery { taskBehaviorPreferences.getUrgencyWeights() } returns flowOf(UrgencyWeights())
        coEvery { sortPreferences.observeSortMode(any()) } returns flowOf(SortPreferences.SortModes.DUE_DATE)
        coEvery { sortPreferences.getSortModeOrNull(any()) } returns null
        coEvery { userPreferencesDataStore.swipeFlow } returns flowOf(SwipePrefs())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = TaskListViewModel(
        taskRepository,
        projectRepository,
        tagRepository,
        attachmentRepository,
        todoListParser,
        taskBehaviorPreferences,
        sortPreferences,
        userPreferencesDataStore
    )

    @Test
    fun viewModel_constructsAndLoads() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        // isLoading transitions to false after the first emission of root tasks.
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun onToggleFlag_delegatesToRepository() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onToggleFlag(42L)
        advanceUntilIdle()
        coVerify { taskRepository.toggleFlag(42L) }
    }

    @Test
    fun onArchiveTask_delegatesToRepository() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onArchiveTask(42L)
        advanceUntilIdle()
        coVerify { taskRepository.archiveTask(42L) }
    }

    @Test
    fun onEnterMultiSelect_setsModeWithInitialTaskSelected() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onEnterMultiSelect(7L)
        assertTrue(vm.isMultiSelectMode.value)
        assertEquals(setOf(7L), vm.selectedTaskIds.value)
    }

    @Test
    fun onToggleTaskSelection_addsAndRemovesIds() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onEnterMultiSelect(1L)
        vm.onToggleTaskSelection(2L)
        vm.onToggleTaskSelection(3L)
        assertEquals(setOf(1L, 2L, 3L), vm.selectedTaskIds.value)

        // Toggling an already-selected id removes it.
        vm.onToggleTaskSelection(2L)
        assertEquals(setOf(1L, 3L), vm.selectedTaskIds.value)
    }

    @Test
    fun onExitMultiSelect_clearsModeAndSelection() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onEnterMultiSelect(1L)
        vm.onToggleTaskSelection(2L)
        vm.onExitMultiSelect()

        assertFalse(vm.isMultiSelectMode.value)
        assertTrue(vm.selectedTaskIds.value.isEmpty())
    }

    @Test
    fun onDeselectAll_clearsSelectionWithoutLeavingMultiSelect() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onEnterMultiSelect(1L)
        vm.onToggleTaskSelection(2L)
        vm.onDeselectAll()

        assertTrue(vm.selectedTaskIds.value.isEmpty())
        assertTrue(
            "Deselect all should keep multi-select mode active",
            vm.isMultiSelectMode.value
        )
    }
}
