package com.averykarlin.averytask.ui.screens.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TaskRepository
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val rootTasks: StateFlow<List<TaskEntity>> = taskRepository.getIncompleteRootTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedTasks: StateFlow<Map<String, List<TaskEntity>>> = taskRepository.getTasksGroupedByDate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId

    val filteredTasks: StateFlow<List<TaskEntity>> = combine(rootTasks, _selectedProjectId) { taskList, projectId ->
        if (projectId == null) taskList else taskList.filter { it.projectId == projectId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subtasksMap: StateFlow<Map<Long, List<TaskEntity>>> = filteredTasks.flatMapLatest { tasks ->
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

    fun onSelectProject(projectId: Long?) {
        _selectedProjectId.value = projectId
    }

    fun onAddTask(title: String, dueDate: Long? = null, priority: Int = 0, projectId: Long? = null) {
        viewModelScope.launch {
            taskRepository.addTask(title = title, dueDate = dueDate, priority = priority, projectId = projectId)
        }
    }

    fun onAddSubtask(title: String, parentTaskId: Long) {
        viewModelScope.launch {
            taskRepository.addSubtask(title = title, parentTaskId = parentTaskId)
        }
    }

    fun onToggleComplete(taskId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyCompleted) {
                taskRepository.uncompleteTask(taskId)
            } else {
                taskRepository.completeTask(taskId)
            }
        }
    }

    fun onToggleSubtaskComplete(subtaskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            if (isCompleted) {
                taskRepository.uncompleteTask(subtaskId)
            } else {
                taskRepository.completeTask(subtaskId)
            }
        }
    }

    fun onDeleteTask(taskId: Long) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
        }
    }
}
