package com.averycorp.prismtask.ui.screens.tasklist

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.UrgencyWeights
import com.averycorp.prismtask.data.repository.AttachmentRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.model.TagFilterMode
import com.averycorp.prismtask.ui.components.QuickRescheduleFormatter
import com.averycorp.prismtask.domain.model.TaskFilter
import com.averycorp.prismtask.domain.usecase.ParsedTodoItem
import com.averycorp.prismtask.domain.usecase.TodoListParser
import com.averycorp.prismtask.domain.usecase.UrgencyScorer
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption(val label: String, val token: String) {
    DUE_DATE("Due Date", SortPreferences.SortModes.DUE_DATE),
    PRIORITY("Priority", SortPreferences.SortModes.PRIORITY),
    URGENCY("Urgency", SortPreferences.SortModes.URGENCY),
    CREATED("Date Created", SortPreferences.SortModes.DATE_CREATED),
    ALPHABETICAL("Alphabetical", SortPreferences.SortModes.ALPHABETICAL),
    CUSTOM("Custom", SortPreferences.SortModes.CUSTOM);

    companion object {
        fun fromToken(token: String?): SortOption? =
            entries.find { it.token == token }
    }
}

enum class ViewMode(val label: String) {
    UPCOMING("Upcoming"),
    LIST("List"),
    BY_PROJECT("By Project"),
    WEEK("Week"),
    MONTH("Month")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val tagRepository: TagRepository,
    private val attachmentRepository: AttachmentRepository,
    private val todoListParser: TodoListParser,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val sortPreferences: SortPreferences
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    // Events emitted when the user wants to open the editor for a specific
    // task id, e.g. after the "View" action on the "Task Duplicated" snackbar.
    private val _openTaskEditorEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openTaskEditorEvents: SharedFlow<Long> = _openTaskEditorEvents.asSharedFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _urgencyWeights = MutableStateFlow(UrgencyWeights())

    init {
        // Prefer the per-screen sort saved in SortPreferences. When the user
        // has never picked a sort on this screen, fall back to the app-wide
        // "default sort" setting from TaskBehaviorPreferences so existing
        // installs keep respecting the global preference.
        viewModelScope.launch {
            val savedToken = sortPreferences.getSortModeOrNull(SortPreferences.ScreenKeys.TASK_LIST)
            val initial = SortOption.fromToken(savedToken)
                ?: taskBehaviorPreferences.getDefaultSort().first().let { name ->
                    SortOption.entries.find { it.name == name } ?: SortOption.DUE_DATE
                }
            _currentSort.value = initial
        }
        viewModelScope.launch {
            taskBehaviorPreferences.getDefaultViewMode().collect { modeName ->
                val mode = ViewMode.entries.find { it.name == modeName } ?: ViewMode.UPCOMING
                if (_viewMode.value == ViewMode.UPCOMING) _viewMode.value = mode
            }
        }
        viewModelScope.launch {
            taskBehaviorPreferences.getUrgencyWeights().collect { weights ->
                _urgencyWeights.value = weights
            }
        }
    }

