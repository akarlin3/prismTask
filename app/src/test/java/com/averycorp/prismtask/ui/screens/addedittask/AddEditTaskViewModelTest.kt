package com.averycorp.prismtask.ui.screens.addedittask

import androidx.lifecycle.SavedStateHandle
import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.repository.AttachmentRepository
import com.averycorp.prismtask.data.repository.BoundaryRuleRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskDependencyRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.data.repository.TaskTemplateRepository
import com.averycorp.prismtask.data.repository.TaskTimingRepository
import com.averycorp.prismtask.domain.model.CognitiveLoad
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.domain.model.TaskMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Unit tests for [AddEditTaskViewModel]. All repository collaborators are
 * relaxed-mocked; we test the editor state-machine (title validation,
 * pending subtasks, unsaved changes) and the save path end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditTaskViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var taskRepository: TaskRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var attachmentRepository: AttachmentRepository
    private lateinit var templateRepository: TaskTemplateRepository
    private lateinit var taskTimingRepository: TaskTimingRepository
    private lateinit var taskDependencyRepository: TaskDependencyRepository
    private lateinit var boundaryRuleRepository: BoundaryRuleRepository
    private lateinit var notificationPreferences: NotificationPreferences
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private lateinit var taskBehaviorPreferences: TaskBehaviorPreferences
    private lateinit var advancedTuningPreferences: com.averycorp.prismtask.data.preferences.AdvancedTuningPreferences
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        taskRepository = mockk(relaxed = true)
        projectRepository = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)
        attachmentRepository = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true)
        taskTimingRepository = mockk(relaxed = true)
        coEvery { taskTimingRepository.observeSumMinutesForTask(any()) } returns flowOf(0)
        taskDependencyRepository = mockk(relaxed = true)
        coEvery { taskDependencyRepository.observeBlockersOf(any()) } returns flowOf(emptyList())
        coEvery { taskRepository.getAllTasks() } returns flowOf(emptyList())
        boundaryRuleRepository = mockk(relaxed = true)
        notificationPreferences = mockk(relaxed = true)
        userPreferencesDataStore = mockk(relaxed = true)
        taskBehaviorPreferences = mockk(relaxed = true)
        coEvery { taskBehaviorPreferences.getReminderPresets() } returns flowOf(
            listOf(0L, 900_000L, 1_800_000L, 3_600_000L, 86_400_000L)
        )
        advancedTuningPreferences = mockk(relaxed = true)
        coEvery { advancedTuningPreferences.getEditorFieldRows() } returns
            flowOf(com.averycorp.prismtask.data.preferences.EditorFieldRows())
        // Default-keyword stubs for the auto-classifier wiring. Tests that
        // exercise customization can override these in their own setup.
        coEvery { advancedTuningPreferences.getLifeCategoryCustomKeywords() } returns
            flowOf(com.averycorp.prismtask.data.preferences.LifeCategoryCustomKeywords())
        coEvery { advancedTuningPreferences.getTaskModeCustomKeywords() } returns
            flowOf(com.averycorp.prismtask.data.preferences.TaskModeCustomKeywords())
        coEvery { advancedTuningPreferences.getCognitiveLoadCustomKeywords() } returns
            flowOf(com.averycorp.prismtask.data.preferences.CognitiveLoadCustomKeywords())
        savedStateHandle = SavedStateHandle()

        // Default StateFlow seeds so the VM init doesn't crash on relaxed mocks.
        coEvery { projectRepository.getAllProjects() } returns flowOf(emptyList())
        coEvery { tagRepository.getAllTags() } returns flowOf(emptyList())
        // Default reminder offset = OFFSET_NONE so create-mode tests don't
        // get a surprise pre-fill that flips hasUnsavedChanges semantics.
        coEvery {
            notificationPreferences.getDefaultReminderOffsetOnce()
        } returns NotificationPreferences.OFFSET_NONE
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): AddEditTaskViewModel = AddEditTaskViewModel(
        taskRepository,
        projectRepository,
        tagRepository,
        attachmentRepository,
        templateRepository,
        taskTimingRepository,
        taskDependencyRepository,
        boundaryRuleRepository,
        notificationPreferences,
        userPreferencesDataStore,
        taskBehaviorPreferences,
        advancedTuningPreferences,
        savedStateHandle
    )

    // ---------------------------------------------------------------------
    // Create mode
    // ---------------------------------------------------------------------

    @Test
    fun newViewModel_createMode_hasBlankDefaults() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        assertEquals("", vm.title)
        assertEquals("", vm.description)
        assertNull(vm.dueDate)
        assertEquals(0, vm.priority)
        assertFalse(vm.isEditMode)
    }

    @Test
    fun initialize_withProjectAndDate_seedsThoseFields() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = 7L, initialDate = 1_700_000_000_000L)
        assertEquals(7L, vm.projectId)
        assertEquals(1_700_000_000_000L, vm.dueDate)
    }

    // ---------------------------------------------------------------------
    // Field mutations
    // ---------------------------------------------------------------------

    @Test
    fun onTitleChange_updatesTitleAndClearsError() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onTitleChange("")
        // Trigger validation via save.
        runBlocking { vm.saveTask() }
        assertTrue("Blank title should set error", vm.titleError)

        vm.onTitleChange("Not blank")
        assertFalse("Setting a non-blank title should clear the error", vm.titleError)
    }

    @Test
    fun onPriorityChange_updatesPriority() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onPriorityChange(4)
        assertEquals(4, vm.priority)
    }

    @Test
    fun hasUnsavedChanges_trueOnlyAfterEditing() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        assertFalse(vm.hasUnsavedChanges)
        vm.onTitleChange("Draft")
        assertTrue(vm.hasUnsavedChanges)
    }

    // ---------------------------------------------------------------------
    // Pending subtasks
    // ---------------------------------------------------------------------

    @Test
    fun addPendingSubtask_appendsWithGeneratedId() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        val id = vm.addPendingSubtask("First")
        assertTrue(id > 0L)
        assertEquals(1, vm.pendingSubtasks.size)
        assertEquals("First", vm.pendingSubtasks.single().title)
    }

    @Test
    fun addPendingSubtask_blankTitleIsDropped() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        val id = vm.addPendingSubtask("   ")
        assertEquals(-1L, id)
        assertTrue(vm.pendingSubtasks.isEmpty())
    }

    @Test
    fun togglePendingSubtask_flipsCompletionFlag() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        val id = vm.addPendingSubtask("Todo")
        assertFalse(vm.pendingSubtasks.single().isCompleted)
        vm.togglePendingSubtask(id)
        assertTrue(vm.pendingSubtasks.single().isCompleted)
    }

    @Test
    fun removePendingSubtask_removesById() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        val idA = vm.addPendingSubtask("A")
        vm.addPendingSubtask("B")
        vm.removePendingSubtask(idA)
        assertEquals(1, vm.pendingSubtasks.size)
        assertEquals("B", vm.pendingSubtasks.single().title)
    }

    // ---------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------

    @Test
    fun saveTask_blankTitleFailsAndFlagsError() = runTest {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onTitleChange("")
        val ok = vm.saveTask()
        assertFalse(ok)
        assertTrue(vm.titleError)
        coVerify(exactly = 0) {
            taskRepository.addTask(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun saveTask_createMode_invokesRepositoryWithFields() = runTest {
        coEvery {
            taskRepository.addTask(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns 55L
        coEvery { taskRepository.getTaskById(55L) } returns flowOf(
            TaskEntity(id = 55L, title = "Bake bread")
        )

        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onTitleChange("Bake bread")
        vm.onPriorityChange(2)
        vm.onDueDateChange(1_700_000_000_000L)

        val ok = vm.saveTask()
        assertTrue(ok)
        coVerify {
            taskRepository.addTask(
                title = "Bake bread",
                description = null,
                dueDate = 1_700_000_000_000L,
                dueTime = null,
                priority = 2,
                projectId = null,
                parentTaskId = null,
                lifeCategory = any(),
                taskMode = any(),
                cognitiveLoad = any(),
                reminderOffset = any(),
                recurrenceRule = any(),
                estimatedDuration = any()
            )
        }
    }

    // ---------------------------------------------------------------------
    // Logged time (P2-B)
    // ---------------------------------------------------------------------

    @Test
    fun `logTime is no-op in create mode (no taskId yet)`() = runTest {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)

        vm.logTime(15)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            taskTimingRepository.logTime(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `logTime forwards minutes to repository in edit mode`() = runTest {
        coEvery { taskRepository.getTaskById(7L) } returns flowOf(
            TaskEntity(id = 7L, title = "Refactor parser")
        )
        val vm = newViewModel()
        vm.initialize(taskId = 7L, projectId = null, initialDate = null)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.logTime(25)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            taskTimingRepository.logTime(taskId = 7L, durationMinutes = 25)
        }
    }

    @Test
    fun `logTime ignores non-positive minutes without hitting repository`() = runTest {
        coEvery { taskRepository.getTaskById(7L) } returns flowOf(
            TaskEntity(id = 7L, title = "Whatever")
        )
        val vm = newViewModel()
        vm.initialize(taskId = 7L, projectId = null, initialDate = null)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.logTime(0)
        vm.logTime(-10)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            taskTimingRepository.logTime(any(), any(), any(), any(), any(), any())
        }
    }

    // ---------------------------------------------------------------------
    // Blocker management (F.5 follow-on — task-editor dependency UI)
    // ---------------------------------------------------------------------

    @Test
    fun `addBlocker in edit mode forwards to repository`() = runTest {
        coEvery { taskRepository.getTaskById(7L) } returns flowOf(
            TaskEntity(id = 7L, title = "Blocked task")
        )
        coEvery { taskDependencyRepository.addDependency(99L, 7L) } returns Result.success(123L)

        val vm = newViewModel()
        vm.initialize(taskId = 7L, projectId = null, initialDate = null)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addBlocker(blockerTaskId = 99L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            taskDependencyRepository.addDependency(blockerTaskId = 99L, blockedTaskId = 7L)
        }
    }

    @Test
    fun `addBlocker in create mode emits error and does not hit repository`() = runTest {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)

        vm.addBlocker(blockerTaskId = 42L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            taskDependencyRepository.addDependency(any(), any())
        }
    }

    @Test
    fun `addBlocker rejects self as blocker`() = runTest {
        coEvery { taskRepository.getTaskById(7L) } returns flowOf(
            TaskEntity(id = 7L, title = "Self")
        )
        val vm = newViewModel()
        vm.initialize(taskId = 7L, projectId = null, initialDate = null)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addBlocker(blockerTaskId = 7L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            taskDependencyRepository.addDependency(any(), any())
        }
    }

    @Test
    fun `addBlocker surfaces cycle rejection from repository`() = runTest {
        coEvery { taskRepository.getTaskById(7L) } returns flowOf(
            TaskEntity(id = 7L, title = "Blocked task")
        )
        coEvery { taskDependencyRepository.addDependency(99L, 7L) } returns Result.failure(
            TaskDependencyRepository.DependencyError.CycleRejected(99L, 7L)
        )

        val vm = newViewModel()
        vm.initialize(taskId = 7L, projectId = null, initialDate = null)
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = mutableListOf<String>()
        // Collect on testDispatcher so the subscriber and the VM's emitter share
        // the same TestScheduler — backgroundScope on runTest's TestScope uses a
        // different scheduler, so testDispatcher.scheduler.advanceUntilIdle()
        // would not start the collector before the SharedFlow (no replay) emit.
        val collectJob = launch(testDispatcher) { vm.errorMessages.collect { errors.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addBlocker(blockerTaskId = 99L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "cycle rejection should surface as a user-facing error",
            errors.any { it.contains("cycle", ignoreCase = true) }
        )
        collectJob.cancel()
    }

    // ---------------------------------------------------------------------
    // Auto-pick (classifier-backed chips on Organize tab)
    // ---------------------------------------------------------------------

    @Test
    fun `autoPickLifeCategory picks a chip from the title`() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onTitleChange("Email project status update to manager")
        vm.autoPickLifeCategory()
        assertEquals(LifeCategory.WORK, vm.lifeCategory)
        // Auto-press must NOT flip manuallySet — the boundary-suggestion gate
        // and subsequent re-picks both depend on it staying false.
        assertFalse(vm.lifeCategoryManuallySet)
    }

    @Test
    fun `autoPickLifeCategory leaves chip empty when title is blank`() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.autoPickLifeCategory()
        assertNull(vm.lifeCategory)
        assertFalse(vm.lifeCategoryManuallySet)
    }

    @Test
    fun `autoPickLifeCategory respects a prior manual pick`() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onLifeCategoryChange(LifeCategory.PERSONAL)
        assertTrue(vm.lifeCategoryManuallySet)

        vm.onTitleChange("Email project status update to manager")
        vm.autoPickLifeCategory()
        // Manual pick wins over auto re-press.
        assertEquals(LifeCategory.PERSONAL, vm.lifeCategory)
        assertTrue(vm.lifeCategoryManuallySet)
    }

    @Test
    fun `autoPickLifeCategory with force overrides a manual pick`() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onLifeCategoryChange(LifeCategory.PERSONAL)
        vm.onTitleChange("Email project status update to manager")

        vm.autoPickLifeCategory(force = true)
        // Force resets manualSet and re-picks from the classifier.
        assertEquals(LifeCategory.WORK, vm.lifeCategory)
        assertFalse(vm.lifeCategoryManuallySet)
    }

    @Test
    fun `autoPickTaskMode picks a chip from the title`() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onTitleChange("Play board games with friends")
        vm.autoPickTaskMode()
        assertEquals(TaskMode.PLAY, vm.taskMode)
        assertFalse(vm.taskModeManuallySet)
    }

    @Test
    fun `autoPickCognitiveLoad picks a chip from the title`() {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onTitleChange("Read research paper")
        vm.autoPickCognitiveLoad()
        assertEquals(CognitiveLoad.HARD, vm.cognitiveLoad)
        assertFalse(vm.cognitiveLoadManuallySet)
    }

    @Test
    fun `resolveLifeCategoryForSave persists displayed auto-pick`() = runTest {
        val vm = newViewModel()
        vm.initialize(taskId = null, projectId = null, initialDate = null)
        vm.onTitleChange("Email project status update to manager")
        vm.autoPickLifeCategory()

        // Auto-pick produces a value; save path must persist exactly that.
        assertEquals(LifeCategory.WORK.name, vm.resolveLifeCategoryForSave())
    }

    @Test
    fun `removeBlocker forwards edge id to repository`() = runTest {
        coEvery { taskRepository.getTaskById(7L) } returns flowOf(
            TaskEntity(id = 7L, title = "Blocked task")
        )
        val vm = newViewModel()
        vm.initialize(taskId = 7L, projectId = null, initialDate = null)
        testDispatcher.scheduler.advanceUntilIdle()

        val edge = TaskDependencyEntity(
            id = 333L,
            blockerTaskId = 99L,
            blockedTaskId = 7L,
            createdAt = 0L
        )
        vm.removeBlocker(edge)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { taskDependencyRepository.removeById(333L) }
    }
}
