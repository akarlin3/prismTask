package com.averycorp.prismtask.ui.screens.projects

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.ProjectWithCount
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.ParsedTodoItem
import com.averycorp.prismtask.domain.usecase.TodoListParser
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
        private val todoListParser: TodoListParser,
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
                    snackbarHostState.showSnackbar("Something went wrong")
                }
            }
        }

        // --- Import from JSX / file ---

        fun importFromFile(context: Context, uri: Uri) {
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
                    importContent(content)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Import failed: ${e.message}")
                }
            }
        }

        fun importFromText(content: String) {
            viewModelScope.launch {
                try {
                    importContent(content)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Import failed: ${e.message}")
                }
            }
        }

        private suspend fun importContent(content: String) {
            val parsed = todoListParser.parse(content)
            if (parsed == null) {
                snackbarHostState.showSnackbar("Could not parse to-do list format")
                return
            }

            val projectName = parsed.name ?: "Imported List"
            val projectId = projectRepository.addProject(name = projectName)

            var count = 0
            for (item in parsed.items) {
                val taskId = insertParsedItem(item, projectId = projectId, parentTaskId = null)
                if (taskId > 0) count++
                for (sub in item.subtasks) {
                    insertParsedItem(sub, projectId = projectId, parentTaskId = taskId)
                }
            }

            snackbarHostState.showSnackbar("Imported \"$projectName\": $count tasks")
        }

        private suspend fun insertParsedItem(item: ParsedTodoItem, projectId: Long, parentTaskId: Long?): Long {
            val now = System.currentTimeMillis()
            return taskRepository.insertTask(
                TaskEntity(
                    title = item.title,
                    description = item.description,
                    dueDate = item.dueDate,
                    priority = item.priority,
                    isCompleted = item.completed,
                    completedAt = if (item.completed) now else null,
                    projectId = projectId,
                    parentTaskId = parentTaskId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }
