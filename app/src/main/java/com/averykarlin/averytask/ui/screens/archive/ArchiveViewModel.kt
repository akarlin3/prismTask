package com.averykarlin.averytask.ui.screens.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val archivedTasks: StateFlow<List<TaskEntity>> = taskRepository.getArchivedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedCount: StateFlow<Int> = taskRepository.getArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val filteredArchive: StateFlow<List<TaskEntity>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                taskRepository.getArchivedTasks()
            } else {
                taskRepository.searchArchivedTasks(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onUnarchive(taskId: Long) {
        viewModelScope.launch {
            taskRepository.unarchiveTask(taskId)
        }
    }

    fun onPermanentlyDelete(taskId: Long) {
        viewModelScope.launch {
            taskRepository.permanentlyDeleteTask(taskId)
        }
    }

    fun onClearAllArchived() {
        viewModelScope.launch {
            archivedTasks.value.forEach { task ->
                taskRepository.permanentlyDeleteTask(task.id)
            }
        }
    }
}
