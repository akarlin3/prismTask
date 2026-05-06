package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.ExternalAnchorRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskDependencyRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.model.ExternalAnchor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared schedule-import orchestrator for the F.8 "Import Project from
 * Schedule File" feature. Both [com.averycorp.prismtask.ui.screens.projects.ProjectListViewModel]
 * and [com.averycorp.prismtask.ui.screens.tasklist.TaskListViewModel] delegate
 * here so the rich path (phases / risks / anchors / dependencies) lights up
 * from either entry point.
 *
 * The caller passes `createProject` based on the user's choice in the
 * import dialog ("Import as new project?" checkbox). It controls the whole
 * orchestration:
 *
 * - `createProject = true`:
 *     - If the source expresses phases / risks / anchors / dependencies →
 *       materialise the full Project + cascading entities → [ImportOutcome.Rich].
 *     - Otherwise wrap the parsed flat items in a new Project → [ImportOutcome.FlatProject].
 * - `createProject = false`:
 *     - Skip the rich parser entirely (it's only useful when we're going
 *       to create a project tree). Use the flat parser and insert items as
 *       orphan tasks (no project) → [ImportOutcome.FlatOrphans]. Any rich
 *       structure in the source is intentionally ignored — the user opted
 *       out of project creation.
 * - Either path: if no parser produces output → [ImportOutcome.Unparseable].
 */
@Singleton
class ProjectImporter @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val externalAnchorRepository: ExternalAnchorRepository,
    private val taskDependencyRepository: TaskDependencyRepository,
    private val checklistParser: ChecklistParser,
    private val todoListParser: TodoListParser
) {
    suspend fun importContent(content: String, createProject: Boolean): ImportOutcome {
        if (!createProject) {
            // User opted out of project creation. Skip the (more expensive)
            // rich parser — its only output is project-shaped — and insert
            // the flat parse as orphan tasks.
            val flat = todoListParser.parse(content) ?: return ImportOutcome.Unparseable
            return materialiseFlatAsOrphans(flat)
        }

        val checklist = checklistParser.parse(content)
        val hasRichExtras = checklist != null && (
            checklist.phases.isNotEmpty() ||
                checklist.risks.isNotEmpty() ||
                checklist.externalAnchors.isNotEmpty() ||
                checklist.taskDependencies.isNotEmpty()
            )
        if (checklist != null && hasRichExtras) {
            return materialiseRich(checklist)
        }

        val flat = todoListParser.parse(content) ?: return ImportOutcome.Unparseable
        return materialiseFlatAsProject(flat)
    }

    private suspend fun materialiseRich(result: ComprehensiveImportResult): ImportOutcome.Rich {
        val projectId = projectRepository.addProject(
            name = result.project.name,
            color = result.project.color,
            icon = result.project.icon
        )

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

        val taskIdsByTitle = mutableMapOf<String, Long>()
        var taskCount = 0
        for (task in result.tasks) {
            val taskId = insertChecklistTask(task, projectId, parentTaskId = null)
            if (taskId > 0) {
                taskCount++
                taskIdsByTitle[task.title] = taskId
            }
            for (sub in task.subtasks) {
                insertChecklistTask(sub, projectId, parentTaskId = taskId)
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

        return ImportOutcome.Rich(
            projectName = result.project.name,
            taskCount = taskCount,
            phaseCount = result.phases.size,
            riskCount = result.risks.size
        )
    }

    private suspend fun materialiseFlatAsProject(parsed: ParsedTodoList): ImportOutcome.FlatProject {
        val projectName = parsed.name ?: "Imported List"
        val projectId = projectRepository.addProject(name = projectName)
        var count = 0
        for (item in parsed.items) {
            val taskId = insertParsedItem(item, projectId, parentTaskId = null)
            if (taskId > 0) count++
            for (sub in item.subtasks) insertParsedItem(sub, projectId, parentTaskId = taskId)
        }
        return ImportOutcome.FlatProject(projectName = projectName, taskCount = count)
    }

    private suspend fun materialiseFlatAsOrphans(parsed: ParsedTodoList): ImportOutcome.FlatOrphans {
        var count = 0
        for (item in parsed.items) {
            val taskId = insertParsedItem(item, projectId = null, parentTaskId = null)
            if (taskId > 0) count++
            for (sub in item.subtasks) insertParsedItem(sub, projectId = null, parentTaskId = taskId)
        }
        return ImportOutcome.FlatOrphans(listName = parsed.name, taskCount = count)
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

    private suspend fun insertParsedItem(
        item: ParsedTodoItem,
        projectId: Long?,
        parentTaskId: Long?
    ): Long {
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

sealed interface ImportOutcome {
    data class Rich(
        val projectName: String,
        val taskCount: Int,
        val phaseCount: Int,
        val riskCount: Int
    ) : ImportOutcome

    data class FlatProject(val projectName: String, val taskCount: Int) : ImportOutcome

    data class FlatOrphans(val listName: String?, val taskCount: Int) : ImportOutcome

    object Unparseable : ImportOutcome
}
