package com.averycorp.prismtask.ui.screens.projects.roadmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.ExternalAnchorRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Read-only view-model state for the project-roadmap screen
 * (PrismTask-timeline-class scope, audit § P10 option (b) per the
 * operator's O3 override of "no UI in this PR").
 *
 * Combines the per-project phase list, risk register, and external
 * anchors with each phase's task membership and the project's
 * unphased-but-not-archived tasks. Edits happen elsewhere — this
 * surface is the dashboard, not the editor.
 */
data class ProjectRoadmapState(
    val project: ProjectEntity? = null,
    val phases: List<PhaseWithTasks> = emptyList(),
    val unphasedTasks: List<TaskEntity> = emptyList(),
    val risks: List<ProjectRiskEntity> = emptyList(),
    val anchors: List<ExternalAnchorRepository.Decoded> = emptyList()
)

data class PhaseWithTasks(
    val phase: ProjectPhaseEntity,
    val tasks: List<TaskEntity>
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRoadmapViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val taskDao: TaskDao,
    private val projectRepository: ProjectRepository,
    private val externalAnchorRepository: ExternalAnchorRepository
) : ViewModel() {
    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L

    val state: StateFlow<ProjectRoadmapState> = if (projectId <= 0) {
        flowOf(ProjectRoadmapState())
    } else {
        // observeProject returns ProjectDetail (project + milestones +
        // streak); we only need the underlying ProjectEntity for the
        // header. We re-emit on any (project | phase | risk | anchor)
        // change; per-phase task lists are pulled at emission time
        // because TaskDao doesn't expose a per-phase Flow yet and
        // re-querying is cheap relative to Compose recomposition.
        projectRepository.observeProject(projectId)
            .flatMapLatest { detail ->
                if (detail == null) flowOf(ProjectRoadmapState())
                else combine(
                    projectRepository.observePhases(projectId),
                    projectRepository.observeRisks(projectId),
                    externalAnchorRepository.observeAnchors(projectId)
                ) { phases, risks, anchors ->
                    val phaseWithTasks = phases.map { phase ->
                        PhaseWithTasks(phase, taskDao.getTasksForPhaseOnce(phase.id))
                    }
                    val unphased = taskDao.getUnphasedTasksForProjectOnce(projectId)
                    ProjectRoadmapState(
                        project = detail.project,
                        phases = phaseWithTasks,
                        unphasedTasks = unphased,
                        risks = risks,
                        anchors = anchors
                    )
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProjectRoadmapState()
    )
}
