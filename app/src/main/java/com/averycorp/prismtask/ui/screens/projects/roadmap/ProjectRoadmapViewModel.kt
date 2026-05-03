package com.averycorp.prismtask.ui.screens.projects.roadmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.ExternalAnchorRepository
import com.averycorp.prismtask.data.repository.ProjectPhaseRepository
import com.averycorp.prismtask.data.repository.ProjectRiskRepository
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
 * State for the project-roadmap view-mode UI (audit § P10 option b).
 *
 * The screen is read-only by default — the operator's choice was option
 * (b) "add view-mode UI", not (c) full editor — so the view model only
 * exposes Flows. Add/Edit lives in dedicated downstream screens that
 * the navigator can wire later.
 */
data class ProjectRoadmapState(
    val project: ProjectEntity? = null,
    val phases: List<PhaseWithTasks> = emptyList(),
    val unphasedTasks: List<TaskEntity> = emptyList(),
    val risks: List<ProjectRiskEntity> = emptyList(),
    val anchors: List<ExternalAnchorRepository.ResolvedAnchor> = emptyList()
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
    projectDao: ProjectDao,
    private val taskDao: TaskDao,
    private val phaseRepository: ProjectPhaseRepository,
    private val riskRepository: ProjectRiskRepository,
    private val anchorRepository: ExternalAnchorRepository
) : ViewModel() {
    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L

    val state: StateFlow<ProjectRoadmapState> = if (projectId <= 0) {
        flowOf(ProjectRoadmapState())
    } else {
        // Re-emit the per-project projection whenever the phase list
        // changes; tasks are pulled per-phase via taskDao on each
        // emission. The TaskDao stream wouldn't help here because we
        // need a per-phase grouping that the DAO doesn't pre-compute.
        projectDao.getProjectById(projectId)
            .flatMapLatest { project ->
                if (project == null) {
                    flowOf(ProjectRoadmapState())
                } else {
                    combine(
                        phaseRepository.observePhases(projectId),
                        riskRepository.observeRisks(projectId),
                        anchorRepository.observeAnchorsForProject(projectId)
                    ) { phases, risks, anchors ->
                        val phaseWithTasks = phases.map { phase ->
                            PhaseWithTasks(phase, taskDao.getTasksForPhaseOnce(phase.id))
                        }
                        val unphased = taskDao.getUnphasedTasksForProjectOnce(projectId)
                        ProjectRoadmapState(
                            project = project,
                            phases = phaseWithTasks,
                            unphasedTasks = unphased,
                            risks = risks,
                            anchors = anchors
                        )
                    }
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProjectRoadmapState()
    )
}
