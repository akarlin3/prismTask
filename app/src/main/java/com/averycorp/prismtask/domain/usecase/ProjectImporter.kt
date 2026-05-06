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
 * Strategy:
 *   1. Try the comprehensive parser ([ChecklistParser]) first. If the source
 *      expresses phases / risks / external anchors / task dependencies,
 *      materialise a Project + cascading entities and return [ImportOutcome.Rich].
 *   2. Otherwise fall back to the flat parser ([TodoListParser]):
 *        - When `projectifyFlat = true` (Projects screen UX), wrap the items
 *          in a new Project and return [ImportOutcome.FlatProject].
 *        - When `projectifyFlat = false` (Tasks screen UX, preserves the
 *          pre-F.8 behaviour), insert items as orphan tasks and return
 *          [ImportOutcome.FlatOrphans].
 *   3. If neither parser produces output, return [ImportOutcome.Unparseable].
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
    suspend fun importContent(content: String, projectifyFlat: Boolean): ImportOutcome {
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
        return if (projectifyFlat) materialiseFlatAsProject(flat) else materialiseFlatAsOrphans(flat)
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
