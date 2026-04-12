package com.averycorp.prismtask.ui.screens.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskCompletionRepository
import com.averycorp.prismtask.data.repository.TaskCompletionStats
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

enum class AnalyticsPeriod(val days: Int, val label: String) {
    WEEK(7, "7 Days"),
    MONTH(30, "30 Days"),
    QUARTER(90, "90 Days"),
    YEAR(365, "Year")
}

data class TaskAnalyticsState(
    val stats: TaskCompletionStats? = null,
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val selectedProjectId: Long? = null,
    val projects: List<ProjectEntity> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskAnalyticsViewModel @Inject constructor(
    private val taskCompletionRepository: TaskCompletionRepository,
    private val projectRepository: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialProjectId: Long? = savedStateHandle.get<Long>("projectId")
        ?.takeIf { it > 0 }

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)
    private val _selectedProjectId = MutableStateFlow(initialProjectId)

    val state: StateFlow<TaskAnalyticsState> = combine(
        _selectedPeriod,
        _selectedProjectId,
        combine(_selectedPeriod, _selectedProjectId) { period, projectId ->
            if (projectId != null) {
                taskCompletionRepository.getProjectStats(projectId, period.days)
            } else {
                taskCompletionRepository.getCompletionStats(period.days)
            }
        }.flatMapLatest { it },
        projectRepository.getAllProjects()
    ) { period, projectId, stats, projects ->
        TaskAnalyticsState(
            stats = stats,
            selectedPeriod = period,
            selectedProjectId = projectId,
            projects = projects,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TaskAnalyticsState()
    )

    fun setPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
    }

    fun setProject(projectId: Long?) {
        _selectedProjectId.value = projectId
    }
}
