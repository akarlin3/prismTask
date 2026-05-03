package com.averycorp.prismtask.ui.screens.addedittask

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.converter.RecurrenceConverter
import com.averycorp.prismtask.data.local.entity.AttachmentEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.AttachmentRepository
import com.averycorp.prismtask.data.repository.BoundaryRuleRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.data.repository.TaskTemplateRepository
import com.averycorp.prismtask.data.repository.TaskTimingRepository
import com.averycorp.prismtask.domain.model.CognitiveLoad
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.averycorp.prismtask.domain.model.TaskMode
import com.averycorp.prismtask.domain.usecase.BoundaryDecision
import com.averycorp.prismtask.domain.usecase.BoundaryEnforcer
import com.averycorp.prismtask.domain.usecase.CognitiveLoadClassifier
import com.averycorp.prismtask.domain.usecase.LifeCategoryClassifier
import com.averycorp.prismtask.domain.usecase.TaskModeClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lightweight local model for subtasks surfaced in the editor's Details tab.
 * Subtasks are held in VM state until the task is persisted — this lets
 * templates pre-fill a list of titles that the user can tweak before the
 * save path flushes them into [TaskRepository] as real [TaskEntity] rows.
 */
data class PendingSubtask(
    val id: Long,
    val title: String,
    val isCompleted: Boolean = false
)

