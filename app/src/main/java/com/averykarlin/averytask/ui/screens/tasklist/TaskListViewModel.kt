package com.averykarlin.averytask.ui.screens.tasklist

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.repository.AttachmentRepository
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TagRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import com.averykarlin.averytask.domain.model.TagFilterMode
import com.averykarlin.averytask.domain.model.TaskFilter
import com.averykarlin.averytask.domain.usecase.UrgencyScorer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class SortOption(val label: String) {
    DUE_DATE("Due Date"),
    PRIORITY("Priority"),
    URGENCY("Urgency"),
    CREATED("Date Created"),
    ALPHABETICAL("Alphabetical")
}

enum class ViewMode(val label: String) {
    UPCOMING("Upcoming"),
    LIST("List"),
    WEEK("Week"),
    MONTH("Month")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val tagRepository: TagRepository,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    private val rootTasks: StateFlow<List<TaskEntity>> = taskRepository.getIncompleteRootTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // For filter: get ALL root tasks (including completed/archived) so filters can show them
    private val allRootTasks: StateFlow<List<TaskEntity>> = taskRepository.getAllTasks()
        .map { tasks -> tasks.filter { it.parentTaskId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val overdueCount: StateFlow<Int> = rootTasks.map { tasks ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
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
        viewModelScope.launch {
            try {
                ids.forEach { id ->
                    val task = taskRepository.getTaskByIdOnce(id)
                    if (task != null) {
                        taskRepository.updateTask(task.copy(priority = priority))
                    }
                }
                onExitMultiSelect()
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to bulk set priority", e)
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
    }

    fun onChangeSort(sort: SortOption) {
        _currentSort.value = sort
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

    fun onAddSubtask(title: String, parentTaskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.addSubtask(title = title, parentTaskId = parentTaskId)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to add subtask", e)
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
            SortOption.URGENCY -> tasks.sortedByDescending { UrgencyScorer.calculateScore(it) }
            SortOption.ALPHABETICAL -> tasks.sortedBy { it.title.lowercase() }
        }

    private fun groupByDate(tasks: List<TaskEntity>, sort: SortOption): Map<String, List<TaskEntity>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startOfTomorrow = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startOfDayAfterTomorrow = calendar.timeInMillis

        calendar.timeInMillis = startOfToday
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val endOfWeek = calendar.timeInMillis

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
