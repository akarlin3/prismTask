package com.averycorp.prismtask.ui.screens.projects

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.ProjectWithCount
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.ImportOutcome
import com.averycorp.prismtask.domain.usecase.ProjectImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel
@Inject
constructor(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val projectImporter: ProjectImporter,
    private val sortPreferences: SortPreferences
) : ViewModel() {
    val snackbarHostState = SnackbarHostState()

    val projects: StateFlow<List<ProjectWithCount>> = projectRepository
        .getProjectWithTaskCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Reactive per-project sort mode, keyed by the dynamic
     * `sort_project_{projectId}` preference. UI that surfaces a per-project
     * sort dropdown can collect this for a single project.
     */
    fun observeProjectSort(projectId: Long): Flow<String> =
        sortPreferences.observeSortMode(SortPreferences.ScreenKeys.project(projectId))

    fun onChangeProjectSort(projectId: Long, sortMode: String) {
        viewModelScope.launch {
            sortPreferences.setSortMode(SortPreferences.ScreenKeys.project(projectId), sortMode)
        }
    }

    fun onDeleteProject(project: ProjectEntity, deleteTasks: Boolean = false) {
        viewModelScope.launch {
            try {
                if (deleteTasks) {
                    taskRepository.deleteTasksByProjectId(project.id)
                }
                projectRepository.deleteProject(project)
            } catch (e: Exception) {
                Log.e("ProjectListVM", "Failed to delete project", e)
                snackbarHostState.showSnackbar("Couldn't delete project")
            }
        }
    }

    // --- Import from JSX / file ---

    fun importFromFile(context: Context, uri: Uri, asProject: Boolean) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: run {
                        snackbarHostState.showSnackbar("Could not read file")
                        return@launch
                    }
                importContent(content, asProject)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Import failed")
            }
        }
    }

    fun importFromText(content: String, asProject: Boolean) {
        viewModelScope.launch {
            try {
                importContent(content, asProject)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Import failed")
            }
        }
    }

    private suspend fun importContent(content: String, asProject: Boolean) {
        when (val outcome = projectImporter.importContent(content, createProject = asProject)) {
            is ImportOutcome.Rich -> snackbarHostState.showSnackbar(
                "Imported \"${outcome.projectName}\": ${outcome.taskCount} tasks, " +
                    "${outcome.phaseCount} phases, ${outcome.riskCount} risks"
            )
            is ImportOutcome.FlatProject -> snackbarHostState.showSnackbar(
                "Imported \"${outcome.projectName}\": ${outcome.taskCount} tasks"
            )
            is ImportOutcome.FlatOrphans -> {
                val label = outcome.listName?.let { "$it: " } ?: ""
                snackbarHostState.showSnackbar("Imported ${label}${outcome.taskCount} tasks")
            }
            ImportOutcome.Unparseable -> snackbarHostState.showSnackbar(
                "Could not parse to-do list format"
            )
        }
    }
}
