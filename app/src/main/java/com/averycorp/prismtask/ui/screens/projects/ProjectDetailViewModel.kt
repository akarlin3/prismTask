package com.averycorp.prismtask.ui.screens.projects

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.MilestoneEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.domain.model.ProjectDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing [ProjectDetailScreen].
 *
 * Expects `projectId` to be passed through [SavedStateHandle] via the
 * navigation argument. If the project is deleted while the screen is
 * open, [detail] emits `null` and the screen pops back.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModel
@Inject
constructor(
    private val projectRepository: ProjectRepository,
    private val taskDao: TaskDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    val detail: StateFlow<ProjectDetail?> = projectRepository
        .observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Top-level tasks linked to this project (subtasks are hidden from the detail list). */
    val tasks: StateFlow<List<TaskEntity>> = detail
        .flatMapLatest { d ->
            if (d == null) {
                flowOf(emptyList())
            } else {
                taskDao.getTasksByProject(d.project.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------- Project lifecycle --------------------

    fun completeProject() = launchSafely("complete project") {
        projectRepository.completeProject(projectId)
    }

    fun archiveProject() = launchSafely("archive project") {
        projectRepository.archiveProject(projectId)
    }

    fun reopenProject() = launchSafely("reopen project") {
        projectRepository.reopenProject(projectId)
    }

    fun deleteProject(onDeleted: () -> Unit) = launchSafely("delete project") {
        val project = detail.value?.project ?: return@launchSafely
        projectRepository.deleteProject(project)
        onDeleted()
    }

    // -------------------- Milestones --------------------

    fun addMilestone(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        launchSafely("add milestone") {
            projectRepository.addMilestone(projectId, trimmed)
        }
    }

    fun toggleMilestone(milestone: MilestoneEntity, completed: Boolean) =
        launchSafely("toggle milestone") {
            projectRepository.toggleMilestone(milestone, completed)
        }

    fun updateMilestoneTitle(milestone: MilestoneEntity, newTitle: String) =
        launchSafely("rename milestone") {
            val trimmed = newTitle.trim()
            if (trimmed.isEmpty() || trimmed == milestone.title) return@launchSafely
            projectRepository.updateMilestone(milestone.copy(title = trimmed))
        }

    fun deleteMilestone(milestone: MilestoneEntity) =
        launchSafely("delete milestone") {
            projectRepository.deleteMilestone(milestone)
        }

    fun reorderMilestones(orderedIds: List<Long>) =
        launchSafely("reorder milestones") {
            projectRepository.reorderMilestones(projectId, orderedIds)
        }

    private inline fun launchSafely(opLabel: String, crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e("ProjectDetailVM", "Failed to $opLabel", e)
                _errorMessages.emit("Couldn't $opLabel")
            }
        }
    }
}
