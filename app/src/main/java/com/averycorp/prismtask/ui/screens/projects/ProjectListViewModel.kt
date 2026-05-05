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
import com.averycorp.prismtask.data.repository.ExternalAnchorRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskDependencyRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.model.ExternalAnchor
import com.averycorp.prismtask.domain.usecase.ChecklistParsedTask
import com.averycorp.prismtask.domain.usecase.ChecklistParser
import com.averycorp.prismtask.domain.usecase.ComprehensiveImportResult
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
    private val checklistParser: ChecklistParser,
    private val externalAnchorRepository: ExternalAnchorRepository,
    private val taskDependencyRepository: TaskDependencyRepository,
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
                snackbarHostState.showSnackbar("Import failed")
            }
        }
    }

    fun importFromText(content: String) {
        viewModelScope.launch {
            try {
                importContent(content)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Import failed")
            }
        }
    }

    private suspend fun importContent(content: String) {
        // Try the richer checklist parser first — when the source has phases /
        // risks / anchors / dependencies (F.8), this materialises the full
        // project-import schema. The simple todoListParser stays as a fallback
        // for sources that don't shape into the comprehensive schema.
        val checklist = checklistParser.parse(content)
        val hasRichExtras = checklist != null && (
            checklist.phases.isNotEmpty() ||
                checklist.risks.isNotEmpty() ||
                checklist.externalAnchors.isNotEmpty() ||
                checklist.taskDependencies.isNotEmpty()
            )
        if (checklist != null && hasRichExtras) {
            importComprehensive(checklist)
            return
        }

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

    private suspend fun importComprehensive(result: ComprehensiveImportResult) {
        val projectId = projectRepository.addProject(
            name = result.project.name,
            color = result.project.color,
            icon = result.project.icon
        )

        // Phases: name → id, used to resolve task.phaseName + anchor.phaseName.
        val phaseIdsByName = mutableMapOf<String, Long>()
        for (phase in result.phases) {
            val id = projectRepository.addPhase(
                projectId = projectId,
                title = phase.name,
                description = phase.description,
                startDate = phase.startDate,
                endDate = phase.endDate
            )
            phaseIdsByName[phase.name] = id
        }

        // Tasks: title → id, used to resolve dependency.blockerTitle / blockedTitle.
        val taskIdsByTitle = mutableMapOf<String, Long>()
        var taskCount = 0
        for (task in result.tasks) {
            val taskId = insertChecklistTask(
                task = task,
                projectId = projectId,
                parentTaskId = null
            )
            if (taskId > 0) {
                taskCount++
                taskIdsByTitle[task.title] = taskId
            }
            for (sub in task.subtasks) {
                insertChecklistTask(task = sub, projectId = projectId, parentTaskId = taskId)
            }
        }

        for (risk in result.risks) {
            projectRepository.addRisk(
                projectId = projectId,
                title = risk.title,
                level = risk.level.uppercase().takeIf { it in setOf("LOW", "MEDIUM", "HIGH") } ?: "MEDIUM",
                mitigation = risk.description
            )
        }

        for (anchor in result.externalAnchors) {
            // v1 only materialises calendar_deadline anchors. NumericThreshold
            // and BooleanGate require info the import prompt doesn't extract;
            // skip silently rather than fail the import.
            if (anchor.type != "calendar_deadline" || anchor.targetDate == null) continue
            externalAnchorRepository.addAnchor(
                projectId = projectId,
                label = anchor.title,
                anchor = ExternalAnchor.CalendarDeadline(epochMs = anchor.targetDate),
                phaseId = anchor.phaseName?.let(phaseIdsByName::get)
            )
        }

        for (dep in result.taskDependencies) {
            val blockerId = taskIdsByTitle[dep.blockerTitle] ?: continue
            val blockedId = taskIdsByTitle[dep.blockedTitle] ?: continue
            taskDependencyRepository.addDependency(
                blockerTaskId = blockerId,
                blockedTaskId = blockedId
            )
        }

        snackbarHostState.showSnackbar(
            "Imported \"${result.project.name}\": $taskCount tasks, " +
                "${result.phases.size} phases, ${result.risks.size} risks"
        )
    }

    private suspend fun insertChecklistTask(
        task: ChecklistParsedTask,
        projectId: Long,
        parentTaskId: Long?
    ): Long {
        val now = System.currentTimeMillis()
        return taskRepository.insertTask(
            TaskEntity(
                title = task.title,
                description = task.description,
                dueDate = task.dueDate,
                priority = task.priority,
                isCompleted = task.completed,
                completedAt = if (task.completed) now else null,
                projectId = projectId,
                parentTaskId = parentTaskId,
                createdAt = now,
                updatedAt = now
            )
        )
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