@HiltViewModel
class AddEditTaskViewModel
@Inject
constructor(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val tagRepository: TagRepository,
    private val attachmentRepository: AttachmentRepository,
    private val templateRepository: TaskTemplateRepository,
    private val taskTimingRepository: TaskTimingRepository,
    private val boundaryRuleRepository: BoundaryRuleRepository,
    private val notificationPreferences: NotificationPreferences,
    private val userPreferencesDataStore: com.averycorp.prismtask.data.preferences.UserPreferencesDataStore,
    taskBehaviorPreferences: TaskBehaviorPreferences,
    private val advancedTuningPreferences: com.averycorp.prismtask.data.preferences.AdvancedTuningPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val boundaryEnforcer = BoundaryEnforcer()

    /**
     * User-configurable reminder offsets (millis-before-due). Falls back to the
     * factory list `0,15m,30m,1h,1d` when the user hasn't customized presets.
     * The "None" option is added by the picker UI; this list is only the
     * positive-offset choices.
     */
    val reminderPresets: StateFlow<List<Long>> = taskBehaviorPreferences
        .getReminderPresets()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            listOf(0L, 900_000L, 1_800_000L, 3_600_000L, 86_400_000L)
        )

    val editorFieldRows: StateFlow<com.averycorp.prismtask.data.preferences.EditorFieldRows> =
        advancedTuningPreferences.getEditorFieldRows()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences.EditorFieldRows()
            )

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private var currentTaskId: Long? by mutableStateOf(null)
    private val _taskIdFlow = MutableStateFlow<Long?>(null)
    val isEditMode: Boolean get() = currentTaskId != null

    /** The id of the task currently being edited, or null in create mode. */
    val currentEditingTaskId: Long? get() = currentTaskId

    private var existingTask: TaskEntity? = null
    private var loadJob: Job? = null

    var title by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var dueDate by mutableStateOf<Long?>(null)
        private set
    var dueTime by mutableStateOf<Long?>(null)
        private set
    var priority by mutableIntStateOf(0)
        private set
    var projectId by mutableStateOf<Long?>(null)
        private set
    var parentTaskId by mutableStateOf<Long?>(null)
        private set
    var recurrenceRule by mutableStateOf<RecurrenceRule?>(null)
        private set
    var reminderOffset by mutableStateOf<Long?>(null)
        private set
    var estimatedDuration by mutableStateOf<Int?>(null)
        private set
    var titleError by mutableStateOf(false)
        private set
    var notes by mutableStateOf("")
        private set
    var selectedTagIds by mutableStateOf(setOf<Long>())
        private set

    /**
     * Work-Life Balance category for this task. `null` means "Auto" —
     * the save path will run [LifeCategoryClassifier] to guess one before
     * persisting (see [saveTask]).
     */
    var lifeCategory by mutableStateOf<LifeCategory?>(null)
        private set

    /** True if the user has explicitly picked a category via the Organize tab chips. */
    var lifeCategoryManuallySet by mutableStateOf(false)
        private set

    private val lifeCategoryClassifier = LifeCategoryClassifier()

    /**
     * Reward / output mode for this task. `null` means "Auto" — the save
     * path will run [TaskModeClassifier] to guess one before persisting.
     * Orthogonal to [lifeCategory] (see `docs/WORK_PLAY_RELAX.md`).
     */
    var taskMode by mutableStateOf<TaskMode?>(null)
        private set

    var taskModeManuallySet by mutableStateOf(false)
        private set

    private val taskModeClassifier = TaskModeClassifier()

    /**
     * Start-friction for this task. `null` means "Auto" — the save path
     * runs [CognitiveLoadClassifier] to guess one before persisting.
     * Orthogonal to [lifeCategory] / [taskMode] (see
     * `docs/COGNITIVE_LOAD.md`).
     */
    var cognitiveLoad by mutableStateOf<CognitiveLoad?>(null)
        private set

    var cognitiveLoadManuallySet by mutableStateOf(false)
        private set

    private val cognitiveLoadClassifier = CognitiveLoadClassifier()

    /**
     * Boundary-rule decision for the task currently being edited. The save
     * path checks this and bubbles a [BoundaryDecision.Block] up to the UI
     * via [pendingBoundaryBlock]; [BoundaryDecision.Suggest] pre-fills the
     * life category field.
     */
    var pendingBoundaryBlock by mutableStateOf<BoundaryDecision.Block?>(null)
        private set

    fun dismissBoundaryBlock() {
        pendingBoundaryBlock = null
    }

    /**
     * Evaluate boundary rules against the current draft. Returns `true` if
     * the save should proceed, `false` if the UI should show the block dialog.
     * A SUGGEST decision silently pre-fills [lifeCategory] unless the user
     * already set one manually.
     */
    private suspend fun evaluateBoundaryRules(): Boolean {
        val draftCategory = lifeCategory
            ?: lifeCategoryClassifier
                .classify(title, description.ifBlank { null })
                .takeIf { it != LifeCategory.UNCATEGORIZED }
            ?: return true
        val rules = boundaryRuleRepository.getRulesOnce()
        if (rules.isEmpty()) return true
        return when (val decision = boundaryEnforcer.evaluate(rules, draftCategory)) {
            is BoundaryDecision.Allow -> true
            is BoundaryDecision.Block -> {
                pendingBoundaryBlock = decision
                false
            }
            is BoundaryDecision.Suggest -> {
                if (!lifeCategoryManuallySet) {
                    lifeCategory = decision.category
                }
                true
            }
        }
    }

    /**
     * Unpersisted subtasks for the task currently being composed. Populated
     * either by the user typing into the Details tab's subtask field or by
     * applying a template (which dumps its blueprint subtask titles here).
     * Flushed into real [TaskEntity] rows when [saveTask] succeeds.
     */
    val pendingSubtasks: SnapshotStateList<PendingSubtask> = mutableStateListOf()
    private var nextPendingSubtaskId: Long = 1L

    // Snapshot of initial values for unsaved-changes detection.
    private var hasInitialized: Boolean = false
    private var initialTitle: String = ""
    private var initialDescription: String = ""
    private var initialDueDate: Long? = null
    private var initialDueTime: Long? = null
    private var initialPriority: Int = 0
    private var initialProjectId: Long? = null
    private var initialParentTaskId: Long? = null
    private var initialRecurrenceRule: RecurrenceRule? = null
    private var initialReminderOffset: Long? = null
    private var initialEstimatedDuration: Int? = null
    private var initialNotes: String = ""
    private var initialSelectedTagIds: Set<Long> = emptySet()
    private var initialLifeCategory: LifeCategory? = null
    private var initialTaskMode: TaskMode? = null
    private var initialCognitiveLoad: CognitiveLoad? = null

    val projects: StateFlow<List<ProjectEntity>> = projectRepository
        .getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<TagEntity>> = tagRepository
        .getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val attachments: StateFlow<List<AttachmentEntity>> = _taskIdFlow
        .flatMapLatest { id ->
            if (id != null) {
                attachmentRepository.getAttachments(id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Count of persisted subtasks for the task being edited. Drives the
    // "Include Subtasks (N)" checkbox on the duplicate dialog.
    @OptIn(ExperimentalCoroutinesApi::class)
    val subtaskCount: StateFlow<Int> = _taskIdFlow
        .flatMapLatest { id ->
            if (id != null) {
                taskRepository.getSubtasks(id).map { it.size }
            } else {
                flowOf(0)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Sum of all logged time entries (manual / pomodoro / timer) for the task
    // being edited. Drives the "Logged Time" section in the Schedule tab.
    // Empty in create mode (no taskId yet) — the section hides via isEditMode.
    @OptIn(ExperimentalCoroutinesApi::class)
    val loggedMinutes: StateFlow<Int> = _taskIdFlow
        .flatMapLatest { id ->
            if (id != null) {
                taskTimingRepository.observeSumMinutesForTask(id)
            } else {
                flowOf(0)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics
                .getInstance()
                .setCustomKey("screen", "AddEditTaskScreen")
        } catch (_: Exception) {
        }
        // Backward compat: when opened via the navigation route, SavedStateHandle
        // contains the taskId nav arg. Sheet-based invocation leaves this null and
        // calls initialize() explicitly from the host composable.
        val routeTaskId = savedStateHandle.get<Long>("taskId")
        if (routeTaskId != null) {
            initialize(
                taskId = routeTaskId.takeIf { it != -1L },
                projectId = null,
                initialDate = null
            )
        }
    }

    /**
     * Prepare the form for a new invocation. Resets all fields, then either
     * loads an existing task (edit mode) or applies the given defaults (create mode).
     * Safe to call multiple times — each call re-seeds the form and cancels any
     * in-flight load from a previous call.
     */
    fun initialize(taskId: Long?, projectId: Long?, initialDate: Long?) {
        loadJob?.cancel()

        // Reset all fields to defaults / supplied create-mode seeds.
        currentTaskId = taskId
        _taskIdFlow.value = taskId
        existingTask = null
        title = ""
        description = ""
        dueDate = initialDate
        dueTime = null
        priority = 0
        this.projectId = projectId
        parentTaskId = null
        recurrenceRule = null
        reminderOffset = null
        estimatedDuration = null
        notes = ""
        selectedTagIds = emptySet()
        lifeCategory = null
        lifeCategoryManuallySet = false
        taskMode = null
        taskModeManuallySet = false
        cognitiveLoad = null
        cognitiveLoadManuallySet = false
        titleError = false
        pendingSubtasks.clear()
        nextPendingSubtaskId = 1L

        if (taskId != null) {
            // Snapshot with defaults until the real task loads — the load
            // will replace the snapshot with the true initial values.
            hasInitialized = false
            loadJob = viewModelScope.launch {
                try {
                    val task = taskRepository.getTaskById(taskId).firstOrNull()
                    val tagIds = tagRepository
                        .getTagsForTask(taskId)
                        .firstOrNull()
                        ?.map { it.id }
                        ?.toSet()
                        ?: emptySet()
                    if (task != null) {
                        existingTask = task
                        title = task.title
                        description = task.description.orEmpty()
                        dueDate = task.dueDate
                        dueTime = task.dueTime
                        priority = task.priority
                        this@AddEditTaskViewModel.projectId = task.projectId
                        parentTaskId = task.parentTaskId
                        recurrenceRule = task.recurrenceRule?.let { RecurrenceConverter.fromJson(it) }
                        reminderOffset = task.reminderOffset
                        estimatedDuration = task.estimatedDuration
                        notes = task.notes.orEmpty()
                        selectedTagIds = tagIds
                        val loadedCategory = LifeCategory.fromStorage(task.lifeCategory)
                        lifeCategory = loadedCategory.takeIf { it != LifeCategory.UNCATEGORIZED }
                        lifeCategoryManuallySet = lifeCategory != null
                        val loadedMode = TaskMode.fromStorage(task.taskMode)
                        taskMode = loadedMode.takeIf { it != TaskMode.UNCATEGORIZED }
                        taskModeManuallySet = taskMode != null
                        val loadedLoad = CognitiveLoad.fromStorage(task.cognitiveLoad)
                        cognitiveLoad = loadedLoad.takeIf { it != CognitiveLoad.UNCATEGORIZED }
                        cognitiveLoadManuallySet = cognitiveLoad != null
                        snapshotInitialValuesFromTask(task, tagIds)
                    } else {
                        snapshotInitialValuesForCreate(projectId, initialDate)
                    }
                } catch (e: Exception) {
                    Log.e("AddEditTaskVM", "Failed to load task", e)
                    snapshotInitialValuesForCreate(projectId, initialDate)
                } finally {
                    hasInitialized = true
                }
            }
        } else {
            snapshotInitialValuesForCreate(projectId, initialDate)
            hasInitialized = true
            // Pre-fill the reminder offset for new tasks from the user's
            // configured default. A value of OFFSET_NONE / -1 means "no
            // default" (leave it null so the user must opt in explicitly).
            // Edit-mode never goes through this path, so we never overwrite
            // an existing task's saved reminder.
            loadJob = viewModelScope.launch {
                try {
                    val default = notificationPreferences.getDefaultReminderOffsetOnce()
                    if (default != NotificationPreferences.OFFSET_NONE && default >= 0L) {
                        // Only apply if the user hasn't already changed it.
                        if (reminderOffset == null) {
                            reminderOffset = default
                            initialReminderOffset = default
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AddEditTaskVM", "Failed to load default reminder offset", e)
                }
            }
        }
    }

    private fun snapshotInitialValuesFromTask(task: TaskEntity, tagIds: Set<Long>) {
        initialTitle = task.title
        initialDescription = task.description.orEmpty()
        initialDueDate = task.dueDate
        initialDueTime = task.dueTime
        initialPriority = task.priority
        initialProjectId = task.projectId
        initialParentTaskId = task.parentTaskId
        initialRecurrenceRule = task.recurrenceRule?.let { RecurrenceConverter.fromJson(it) }
        initialReminderOffset = task.reminderOffset
        initialEstimatedDuration = task.estimatedDuration
        initialNotes = task.notes.orEmpty()
        initialSelectedTagIds = tagIds
        initialLifeCategory = LifeCategory.fromStorage(task.lifeCategory).takeIf {
            it != LifeCategory.UNCATEGORIZED
        }
        initialTaskMode = TaskMode.fromStorage(task.taskMode).takeIf {
            it != TaskMode.UNCATEGORIZED
        }
        initialCognitiveLoad = CognitiveLoad.fromStorage(task.cognitiveLoad).takeIf {
            it != CognitiveLoad.UNCATEGORIZED
        }
    }

    private fun snapshotInitialValuesForCreate(projectId: Long?, initialDate: Long?) {
        initialTitle = ""
        initialDescription = ""
        initialDueDate = initialDate
        initialDueTime = null
        initialPriority = 0
        initialProjectId = projectId
        initialParentTaskId = null
        initialRecurrenceRule = null
        initialReminderOffset = null
        initialEstimatedDuration = null
        initialNotes = ""
        initialSelectedTagIds = emptySet()
        initialLifeCategory = null
        initialTaskMode = null
        initialCognitiveLoad = null
    }

    val hasUnsavedChanges: Boolean
        get() = hasInitialized &&
            (
                title != initialTitle ||
                    description != initialDescription ||
                    dueDate != initialDueDate ||
                    dueTime != initialDueTime ||
                    priority != initialPriority ||
                    projectId != initialProjectId ||
                    parentTaskId != initialParentTaskId ||
                    recurrenceRule != initialRecurrenceRule ||
                    reminderOffset != initialReminderOffset ||
                    estimatedDuration != initialEstimatedDuration ||
                    notes != initialNotes ||
                    selectedTagIds != initialSelectedTagIds ||
                    lifeCategory != initialLifeCategory ||
                    taskMode != initialTaskMode ||
                    cognitiveLoad != initialCognitiveLoad
                )

    fun onTitleChange(value: String) {
        title = value
        if (value.isNotBlank()) titleError = false
    }

    fun onDescriptionChange(value: String) {
        description = value
    }

    fun onDueDateChange(value: Long?) {
        dueDate = value
    }

    fun onDueTimeChange(value: Long?) {
        dueTime = value
    }

    fun onPriorityChange(value: Int) {
        priority = value
    }

    fun onProjectIdChange(value: Long?) {
        projectId = value
    }

    fun onRecurrenceRuleChange(value: RecurrenceRule?) {
        recurrenceRule = value
    }

    fun onNotesChange(value: String) {
        notes = value
    }

    fun onReminderOffsetChange(value: Long?) {
        reminderOffset = value
    }

    fun onEstimatedDurationChange(value: Int?) {
        estimatedDuration = value
    }

    /**
     * Append a manual time-tracking entry to the task being edited. Only
     * valid in edit mode (the task must already exist so timings can FK to
     * it). Negative or zero minutes are silently ignored at the call site —
     * the underlying repository requires `> 0` and would otherwise throw.
     */
    fun logTime(minutes: Int) {
        val taskId = currentTaskId ?: return
        if (minutes <= 0) return
        viewModelScope.launch {
            try {
                taskTimingRepository.logTime(taskId = taskId, durationMinutes = minutes)
            } catch (e: Exception) {
                Log.w("AddEditTaskVM", "logTime failed", e)
                _errorMessages.emit("Failed to log time")
            }
        }
    }

    fun onSelectedTagIdsChange(value: Set<Long>) {
        selectedTagIds = value
    }

    fun onParentTaskIdChange(value: Long?) {
        parentTaskId = value
    }

    /**
     * Set the [LifeCategory] chip. Passing `null` switches back to "Auto"
     * mode — the classifier will run at save time to guess one.
     */
    fun onLifeCategoryChange(value: LifeCategory?) {
        lifeCategory = value
        lifeCategoryManuallySet = value != null
    }

    /**
     * Set the [TaskMode] chip. Passing `null` switches back to "Auto" mode
     * — the classifier will run at save time to guess one.
     */
    fun onTaskModeChange(value: TaskMode?) {
        taskMode = value
        taskModeManuallySet = value != null
    }

    /**
     * Set the [CognitiveLoad] chip. Passing `null` switches back to "Auto"
     * — the classifier will run at save time to guess one.
     */
    fun onCognitiveLoadChange(value: CognitiveLoad?) {
        cognitiveLoad = value
        cognitiveLoadManuallySet = value != null
    }

    /**
     * Resolve the final life_category value to persist:
     *  - If the user picked one, use it.
     *  - Otherwise run the keyword classifier on title + description.
     *  - No keyword match maps to UNCATEGORIZED.name (explicit "tried and
     *    didn't match") rather than null (which now means "never classified").
     */
    internal fun resolveLifeCategoryForSave(): String {
        if (lifeCategoryManuallySet && lifeCategory != null) {
            return lifeCategory?.name ?: LifeCategory.UNCATEGORIZED.name
        }
        val guess = lifeCategoryClassifier.classify(title, description.ifBlank { null })
        return guess.name
    }

    /**
     * Resolve the final task_mode value to persist. Same shape as
     * [resolveLifeCategoryForSave] but for the orthogonal mode dimension.
     */
    internal fun resolveTaskModeForSave(): String {
        if (taskModeManuallySet && taskMode != null) {
            return taskMode?.name ?: TaskMode.UNCATEGORIZED.name
        }
        val guess = taskModeClassifier.classify(title, description.ifBlank { null })
        return guess.name
    }

    /**
     * Resolve the final cognitive_load value to persist. Same shape as
     * [resolveLifeCategoryForSave] but for the orthogonal start-friction
     * dimension. See `docs/COGNITIVE_LOAD.md`.
     */
    internal fun resolveCognitiveLoadForSave(): String {
        if (cognitiveLoadManuallySet && cognitiveLoad != null) {
            return cognitiveLoad?.name ?: CognitiveLoad.UNCATEGORIZED.name
        }
        val guess = cognitiveLoadClassifier.classify(title, description.ifBlank { null })
        return guess.name
    }

    /**
     * Appends a new pending subtask with the supplied [title] and returns
     * the generated id. Subtasks are kept in VM state until [saveTask]
     * flushes them to the database.
     */
    fun addPendingSubtask(title: String): Long {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return -1L
        val id = nextPendingSubtaskId++
        pendingSubtasks.add(PendingSubtask(id = id, title = trimmed))
        return id
    }

    /** Toggles the local completion flag on an in-progress subtask. */
    fun togglePendingSubtask(id: Long) {
        val idx = pendingSubtasks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val current = pendingSubtasks[idx]
            pendingSubtasks[idx] = current.copy(isCompleted = !current.isCompleted)
        }
    }

    /** Removes a pending subtask from the list by id. */
    fun removePendingSubtask(id: Long) {
        pendingSubtasks.removeAll { it.id == id }
    }

    /**
     * Loads the template referenced by [templateId] and pre-fills the
     * in-progress form with its blueprint fields. Fires only in create mode
     * — the editor hides its template button in edit mode so callers don't
     * accidentally stomp an existing task's data. Returns true on success.
     */
    suspend fun applyTemplate(templateId: Long): Boolean {
        return try {
            val template = templateRepository.getTemplateById(templateId)
                ?: run {
                    _errorMessages.emit("Template not found")
                    return false
                }
            title = template.templateTitle ?: template.name
            description = template.templateDescription.orEmpty()
            priority = template.templatePriority ?: priority
            projectId = template.templateProjectId ?: projectId
            recurrenceRule = template.templateRecurrenceJson
                ?.let { RecurrenceConverter.fromJson(it) }
            estimatedDuration = template.templateDuration
            val templateTagIds = TaskTemplateRepository
                .parseTagIds(template.templateTagsJson)
                .toSet()
            if (templateTagIds.isNotEmpty()) {
                selectedTagIds = templateTagIds
            }
            pendingSubtasks.clear()
            TaskTemplateRepository
                .parseSubtaskTitles(template.templateSubtasksJson)
                .forEach { subtaskTitle -> addPendingSubtask(subtaskTitle) }
            titleError = false
            true
        } catch (e: Exception) {
            Log.e("AddEditTaskVM", "Failed to apply template", e)
            _errorMessages.emit("Failed to apply template")
            false
        }
    }

    /**
     * Captures the currently-being-edited task as a reusable template.
     * Only meaningful in edit mode — there is no draft state to persist in
     * create mode, so this returns null without doing anything there.
     */
    suspend fun saveAsTemplate(
        name: String,
        icon: String?,
        category: String?
    ): Long? {
        val taskId = currentTaskId ?: return null
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null
        return try {
            templateRepository.createTemplateFromTask(
                taskId = taskId,
                name = trimmedName,
                icon = icon?.takeIf { it.isNotBlank() },
                category = category?.trim()?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.e("AddEditTaskVM", "Failed to save task as template", e)
            _errorMessages.emit("Failed to save template")
            null
        }
    }

    /**
     * Inline project creation from the Organize tab. Creates the project via the
     * repository and then selects it on the in-progress form. Uses the default
     * project icon so the caller only has to supply name + color.
     */
    fun createAndSelectProject(name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val newId = projectRepository.addProject(name = trimmed, color = color)
                projectId = newId
            } catch (e: Exception) {
                Log.e("AddEditTaskVM", "Failed to create project", e)
                _errorMessages.emit("Failed to create project")
            }
        }
    }

    /**
     * Inline tag creation from the Organize tab. Creates the tag via the
     * repository and then adds it to the currently selected tag set so the
     * new tag is immediately assigned to the task.
     */
    fun createAndAssignTag(name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val newId = tagRepository.addTag(name = trimmed, color = color)
                selectedTagIds = selectedTagIds + newId
            } catch (e: Exception) {
                Log.e("AddEditTaskVM", "Failed to create tag", e)
                _errorMessages.emit("Failed to create tag")
            }
        }
    }

    fun onAddImageAttachment(context: Context, uri: Uri) {
        val id = currentTaskId ?: return
        viewModelScope.launch {
            try {
                attachmentRepository.addImageAttachment(context, id, uri)
            } catch (e: Exception) {
                Log.e("AddEditTaskVM", "Failed to add image attachment", e)
                _errorMessages.emit("Failed to add attachment")
            }
        }
    }

    fun onAddLinkAttachment(url: String) {
        val id = currentTaskId ?: return
        viewModelScope.launch {
            try {
                attachmentRepository.addLinkAttachment(id, url)
            } catch (e: Exception) {
                Log.e("AddEditTaskVM", "Failed to add link attachment", e)
                _errorMessages.emit("Failed to add attachment")
            }
        }
    }

    fun onDeleteAttachment(context: Context, attachment: AttachmentEntity) {
        viewModelScope.launch {
            try {
                attachmentRepository.deleteAttachment(context, attachment)
            } catch (e: Exception) {
                Log.e("AddEditTaskVM", "Failed to delete attachment", e)
                _errorMessages.emit("Failed to delete attachment")
            }
        }
    }

    /**
     * Save the current draft. Callers that want to bypass boundary rules
     * (e.g. the user tapped "Create Anyway" in the block dialog) can pass
     * [ignoreBoundaries] = true.
     */
    suspend fun saveTask(ignoreBoundaries: Boolean = false): Boolean {
        if (title.isBlank()) {
            titleError = true
            return false
        }

        if (!ignoreBoundaries && !evaluateBoundaryRules()) {
            return false
        }

        return try {
            val trimmedTitle = title.trim()
            val trimmedDesc = description.trim().ifEmpty { null }
            val trimmedNotes = notes.trim().ifEmpty { null }
            val recurrenceJson = recurrenceRule?.let { RecurrenceConverter.toJson(it) }
            val resolvedLifeCategory = resolveLifeCategoryForSave()
            val resolvedTaskMode = resolveTaskModeForSave()
            val resolvedCognitiveLoad = resolveCognitiveLoadForSave()
            val existing = existingTask
            val savedId: Long
            if (existing != null) {
                taskRepository.updateTask(
                    existing.copy(
                        title = trimmedTitle,
                        description = trimmedDesc,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        priority = priority,
                        projectId = projectId,
                        parentTaskId = parentTaskId,
                        reminderOffset = reminderOffset,
                        recurrenceRule = recurrenceJson,
                        estimatedDuration = estimatedDuration,
                        notes = trimmedNotes,
                        lifeCategory = resolvedLifeCategory,
                        taskMode = resolvedTaskMode,
                        cognitiveLoad = resolvedCognitiveLoad
                    )
                )
                savedId = existing.id
            } else {
                savedId = taskRepository.addTask(
                    title = trimmedTitle,
                    description = trimmedDesc,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    priority = priority,
                    projectId = projectId,
                    parentTaskId = parentTaskId,
                    lifeCategory = resolvedLifeCategory,
                    taskMode = resolvedTaskMode,
                    cognitiveLoad = resolvedCognitiveLoad,
                    reminderOffset = reminderOffset,
                    recurrenceRule = recurrenceJson,
                    estimatedDuration = estimatedDuration
                )
            }

            // Save tags
            tagRepository.setTagsForTask(savedId, selectedTagIds.toList())

            // Flush any pending subtasks (e.g. from a template) into real rows.
            if (pendingSubtasks.isNotEmpty()) {
                val now = System.currentTimeMillis()
                pendingSubtasks.forEachIndexed { index, sub ->
                    val subtask = TaskEntity(
                        title = sub.title,
                        parentTaskId = savedId,
                        isCompleted = sub.isCompleted,
                        completedAt = if (sub.isCompleted) now else null,
                        sortOrder = index,
                        createdAt = now,
                        updatedAt = now
                    )
                    taskRepository.insertTask(subtask)
                }
                pendingSubtasks.clear()
            }

            true
        } catch (e: Exception) {
            Log.e("AddEditTaskVM", "Failed to save task", e)
            _errorMessages.emit("Couldn't save task")
            false
        }
    }

    suspend fun deleteTask() {
        try {
            currentTaskId?.let { taskRepository.deleteTask(it) }
        } catch (e: Exception) {
            Log.e("AddEditTaskVM", "Failed to delete task", e)
            _errorMessages.emit("Couldn't delete task")
        }
    }

    /**
     * Duplicates the task currently open in the editor. On success, re-seeds
     * the form with the new copy so the sheet immediately shows the
     * duplicated task without the host having to dismiss-and-reopen. Returns
     * the id of the new task, or null if duplication failed (e.g. we were in
     * create mode or the original had already been deleted).
     */
    suspend fun duplicateCurrentTask(
        includeSubtasks: Boolean,
        copyDueDate: Boolean = false
    ): Long? {
        val id = currentTaskId ?: return null
        return try {
            val newId = taskRepository.duplicateTask(id, includeSubtasks, copyDueDate)
            if (newId <= 0L) {
                _errorMessages.emit("Couldn't duplicate task")
                null
            } else {
                // Reseed the form from the new copy. projectId / initialDate
                // are not needed since initialize() will load them from the
                // new task's persisted state.
                initialize(taskId = newId, projectId = null, initialDate = null)
                newId
            }
        } catch (e: Exception) {
            Log.e("AddEditTaskVM", "Failed to duplicate task", e)
            _errorMessages.emit("Couldn't duplicate task")
            null
        }
    }
}