    private val rootTasks: StateFlow<List<TaskEntity>> = taskRepository.getIncompleteRootTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            taskRepository.getIncompleteRootTasks().first()
            _isLoading.value = false
        }
    }

    // For filter: get ALL root tasks (including completed/archived) so filters can show them
    private val allRootTasks: StateFlow<List<TaskEntity>> = taskRepository.getAllTasks()
        .map { tasks -> tasks.filter { it.parentTaskId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Reactive task-count map (projectId -> active root-task count) backing
     * the move-to-project sheet. Counts only incomplete root tasks so the
     * number matches what the user actually sees in the project filter row.
     */
    val taskCountByProject: StateFlow<Map<Long, Int>> = taskRepository.getIncompleteRootTasks()
        .map { tasks ->
            tasks.groupingBy { it.projectId }
                .eachCount()
                .mapNotNull { (id, count) -> id?.let { it to count } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allTags: StateFlow<List<TagEntity>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentFilter = MutableStateFlow(TaskFilter())
    val currentFilter: StateFlow<TaskFilter> = _currentFilter

    // Backwards compat: derived selectedProjectId from the filter
    val selectedProjectId: StateFlow<Long?> = _currentFilter
        .map { filter ->
            if (filter.selectedProjectIds.size == 1) filter.selectedProjectIds.first()
            else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentSort = MutableStateFlow(SortOption.DUE_DATE)
    val currentSort: StateFlow<SortOption> = _currentSort

    private val _viewMode = MutableStateFlow(ViewMode.UPCOMING)
    val viewMode: StateFlow<ViewMode> = _viewMode

    val taskTagsMap: StateFlow<Map<Long, List<TagEntity>>> = allRootTasks.flatMapLatest { tasks ->
        val parentIds = tasks.map { it.id }
        if (parentIds.isEmpty()) {
            flowOf(emptyMap<Long, List<TagEntity>>())
        } else {
            val flows = parentIds.map { id ->
                tagRepository.getTagsForTask(id).map { tags -> id to tags }
            }
            combine(flows) { pairs: Array<Pair<Long, List<TagEntity>>> -> pairs.toMap() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredTasks: StateFlow<List<TaskEntity>> =
        combine(allRootTasks, _currentFilter, _currentSort, taskTagsMap) { taskList, filter, sort, tagsMap ->
            val filtered = applyFilter(taskList, filter, tagsMap)
            sortTasks(filtered, sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedTasks: StateFlow<Map<String, List<TaskEntity>>> =
        combine(allRootTasks, _currentFilter, _currentSort, taskTagsMap) { taskList, filter, sort, tagsMap ->
            val filtered = applyFilter(taskList, filter, tagsMap)
            groupByDate(filtered, sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Filtered tasks grouped by projectId (null for "No Project"). Backs
     * the By Project view mode — the UI renders one section per project
     * with the tasks sorted using the currently-selected SortOption inside
     * each group. Projects with zero matching tasks still appear so they
     * can act as drop targets for cross-project drag.
     */
    val tasksByProject: StateFlow<Map<Long?, List<TaskEntity>>> =
        combine(
            allRootTasks,
            _currentFilter,
            _currentSort,
            taskTagsMap,
            projects
        ) { taskList, filter, sort, tagsMap, allProjects ->
            val filtered = applyFilter(taskList, filter, tagsMap)
            val grouped = linkedMapOf<Long?, MutableList<TaskEntity>>()
            // Seed every known project so empty projects still render as
            // drop targets in the UI.
            allProjects.forEach { proj -> grouped[proj.id] = mutableListOf() }
            grouped[null] = mutableListOf()
            for (task in filtered) {
                grouped.getOrPut(task.projectId) { mutableListOf() }.add(task)
            }
            grouped.mapValues { (_, list) -> sortTasks(list, sort) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val dayStartFlow: StateFlow<Long> = taskBehaviorPreferences.getDayStartHour()
        .map { DayBoundary.startOfCurrentDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayBoundary.startOfCurrentDay(0))

    val overdueCount: StateFlow<Int> = combine(rootTasks, dayStartFlow) { tasks, startOfToday ->
        tasks.count { it.dueDate != null && it.dueDate < startOfToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val subtasksMap: StateFlow<Map<Long, List<TaskEntity>>> = allRootTasks.flatMapLatest { tasks ->
        val parentIds = tasks.map { it.id }
        if (parentIds.isEmpty()) {
            flowOf(emptyMap<Long, List<TaskEntity>>())
        } else {
            val flows = parentIds.map { id ->
                taskRepository.getSubtasks(id).map { subtasks: List<TaskEntity> -> id to subtasks }
            }
            combine(flows) { pairs: Array<Pair<Long, List<TaskEntity>>> -> pairs.toMap() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val attachmentCountMap: StateFlow<Map<Long, Int>> = allRootTasks.flatMapLatest { tasks ->
        val parentIds = tasks.map { it.id }
        if (parentIds.isEmpty()) {
            flowOf(emptyMap<Long, Int>())
        } else {
            val flows = parentIds.map { id ->
                attachmentRepository.getAttachmentCount(id).map { count -> id to count }
            }
            combine(flows) { pairs: Array<Pair<Long, Int>> -> pairs.toMap() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Multi-select state
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode

    private val _selectedTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTaskIds: StateFlow<Set<Long>> = _selectedTaskIds

    fun onEnterMultiSelect(taskId: Long) {
        _isMultiSelectMode.value = true
        _selectedTaskIds.value = setOf(taskId)
    }

    fun onToggleTaskSelection(taskId: Long) {
        val current = _selectedTaskIds.value
        _selectedTaskIds.value = if (taskId in current) current - taskId else current + taskId
    }

    fun onSelectAll() {
        _selectedTaskIds.value = filteredTasks.value.map { it.id }.toSet()
    }

    fun onDeselectAll() {
        _selectedTaskIds.value = emptySet()
    }

    fun onExitMultiSelect() {
        _isMultiSelectMode.value = false
        _selectedTaskIds.value = emptySet()
    }

    fun onBulkComplete() {
        val ids = _selectedTaskIds.value.toList()
        viewModelScope.launch {
            try {
                ids.forEach { taskRepository.completeTask(it) }
                onExitMultiSelect()
                val result = snackbarHostState.showSnackbar(
                    message = "${ids.size} tasks completed",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    ids.forEach { taskRepository.uncompleteTask(it) }
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to bulk complete", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onBulkDelete() {
        val ids = _selectedTaskIds.value.toList()
        viewModelScope.launch {
            try {
                val savedTasks = ids.mapNotNull { taskRepository.getTaskByIdOnce(it) }
                ids.forEach { taskRepository.deleteTask(it) }
                onExitMultiSelect()
                val result = snackbarHostState.showSnackbar(
                    message = "${ids.size} tasks deleted",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    savedTasks.forEach { taskRepository.insertTask(it) }
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to bulk delete", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onBulkSetPriority(priority: Int) {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                // Snapshot previous priorities for undo.
                val previous = ids.mapNotNull { id ->
                    taskRepository.getTaskByIdOnce(id)?.let { it.id to it.priority }
                }
                taskRepository.batchUpdatePriority(ids, priority)
                onExitMultiSelect()
                val result = snackbarHostState.showSnackbar(
                    message = "Updated Priority for ${ids.size} Tasks",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Restore each task's original priority. Grouping by
                    // priority value keeps the number of UPDATE statements
                    // small for the common case where most tasks started
                    // with the same priority.
                    previous.groupBy { it.second }.forEach { (prio, group) ->
                        taskRepository.batchUpdatePriority(group.map { it.first }, prio)
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to bulk set priority", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onBulkReschedule(newDueDate: Long?) {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                // Snapshot previous due dates for undo.
                val previous = ids.mapNotNull { id ->
                    taskRepository.getTaskByIdOnce(id)?.let { it.id to it.dueDate }
                }
                taskRepository.batchReschedule(ids, newDueDate)
                onExitMultiSelect()
                val label = QuickRescheduleFormatter.describe(newDueDate)
                val result = snackbarHostState.showSnackbar(
                    message = "Rescheduled ${ids.size} Tasks to $label",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    previous.groupBy { it.second }.forEach { (dueDate, group) ->
                        taskRepository.batchReschedule(group.map { it.first }, dueDate)
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to bulk reschedule", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Moves a single task into [newProjectId] (or null to remove the
     * project association) and surfaces an Undo snackbar that restores
     * the previous project. When [cascadeSubtasks] is true, subtasks are
     * moved too and the undo also restores their original projects.
     *
     * Called from the long-press context menu and the drag-to-move-
     * between-projects interaction in the grouped-by-project view.
     */
    fun onMoveToProject(
        taskId: Long,
        newProjectId: Long?,
        cascadeSubtasks: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val task = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
                val previousParentProjectId = task.projectId
                val previousSubtaskProjects = if (cascadeSubtasks) {
                    subtasksMap.value[taskId].orEmpty().associate { it.id to it.projectId }
                } else emptyMap()

                taskRepository.moveToProject(taskId, newProjectId, cascadeSubtasks)

                val projectName = newProjectId?.let { id ->
                    projects.value.find { it.id == id }?.name
                } ?: "No Project"
                val result = snackbarHostState.showSnackbar(
                    message = "Moved '${task.title}' to $projectName",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Restore the parent task first.
                    taskRepository.moveToProject(taskId, previousParentProjectId, false)
                    // Then restore each subtask's original project assignment
                    // in one batch per original project id so we don't issue
                    // N UPDATE statements.
                    previousSubtaskProjects
                        .entries
                        .groupBy { it.value }
                        .forEach { (origProjectId, entries) ->
                            taskRepository.batchMoveToProject(
                                entries.map { it.key },
                                origProjectId
                            )
                        }
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to move task to project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Creates a new project on-the-fly from the single-task move sheet
     * and then moves the given task into it.
     */
    fun onCreateProjectAndMoveTask(taskId: Long, name: String, cascadeSubtasks: Boolean = false) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newId = projectRepository.addProject(name.trim())
                onMoveToProject(taskId, newId, cascadeSubtasks)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to create project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onBulkMoveToProject(newProjectId: Long?) {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val previous = ids.mapNotNull { id ->
                    taskRepository.getTaskByIdOnce(id)?.let { it.id to it.projectId }
                }
                taskRepository.batchMoveToProject(ids, newProjectId)
                onExitMultiSelect()
                val projectName = newProjectId?.let { id ->
                    projects.value.find { it.id == id }?.name
                } ?: "No Project"
                val result = snackbarHostState.showSnackbar(
                    message = "Moved ${ids.size} Tasks to $projectName",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    previous.groupBy { it.second }.forEach { (projectId, group) ->
                        taskRepository.batchMoveToProject(group.map { it.first }, projectId)
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to bulk move", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Applies tag changes from the bulk-tags dialog. [addIds] are tag ids
     * that should be present on every selected task after the operation;
     * [removeIds] are tag ids that should be absent from every selected
     * task. Captures the previous tag set per task so Undo can restore the
     * original assignments in a single pass.
     */
    fun onBulkApplyTags(addIds: Set<Long>, removeIds: Set<Long>) {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty() || (addIds.isEmpty() && removeIds.isEmpty())) return
        viewModelScope.launch {
            try {
                // Snapshot the previous per-task tag sets for undo. We read
                // the currently-cached taskTagsMap which is kept up-to-date
                // by the screen's flows.
                val snapshot: Map<Long, Set<Long>> = ids.associateWith { id ->
                    taskTagsMap.value[id].orEmpty().map { it.id }.toSet()
                }
                addIds.forEach { tagId -> taskRepository.batchAddTag(ids, tagId) }
                removeIds.forEach { tagId -> taskRepository.batchRemoveTag(ids, tagId) }
                onExitMultiSelect()
                val result = snackbarHostState.showSnackbar(
                    message = "Updated Tags for ${ids.size} Tasks",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Restore: for each task, re-establish its original tag
                    // set. Roll back the added tags first (remove them from
                    // tasks that didn't originally have them), then re-add
                    // the removed tags to tasks that did.
                    for (tagId in addIds) {
                        val toRemove = snapshot.filterValues { tagId !in it }.keys.toList()
                        if (toRemove.isNotEmpty()) taskRepository.batchRemoveTag(toRemove, tagId)
                    }
                    for (tagId in removeIds) {
                        val toAdd = snapshot.filterValues { tagId in it }.keys.toList()
                        if (toAdd.isNotEmpty()) taskRepository.batchAddTag(toAdd, tagId)
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to bulk apply tags", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Creates a new project on-the-fly from the bulk move dialog and then
     * moves every currently-selected task into it.
     */
    fun onBulkCreateProjectAndMove(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newId = projectRepository.addProject(name.trim())
                onBulkMoveToProject(newId)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to create project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    private fun applyFilter(
        tasks: List<TaskEntity>,
        filter: TaskFilter,
        tagsMap: Map<Long, List<TagEntity>>
    ): List<TaskEntity> {
        return tasks.filter { task ->
            // Completed filter
            if (!filter.showCompleted && task.isCompleted) return@filter false

            // Archived filter
            if (!filter.showArchived && task.archivedAt != null) return@filter false

            // Project filter
            if (filter.selectedProjectIds.isNotEmpty() && task.projectId !in filter.selectedProjectIds) {
                return@filter false
            }

            // Priority filter
            if (filter.selectedPriorities.isNotEmpty() && task.priority !in filter.selectedPriorities) {
                return@filter false
            }

            // Tag filter
            if (filter.selectedTagIds.isNotEmpty()) {
                val taskTags = tagsMap[task.id]?.map { it.id } ?: emptyList()
                when (filter.tagFilterMode) {
                    TagFilterMode.ANY -> {
                        if (taskTags.none { it in filter.selectedTagIds }) return@filter false
                    }
                    TagFilterMode.ALL -> {
                        if (!taskTags.containsAll(filter.selectedTagIds)) return@filter false
                    }
                }
            }

            // Date range filter
            filter.dateRange?.let { range ->
                val dueDate = task.dueDate
                if (range.start != null && range.end != null) {
                    if (dueDate == null || dueDate < range.start || dueDate > range.end) return@filter false
                } else if (range.start != null) {
                    // "No Date" filter: start == null signals we want tasks with no due date
                    if (dueDate == null || dueDate < range.start) return@filter false
                } else if (range.end != null) {
                    // end-only would be unusual but handle it
                    if (dueDate == null || dueDate > range.end) return@filter false
                } else {
                    // Both null means "No Date" filter
                    if (dueDate != null) return@filter false
                }
            }

            // Search query
            if (filter.searchQuery.isNotBlank()) {
                val q = filter.searchQuery.lowercase()
                val titleMatch = task.title.lowercase().contains(q)
                val descMatch = task.description?.lowercase()?.contains(q) == true
                if (!titleMatch && !descMatch) return@filter false
            }

            true
        }
    }

    fun onUpdateFilter(filter: TaskFilter) {
        _currentFilter.value = filter
    }

    fun onClearFilters() {
        _currentFilter.value = TaskFilter()
    }

    fun onSelectProject(projectId: Long?) {
        val current = _currentFilter.value
        _currentFilter.value = if (projectId == null) {
            current.copy(selectedProjectIds = emptyList())
        } else {
            current.copy(selectedProjectIds = listOf(projectId))
        }
        // Restore the remembered sort for the newly-selected scope: per-project
        // key when a single project is selected, otherwise the screen-wide key.
        viewModelScope.launch {
            val key = if (projectId != null) {
                SortPreferences.ScreenKeys.project(projectId)
            } else {
                SortPreferences.ScreenKeys.TASK_LIST
            }
            val saved = sortPreferences.getSortModeOrNull(key)
            SortOption.fromToken(saved)?.let { _currentSort.value = it }
        }
    }

    fun onChangeSort(sort: SortOption) {
        val previous = _currentSort.value
        _currentSort.value = sort
        viewModelScope.launch {
            // Persist per-screen so the next launch reopens with this sort.
            // Also save under the currently-selected project (if any) so the
            // choice sticks per project-scoped view.
            sortPreferences.setSortMode(SortPreferences.ScreenKeys.TASK_LIST, sort.token)
            val projectFilter = _currentFilter.value.selectedProjectIds
            if (projectFilter.size == 1) {
                projectFilter.first()?.let { singleProjectId ->
                    sortPreferences.setSortMode(
                        SortPreferences.ScreenKeys.project(singleProjectId),
                        sort.token
                    )
                }
            }
            // When the user explicitly picks Custom from the menu, hint at
            // the new interaction so they discover drag-to-reorder.
            if (sort == SortOption.CUSTOM && previous != SortOption.CUSTOM) {
                snackbarHostState.showSnackbar("Drag To Reorder Tasks")
            }
        }
    }

    fun onChangeViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun onAddTask(title: String, dueDate: Long? = null, priority: Int = 0, projectId: Long? = null) {
        viewModelScope.launch {
            try {
                taskRepository.addTask(title = title, dueDate = dueDate, priority = priority, projectId = projectId)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to add task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onAddSubtask(title: String, parentTaskId: Long, priority: Int = 0) {
        viewModelScope.launch {
            try {
                taskRepository.addSubtask(title = title, parentTaskId = parentTaskId, priority = priority)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to add subtask", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onDeleteSubtaskWithUndo(subtaskId: Long) {
        viewModelScope.launch {
            try {
                val saved = taskRepository.getTaskByIdOnce(subtaskId) ?: return@launch
                taskRepository.deleteTask(subtaskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Subtask deleted",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.insertTask(saved)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to delete subtask", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onReorderSubtasks(parentTaskId: Long, orderedIds: List<Long>) {
        viewModelScope.launch {
            try {
                taskRepository.reorderSubtasks(parentTaskId, orderedIds)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to reorder subtasks", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Persist a new order for a slice of tasks (e.g. one group in upcoming
     * view, or the full filtered list in list view). The ViewModel rebases
     * the ordering around the minimum existing sort_order of the affected
     * tasks, so reordering inside one group never disturbs the sort_order of
     * tasks in other groups.
     *
     * If the current sort mode isn't CUSTOM, also switches to CUSTOM sort and
     * surfaces a snackbar so the user knows the view just rebound to custom
     * ordering.
     */
    fun onReorderTasks(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val wasCustom = _currentSort.value == SortOption.CUSTOM
                // Rebase around the minimum sort_order currently in the affected
                // slice so that tasks outside the slice keep their spots.
                val existing = orderedIds.mapNotNull { id ->
                    taskRepository.getTaskByIdOnce(id)?.sortOrder
                }
                val base = existing.minOrNull() ?: 0
                val pairs = orderedIds.mapIndexed { index, id -> id to (base + index) }
                taskRepository.updateTaskOrder(pairs)

                if (!wasCustom) {
                    onChangeSort(SortOption.CUSTOM)
                    snackbarHostState.showSnackbar("Switched To Custom Order")
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to reorder tasks", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onToggleComplete(taskId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isCurrentlyCompleted) {
                    taskRepository.uncompleteTask(taskId)
                } else {
                    taskRepository.completeTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to toggle complete", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onToggleSubtaskComplete(subtaskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isCompleted) {
                    taskRepository.uncompleteTask(subtaskId)
                } else {
                    taskRepository.completeTask(subtaskId)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to toggle subtask", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onDeleteTask(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.deleteTask(taskId)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to delete task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onCompleteTaskWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.completeTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task completed",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.uncompleteTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to complete task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onDeleteTaskWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                val savedTask = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
                taskRepository.deleteTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task deleted",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.insertTask(savedTask)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to delete task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onRescheduleTask(taskId: Long, newDueDate: Long?) {
        viewModelScope.launch {
            try {
                val previous = taskRepository.getTaskByIdOnce(taskId)?.dueDate
                taskRepository.rescheduleTask(taskId, newDueDate)
                val label = QuickRescheduleFormatter.describe(newDueDate)
                val result = snackbarHostState.showSnackbar(
                    message = "Rescheduled to $label",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.rescheduleTask(taskId, previous)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to reschedule", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Duplicates the given task and shows a "Task Duplicated" snackbar with a
     * "View" action that opens the editor on the new copy. Subtask copying is
     * intentionally disabled here — the context menu is the fast path for
     * single-card duplication. Callers that want subtasks duplicated (e.g. the
     * editor sheet dialog) should call [TaskRepository.duplicateTask] directly
     * with includeSubtasks = true.
     */
    fun onDuplicateTask(
        taskId: Long,
        includeSubtasks: Boolean = false,
        copyDueDate: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val newId = taskRepository.duplicateTask(taskId, includeSubtasks, copyDueDate)
                if (newId <= 0L) {
                    snackbarHostState.showSnackbar("Something went wrong")
                    return@launch
                }
                val result = snackbarHostState.showSnackbar(
                    message = "Task Duplicated",
                    actionLabel = "VIEW",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    _openTaskEditorEvents.tryEmit(newId)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to duplicate task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onPlanForToday(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.planTaskForToday(taskId)
                snackbarHostState.showSnackbar(
                    message = "Planned for today",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to plan for today", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    // --- Import from JSX / file ---

    fun importFromFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: run {
                        snackbarHostState.showSnackbar("Could not read file")
                        return@launch
                    }
                importContent(content)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Import failed: ${e.message}")
            }
        }
    }

    fun importFromText(content: String) {
        viewModelScope.launch {
            try {
                importContent(content)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Import failed: ${e.message}")
            }
        }
    }

    private suspend fun importContent(content: String) {
        val parsed = todoListParser.parse(content)
        if (parsed == null) {
            snackbarHostState.showSnackbar("Could not parse to-do list format")
            return
        }

        var count = 0
        for (item in parsed.items) {
            val taskId = insertParsedItem(item, parentTaskId = null)
            if (taskId > 0) count++
            for (sub in item.subtasks) {
                insertParsedItem(sub, parentTaskId = taskId)
            }
        }

        val label = parsed.name?.let { "$it: " } ?: ""
        snackbarHostState.showSnackbar("Imported ${label}$count tasks")
    }

    private suspend fun insertParsedItem(item: ParsedTodoItem, parentTaskId: Long?): Long {
        val now = System.currentTimeMillis()
        return taskRepository.insertTask(
            TaskEntity(
                title = item.title,
                description = item.description,
                dueDate = item.dueDate,
                priority = item.priority,
                isCompleted = item.completed,
                completedAt = if (item.completed) now else null,
                parentTaskId = parentTaskId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun sortTasks(tasks: List<TaskEntity>, sort: SortOption): List<TaskEntity> =
        when (sort) {
            SortOption.DUE_DATE -> tasks.sortedWith(
                compareBy<TaskEntity> { it.dueDate == null }
                    .thenBy { it.dueDate }
                    .thenByDescending { it.priority }
            )
            SortOption.PRIORITY -> tasks.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate == null }
                    .thenBy { it.dueDate }
            )
            SortOption.CREATED -> tasks.sortedByDescending { it.createdAt }
            SortOption.URGENCY -> tasks.sortedByDescending { UrgencyScorer.calculateScore(it, weights = _urgencyWeights.value) }
            SortOption.ALPHABETICAL -> tasks.sortedBy { it.title.lowercase() }
            SortOption.CUSTOM -> tasks.sortedWith(
                compareBy<TaskEntity> { it.sortOrder }.thenBy { it.id }
            )
        }

    private fun groupByDate(tasks: List<TaskEntity>, sort: SortOption): Map<String, List<TaskEntity>> {
        val startOfToday = dayStartFlow.value
        val startOfTomorrow = startOfToday + DayBoundary.DAY_MILLIS
        val startOfDayAfterTomorrow = startOfTomorrow + DayBoundary.DAY_MILLIS
        val endOfWeek = startOfToday + 7 * DayBoundary.DAY_MILLIS

        val grouped = linkedMapOf<String, MutableList<TaskEntity>>()

        for (task in tasks) {
            val bucket = when {
                task.dueDate == null -> "No Date"
                task.dueDate < startOfToday -> "Overdue"
                task.dueDate < startOfTomorrow -> "Today"
                task.dueDate < startOfDayAfterTomorrow -> "Tomorrow"
                task.dueDate < endOfWeek -> "This Week"
                else -> "Later"
            }
            grouped.getOrPut(bucket) { mutableListOf() }.add(task)
        }

        val order = listOf("Overdue", "Today", "Tomorrow", "This Week", "Later", "No Date")
        return order
            .filter { it in grouped }
            .associateWith { sortTasks(grouped[it]!!, sort) }
    }
}
