package com.averycorp.prismtask.ui.screens.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.domain.model.ProjectStatus
import com.averycorp.prismtask.domain.model.ProjectWithProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Projects side of the Tasks tab segmented toggle.
 *
 * State is scoped to the pane (not the parent TaskList screen) so switching
 * sides and coming back preserves the filter chip selection. The selected
 * status is also persisted into [SavedStateHandle] so it survives process
 * death.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsPaneViewModel
@Inject
constructor(
    private val projectRepository: ProjectRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    companion object {
        const val KEY_STATUS_FILTER = "projects_pane_status_filter"
    }

    /** `null` = All. Otherwise one of [ProjectStatus]. */
    private val _statusFilter = MutableStateFlow(
        savedStateHandle.get<String?>(KEY_STATUS_FILTER)
            ?.let { raw -> runCatching { ProjectStatus.valueOf(raw) }.getOrNull() }
            ?: ProjectStatus.ACTIVE
    )
    val statusFilter: StateFlow<ProjectStatus?> = _statusFilter.asStateFlow()

    val projects: StateFlow<List<ProjectWithProgress>> = _statusFilter
        .flatMapLatest { status -> projectRepository.observeProjectsWithProgress(status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStatusFilter(status: ProjectStatus?) {
        _statusFilter.value = status
        savedStateHandle[KEY_STATUS_FILTER] = status?.name
    }

    fun archiveProject(projectId: Long) {
        viewModelScope.launch { projectRepository.archiveProject(projectId) }
    }

    fun completeProject(projectId: Long) {
        viewModelScope.launch { projectRepository.completeProject(projectId) }
    }

    fun reopenProject(projectId: Long) {
        viewModelScope.launch { projectRepository.reopenProject(projectId) }
    }
}
