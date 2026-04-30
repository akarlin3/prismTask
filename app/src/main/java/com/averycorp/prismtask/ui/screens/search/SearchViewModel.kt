package com.averycorp.prismtask.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val projectRepository: ProjectRepository,
    private val advancedTuningPreferences: com.averycorp.prismtask.data.preferences.AdvancedTuningPreferences
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    /** User-configurable description preview lines for search results (E5). */
    val descriptionPreviewLines: StateFlow<Int> =
        advancedTuningPreferences.getSearchPreview()
            .map { it.previewLines }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    private val debouncedQuery = searchQuery
        .debounce(300L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val taskResults: StateFlow<List<TaskEntity>> = debouncedQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                taskRepository.searchTasks(query)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tagResults: StateFlow<List<TagEntity>> = debouncedQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                tagRepository.searchTags(query)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectResults: StateFlow<List<ProjectEntity>> = debouncedQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                projectRepository.searchProjects(query)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onClearQuery() {
        searchQuery.value = ""
    }
}
