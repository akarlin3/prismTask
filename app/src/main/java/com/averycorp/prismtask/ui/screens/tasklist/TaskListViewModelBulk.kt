package com.averycorp.prismtask.ui.screens.tasklist

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.ui.components.QuickRescheduleFormatter
import kotlinx.coroutines.launch

/**
 * Bulk-edit action handlers extracted from [TaskListViewModel]. These all
 * run through the multi-select mode: they read [TaskListViewModel.selectedTaskIds],
 * dispatch batch repository calls, then clear the selection and surface an
 * Undo snackbar.
 */
internal fun TaskListViewModel.onBulkComplete() {
    val ids = selectedTaskIds.value.toList()
    viewModelScope.launch {
        try {
            ids.forEach { taskRepository.completeTask(it) }
            onExitMultiSelect()
            val result = snackbarHostState.showSnackbar(
                message = "${ids.size} tasks completed",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                ids.forEach { taskRepository.uncompleteTask(it) }
            }
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to bulk complete", e)
            snackbarHostState.showSnackbar("Something went wrong")
        }
    }
}

internal fun TaskListViewModel.onBulkDelete() {
    val ids = selectedTaskIds.value.toList()
    viewModelScope.launch {
        try {
            val savedTasks = ids.mapNotNull { taskRepository.getTaskByIdOnce(it) }
            ids.forEach { taskRepository.deleteTask(it) }
            onExitMultiSelect()
            val result = snackbarHostState.showSnackbar(
                message = "${ids.size} tasks deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                savedTasks.forEach { taskRepository.insertTask(it) }
            }
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to bulk delete", e)
            snackbarHostState.showSnackbar("Something went wrong")
        }
    }
}

internal fun TaskListViewModel.onBulkSetPriority(priority: Int) {
    val ids = selectedTaskIds.value.toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
        try {
            val previous = ids.mapNotNull { id ->
                taskRepository.getTaskByIdOnce(id)?.let { it.id to it.priority }
            }
            taskRepository.batchUpdatePriority(ids, priority)
            onExitMultiSelect()
            val result = snackbarHostState.showSnackbar(
                message = "Updated Priority for ${ids.size} Tasks",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                previous.groupBy { it.second }.forEach { (prio, group) ->
                    taskRepository.batchUpdatePriority(group.map { it.first }, prio)
                }
            }
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to bulk set priority", e)
            snackbarHostState.showSnackbar("Something went wrong")
        }
    }
}

internal fun TaskListViewModel.onBulkReschedule(newDueDate: Long?) {
    val ids = selectedTaskIds.value.toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
        try {
            val previous = ids.mapNotNull { id ->
                taskRepository.getTaskByIdOnce(id)?.let { it.id to it.dueDate }
            }
            taskRepository.batchReschedule(ids, newDueDate)
            onExitMultiSelect()
            val label = QuickRescheduleFormatter.describe(newDueDate)
            val result = snackbarHostState.showSnackbar(
                message = "Rescheduled ${ids.size} Tasks to $label",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                previous.groupBy { it.second }.forEach { (dueDate, group) ->
                    taskRepository.batchReschedule(group.map { it.first }, dueDate)
                }
            }
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to bulk reschedule", e)
            snackbarHostState.showSnackbar("Something went wrong")
        }
    }
}

internal fun TaskListViewModel.onBulkMoveToProject(newProjectId: Long?) {
    val ids = selectedTaskIds.value.toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
        try {
            val previous = ids.mapNotNull { id ->
                taskRepository.getTaskByIdOnce(id)?.let { it.id to it.projectId }
            }
            taskRepository.batchMoveToProject(ids, newProjectId)
            onExitMultiSelect()
            val projectName = newProjectId?.let { id ->
                projects.value.find { it.id == id }?.name
            } ?: "No Project"
            val result = snackbarHostState.showSnackbar(
                message = "Moved ${ids.size} Tasks to $projectName",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                previous.groupBy { it.second }.forEach { (projectId, group) ->
                    taskRepository.batchMoveToProject(group.map { it.first }, projectId)
                }
            }
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to bulk move", e)
            snackbarHostState.showSnackbar("Something went wrong")
        }
    }
}

/**
 * Applies tag changes from the bulk-tags dialog. [addIds] are tag ids that
 * should be present on every selected task after the operation; [removeIds]
 * are tag ids that should be absent. Captures the previous tag set per task
 * so Undo can restore the original assignments.
 */
internal fun TaskListViewModel.onBulkApplyTags(addIds: Set<Long>, removeIds: Set<Long>) {
    val ids = selectedTaskIds.value.toList()
    if (ids.isEmpty() || (addIds.isEmpty() && removeIds.isEmpty())) return
    viewModelScope.launch {
        try {
            val snapshot: Map<Long, Set<Long>> = ids.associateWith { id ->
                taskTagsMap.value[id]
                    .orEmpty()
                    .map { it.id }
                    .toSet()
            }
            addIds.forEach { tagId -> taskRepository.batchAddTag(ids, tagId) }
            removeIds.forEach { tagId -> taskRepository.batchRemoveTag(ids, tagId) }
            onExitMultiSelect()
            val result = snackbarHostState.showSnackbar(
                message = "Updated Tags for ${ids.size} Tasks",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                for (tagId in addIds) {
                    val toRemove = snapshot.filterValues { tagId !in it }.keys.toList()
                    if (toRemove.isNotEmpty()) taskRepository.batchRemoveTag(toRemove, tagId)
                }
                for (tagId in removeIds) {
                    val toAdd = snapshot.filterValues { tagId in it }.keys.toList()
                    if (toAdd.isNotEmpty()) taskRepository.batchAddTag(toAdd, tagId)
                }
            }
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to bulk apply tags", e)
            snackbarHostState.showSnackbar("Something went wrong")
        }
    }
}

internal fun TaskListViewModel.onBulkCreateProjectAndMove(name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
        try {
            val newId = projectRepository.addProject(name.trim())
            onBulkMoveToProject(newId)
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to create project", e)
            snackbarHostState.showSnackbar("Something went wrong")
        }
    }
}
