package com.averycorp.prismtask.ui.screens.tasklist.components

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.SwipePrefs
import com.averycorp.prismtask.domain.model.TaskFilter
import com.averycorp.prismtask.ui.components.CircularCheckbox
import com.averycorp.prismtask.ui.components.SubtaskSection
import com.averycorp.prismtask.ui.screens.tasklist.TaskListViewModel
import com.averycorp.prismtask.ui.theme.LocalPriorityColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TodayOrange = Color(0xFFE8872A)

private data class TaskEditorSheetState(
    val taskId: Long? = null,
    val projectId: Long? = null,
    val initialDate: Long? = null
)

internal data class DuplicateDialogState(
    val taskId: Long,
    val dueDate: Long?,
    val subtaskCount: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
internal fun androidx.compose.foundation.lazy.LazyListScope.reorderableTaskItemWithSubtasks(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    subtasksMap: Map<Long, List<TaskEntity>>,
    taskTagsMap: Map<Long, List<TagEntity>>,
    attachmentCountMap: Map<Long, Int>,
    expandedTaskIds: Set<Long>,
    focusSubtaskForId: Long?,
    onTaskClick: (Long) -> Unit,
    onReschedule: (TaskEntity) -> Unit,
    onMoveToProject: (TaskEntity) -> Unit,
    viewModel: TaskListViewModel,
    isMultiSelectMode: Boolean,
    selectedTaskIds: Set<Long>,
    onExpandChange: (Set<Long>) -> Unit,
    onFocusChange: (Long?) -> Unit,
    reorderState: ReorderableLazyListState,
    onDragEnd: () -> Unit,
    onDuplicate: (DuplicateDialogState) -> Unit
) {
    val subtasks = subtasksMap[task.id].orEmpty()
    val tags = taskTagsMap[task.id].orEmpty()
    val attachmentCount = attachmentCountMap[task.id] ?: 0
    val project = projects.find { it.id == task.projectId }

    item(key = task.id) {
        ReorderableItem(reorderState, key = task.id) { isDragging ->
            val elevation = if (isDragging) 8.dp else 0.dp
            val scale = if (isDragging) 1.02f else 1f
            val alpha = if (isDragging) 0.85f else 1f

            if (isMultiSelectMode) {
                TaskItem(
                    task = task,
                    project = project,
                    subtasks = subtasks,
                    tags = tags,
                    attachmentCount = attachmentCount,
                    isSelected = task.id in selectedTaskIds,
                    isMultiSelectMode = true,
                    onToggleComplete = { viewModel.onToggleTaskSelection(task.id) },
                    onClick = { viewModel.onToggleTaskSelection(task.id) },
                    onLongClick = { viewModel.onToggleTaskSelection(task.id) },
                    onAddSubtaskClick = {}
                )
            } else {
                TaskItem(
                    task = task,
                    project = project,
                    subtasks = subtasks,
                    tags = tags,
                    attachmentCount = attachmentCount,
                    onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                    onClick = { onTaskClick(task.id) },
                    onAddSubtaskClick = {
                        onExpandChange(expandedTaskIds + task.id)
                        onFocusChange(task.id)
                    },
                    onDuplicate = {
                        onDuplicate(
                            DuplicateDialogState(
                                taskId = task.id,
                                dueDate = task.dueDate,
                                subtaskCount = subtasks.size
                            )
                        )
                    },
                    showDragHandle = true,
                    dragHandleModifier = Modifier.draggableHandle(
                        onDragStopped = { onDragEnd() }
                    ),
                    modifier = Modifier
                        .shadow(elevation, RoundedCornerShape(12.dp))
                        .scale(scale)
                        .alpha(alpha)
                )
            }
        }
    }
    if (subtasks.isNotEmpty() || expandedTaskIds.contains(task.id)) {
        item(key = "subtasks_${task.id}") {
            SubtaskSection(
                parentTaskId = task.id,
                subtasks = subtasks,
                onToggleComplete = viewModel::onToggleSubtaskComplete,
                onAddSubtask = { title, parentId, priority ->
                    viewModel.onAddSubtask(title, parentId, priority)
                },
                onDeleteSubtask = viewModel::onDeleteSubtaskWithUndo,
                onReorderSubtasks = viewModel::onReorderSubtasks,
                expanded = expandedTaskIds.contains(task.id),
                onToggleExpand = {
                    onExpandChange(
                        if (expandedTaskIds.contains(task.id)) {
                            expandedTaskIds - task.id
                        } else {
                            expandedTaskIds + task.id
                        }
                    )
                },
                requestFocus = focusSubtaskForId == task.id,
                onFocusHandled = { onFocusChange(null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
internal fun androidx.compose.foundation.lazy.LazyListScope.taskItemWithSubtasks(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    subtasksMap: Map<Long, List<TaskEntity>>,
    taskTagsMap: Map<Long, List<TagEntity>>,
    attachmentCountMap: Map<Long, Int>,
    expandedTaskIds: Set<Long>,
    focusSubtaskForId: Long?,
    onTaskClick: (Long) -> Unit,
    onReschedule: (TaskEntity) -> Unit,
    onMoveToProject: (TaskEntity) -> Unit,
    viewModel: TaskListViewModel,
    isMultiSelectMode: Boolean,
    selectedTaskIds: Set<Long>,
    onExpandChange: (Set<Long>) -> Unit,
    onFocusChange: (Long?) -> Unit,
    onDuplicate: (DuplicateDialogState) -> Unit,
    swipePrefs: SwipePrefs
) {
    val subtasks = subtasksMap[task.id].orEmpty()
    val tags = taskTagsMap[task.id].orEmpty()
    val attachmentCount = attachmentCountMap[task.id] ?: 0
    item(key = task.id) {
        val project = projects.find { it.id == task.projectId }

        if (isMultiSelectMode) {
            TaskItem(
                task = task,
                project = project,
                subtasks = subtasks,
                tags = tags,
                attachmentCount = attachmentCount,
                isSelected = task.id in selectedTaskIds,
                isMultiSelectMode = true,
                onToggleComplete = { viewModel.onToggleTaskSelection(task.id) },
                onClick = { viewModel.onToggleTaskSelection(task.id) },
                onLongClick = { viewModel.onToggleTaskSelection(task.id) },
                onAddSubtaskClick = {}
            )
        } else {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val tomorrowBlue = Color(0xFF5C8CC7)
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    val action = when (value) {
                        SwipeToDismissBoxValue.StartToEnd -> swipePrefs.right
                        SwipeToDismissBoxValue.EndToStart -> swipePrefs.left
                        SwipeToDismissBoxValue.Settled -> com.averycorp.prismtask.domain.model.SwipeAction.NONE
                    }
                    if (action == com.averycorp.prismtask.domain.model.SwipeAction.NONE) return@rememberSwipeToDismissBoxState false
                    try {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    } catch (
                        _: Exception
                    ) {
                    }
                    com.averycorp.prismtask.ui.components.dispatchSwipeAction(
                        action = action,
                        taskId = task.id,
                        onComplete = { viewModel.onCompleteTaskWithUndo(it) },
                        onDelete = { viewModel.onDeleteTaskWithUndo(it) },
                        onReschedule = { viewModel.onMoveToTomorrow(it) },
                        onArchive = { viewModel.onArchiveTask(it) },
                        onMoveToProject = {
                            // project picker is a larger lift — fall through to move-to-tomorrow for now
                            viewModel.onMoveToTomorrow(it)
                        },
                        onToggleFlag = { viewModel.onToggleFlag(it) }
                    )
                }
            )

            val swipeIconScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) 1.2f else 0.8f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                ),
                label = "swipe_icon_scale"
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val direction = dismissState.dismissDirection
                    val action = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> swipePrefs.right
                        SwipeToDismissBoxValue.EndToStart -> swipePrefs.left
                        else -> com.averycorp.prismtask.domain.model.SwipeAction.NONE
                    }
                    val style = com.averycorp.prismtask.ui.components
                        .swipeActionStyle(action)
                    val backgroundColor = style.backgroundColor
                    val icon = style.icon ?: Icons.Default.Check
                    val alignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        else -> Alignment.CenterEnd
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(backgroundColor)
                            .padding(horizontal = 20.dp),
                        contentAlignment = alignment
                    ) {
                        if (direction == SwipeToDismissBoxValue.EndToStart && style.label.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = style.label,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.scale(swipeIconScale)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.scale(swipeIconScale)
                            )
                        }
                    }
                }
            ) {
                TaskItem(
                    task = task,
                    project = project,
                    subtasks = subtasks,
                    tags = tags,
                    attachmentCount = attachmentCount,
                    onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                    onClick = { onTaskClick(task.id) },
                    onLongClick = { viewModel.onEnterMultiSelect(task.id) },
                    onAddSubtaskClick = {
                        onExpandChange(expandedTaskIds + task.id)
                        onFocusChange(task.id)
                    },
                    onDuplicate = {
                        onDuplicate(
                            DuplicateDialogState(
                                taskId = task.id,
                                dueDate = task.dueDate,
                                subtaskCount = subtasks.size
                            )
                        )
                    }
                )
            }
        }
    }
    if (subtasks.isNotEmpty() || expandedTaskIds.contains(task.id)) {
        item(key = "subtasks_${task.id}") {
            SubtaskSection(
                parentTaskId = task.id,
                subtasks = subtasks,
                onToggleComplete = viewModel::onToggleSubtaskComplete,
                onAddSubtask = { title, parentId, priority ->
                    viewModel.onAddSubtask(title, parentId, priority)
                },
                onDeleteSubtask = viewModel::onDeleteSubtaskWithUndo,
                onReorderSubtasks = viewModel::onReorderSubtasks,
                expanded = expandedTaskIds.contains(task.id),
                onToggleExpand = {
                    onExpandChange(
                        if (expandedTaskIds.contains(task.id)) {
                            expandedTaskIds - task.id
                        } else {
                            expandedTaskIds + task.id
                        }
                    )
                },
                requestFocus = focusSubtaskForId == task.id,
                onFocusHandled = { onFocusChange(null) }
            )
        }
    }
}

@Composable
internal fun GroupHeader(group: String, count: Int) {
    val displayGroup = if (group == "Overdue") "From Earlier" else group
    val color = when (group) {
        "Today" -> TodayOrange
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayGroup,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.7f)
        )
    }
}

/**
 * Header row for a project section in the By Project view. Shows the
 * project's colored dot + name + task count, and acts as a drop target
 * for the cross-project drag-to-move interaction: any task card dropped
 * onto the header is reassigned to this project (or "No Project" when
 * [project] is null).
 *
 * While a drag is hovering over the header the row scales up slightly
 * and draws an accent border so the drop target is unambiguous. The
 * drag payload is a plain-text ClipData carrying the task id as a long.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectGroupHeader(
    project: ProjectEntity?,
    taskCount: Int,
    onDropTask: (Long) -> Unit
) {
    val accent = if (project != null) {
        try {
            Color(android.graphics.Color.parseColor(project.color))
        } catch (_: Exception) {
            MaterialTheme.colorScheme.primary
        }
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1f,
        label = "projectHeaderScale"
    )

    val target = remember(project?.id) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isHovered = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isHovered = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isHovered = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipItem = event
                    .toAndroidDragEvent()
                    .clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                val droppedId = clipItem?.text?.toString()?.toLongOrNull()
                isHovered = false
                return if (droppedId != null) {
                    onDropTask(droppedId)
                    true
                } else {
                    false
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isHovered) {
                    Modifier.border(
                        width = 2.dp,
                        color = accent,
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            ).background(
                if (isHovered) accent.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ).dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = target
            ).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = project?.name ?: "No Project",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$taskCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Variant of [taskItemWithSubtasks] used inside the By Project view. The
 * card is a drag-and-drop source: long-press starts a native drag whose
 * ClipData carries this task's id, and the card itself acts as a drop
 * target so dropping another task on top of it moves that task into this
 * card's project. Long-press still opens the context menu via the normal
 * combinedClickable path — only the actual drag gesture (long-press +
 * move) triggers [dragAndDropSource].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
internal fun androidx.compose.foundation.lazy.LazyListScope.draggableTaskItemWithSubtasks(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    subtasksMap: Map<Long, List<TaskEntity>>,
    taskTagsMap: Map<Long, List<TagEntity>>,
    attachmentCountMap: Map<Long, Int>,
    expandedTaskIds: Set<Long>,
    focusSubtaskForId: Long?,
    onTaskClick: (Long) -> Unit,
    onReschedule: (TaskEntity) -> Unit,
    onMoveToProject: (TaskEntity) -> Unit,
    onDropTask: (Long) -> Unit,
    viewModel: TaskListViewModel,
    isMultiSelectMode: Boolean,
    selectedTaskIds: Set<Long>,
    onExpandChange: (Set<Long>) -> Unit,
    onFocusChange: (Long?) -> Unit,
    onDuplicate: (DuplicateDialogState) -> Unit
) {
    val subtasks = subtasksMap[task.id].orEmpty()
    val tags = taskTagsMap[task.id].orEmpty()
    val attachmentCount = attachmentCountMap[task.id] ?: 0
    val project = projects.find { it.id == task.projectId }
    item(key = "proj_task_${task.id}") {
        var isHovered by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isHovered) 1.02f else 1f,
            label = "taskDragScale"
        )

        val dropTarget = remember(task.id, task.projectId) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    isHovered = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    isHovered = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isHovered = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val clipItem = event
                        .toAndroidDragEvent()
                        .clipData
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                    val droppedId = clipItem?.text?.toString()?.toLongOrNull()
                    isHovered = false
                    if (droppedId == null || droppedId == task.id) return false
                    onDropTask(droppedId)
                    return true
                }
            }
        }

        // Drag is initiated via the explicit drag handle so that the task
        // card's long-press gesture remains free to open the shared
        // context menu. The whole card stays a drop target so users can
        // release a dragged card on top of any task in the destination
        // project — not just on the project header. The drag shadow is
        // a small semi-transparent rounded rect so the user sees that a
        // drag is in progress — native Android handles the actual
        // follow-the-pointer movement on top of that decoration.
        val dragShadowColor = MaterialTheme.colorScheme.primary
        val dragHandleDragModifier = Modifier.dragAndDropSource(
            drawDragDecoration = {
                drawRoundRect(
                    color = dragShadowColor,
                    alpha = 0.5f,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
        ) {
            detectTapGestures(
                onLongPress = {
                    startTransfer(
                        DragAndDropTransferData(
                            clipData = ClipData.newPlainText(
                                "task_id",
                                task.id.toString()
                            )
                        )
                    )
                }
            )
        }

        val dragModifier = Modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = dropTarget
            ).shadow(if (isHovered) 6.dp else 0.dp, RoundedCornerShape(12.dp))
            .scale(scale)

        if (isMultiSelectMode) {
            TaskItem(
                task = task,
                project = project,
                subtasks = subtasks,
                tags = tags,
                attachmentCount = attachmentCount,
                isSelected = task.id in selectedTaskIds,
                isMultiSelectMode = true,
                onToggleComplete = { viewModel.onToggleTaskSelection(task.id) },
                onClick = { viewModel.onToggleTaskSelection(task.id) },
                onLongClick = { viewModel.onToggleTaskSelection(task.id) },
                onAddSubtaskClick = {},
                showDragHandle = true,
                dragHandleModifier = dragHandleDragModifier,
                modifier = dragModifier
            )
        } else {
            TaskItem(
                task = task,
                project = project,
                subtasks = subtasks,
                tags = tags,
                attachmentCount = attachmentCount,
                onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                onClick = { onTaskClick(task.id) },
                onLongClick = { viewModel.onEnterMultiSelect(task.id) },
                onAddSubtaskClick = {
                    onExpandChange(expandedTaskIds + task.id)
                    onFocusChange(task.id)
                },
                onDuplicate = {
                    onDuplicate(
                        DuplicateDialogState(
                            taskId = task.id,
                            dueDate = task.dueDate,
                            subtaskCount = subtasks.size
                        )
                    )
                },
                showDragHandle = true,
                dragHandleModifier = dragHandleDragModifier,
                modifier = dragModifier
            )
        }
    }
    if (subtasks.isNotEmpty() || expandedTaskIds.contains(task.id)) {
        item(key = "proj_subtasks_${task.id}") {
            SubtaskSection(
                parentTaskId = task.id,
                subtasks = subtasks,
                onToggleComplete = viewModel::onToggleSubtaskComplete,
                onAddSubtask = { title, parentId, priority ->
                    viewModel.onAddSubtask(title, parentId, priority)
                },
                onDeleteSubtask = viewModel::onDeleteSubtaskWithUndo,
                onReorderSubtasks = viewModel::onReorderSubtasks,
                expanded = expandedTaskIds.contains(task.id),
                onToggleExpand = {
                    onExpandChange(
                        if (expandedTaskIds.contains(task.id)) {
                            expandedTaskIds - task.id
                        } else {
                            expandedTaskIds + task.id
                        }
                    )
                },
                requestFocus = focusSubtaskForId == task.id,
                onFocusHandled = { onFocusChange(null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectFilterRow(
    projects: List<ProjectEntity>,
    selectedProjectId: Long?,
    onSelectProject: (Long?) -> Unit,
    onManageProjects: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selectedProjectId == null,
                onClick = { onSelectProject(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        items(projects, key = { it.id }) { project ->
            val projectColor = try {
                Color(android.graphics.Color.parseColor(project.color))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.primary
            }
            FilterChip(
                selected = selectedProjectId == project.id,
                onClick = { onSelectProject(project.id) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(projectColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(project.name)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = projectColor.copy(alpha = 0.15f),
                    selectedLabelColor = projectColor
                )
            )
        }
        item {
            AssistChip(
                onClick = onManageProjects,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FolderCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage")
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskItem(
    task: TaskEntity,
    project: ProjectEntity?,
    subtasks: List<TaskEntity>,
    tags: List<TagEntity> = emptyList(),
    attachmentCount: Int = 0,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onAddSubtaskClick: () -> Unit,
    onReschedule: (() -> Unit)? = null,
    onMoveToProject: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val hasOverflowActions =
        !isMultiSelectMode && (onReschedule != null || onMoveToProject != null || onDuplicate != null || onDelete != null)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag To Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = dragHandleModifier.size(24.dp)
                )
            }
            CircularCheckbox(
                checked = if (isMultiSelectMode) isSelected else task.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    task.dueDate?.let { millis ->
                        val label = formatDueDate(millis)
                        Text(
                            text = label.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = label.color
                        )
                    }

                    if (task.reminderOffset != null) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Reminder set",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (task.recurrenceRule != null) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Recurring task",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!task.notes.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Has notes",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (attachmentCount > 0) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Has attachments",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (subtasks.isNotEmpty()) {
                        val completed = subtasks.count { it.isCompleted }
                        Text(
                            text = "$completed/${subtasks.size} subtasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (project != null) {
                        ProjectChip(project)
                    }
                }

                if (tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val visibleTags = tags.take(3)
                        visibleTags.forEach { tag ->
                            TagChip(tag)
                        }
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onAddSubtaskClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.AddTask,
                    contentDescription = "Add subtask",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            if (hasOverflowActions) {
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More Actions",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        if (onReschedule != null) {
                            DropdownMenuItem(
                                text = { Text("\uD83D\uDCC5  Reschedule") },
                                onClick = {
                                    showOverflowMenu = false
                                    onReschedule()
                                }
                            )
                        }
                        if (onMoveToProject != null) {
                            DropdownMenuItem(
                                text = { Text("\uD83D\uDCC1  Move To Project") },
                                onClick = {
                                    showOverflowMenu = false
                                    onMoveToProject()
                                }
                            )
                        }
                        if (onDuplicate != null) {
                            DropdownMenuItem(
                                text = { Text("\uD83D\uDCCB  Duplicate") },
                                onClick = {
                                    showOverflowMenu = false
                                    onDuplicate()
                                }
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("\uD83D\uDDD1\uFE0F  Delete") },
                                onClick = {
                                    showOverflowMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            PriorityDot(task.priority)
            if (task.eisenhowerQuadrant != null) {
                Spacer(modifier = Modifier.width(3.dp))
                EisenhowerBadge(task.eisenhowerQuadrant)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

internal fun isTaskOverdue(task: TaskEntity): Boolean {
    if (task.isCompleted || task.dueDate == null) return false
    val startOfToday = Calendar
        .getInstance()
        .apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    return task.dueDate < startOfToday
}

@Composable
internal fun PriorityDot(priority: Int) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(LocalPriorityColors.current.forLevel(priority))
    )
}

@Composable
internal fun EisenhowerBadge(quadrant: String) {
    val (color, label) = when (quadrant) {
        "Q1" -> Color(0xFFEF4444) to "1"
        "Q2" -> Color(0xFF3B82F6) to "2"
        "Q3" -> Color(0xFFF59E0B) to "3"
        "Q4" -> Color(0xFF6B7280) to "4"
        else -> return
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 8.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            lineHeight = 8.sp
        )
    }
}

@Composable
internal fun TagChip(tag: TagEntity) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(tag.color))
    } catch (_: Exception) {
        Color.Gray
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(chipColor)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun ProjectChip(project: ProjectEntity) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(project.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal data class DueDateLabel(
    val text: String,
    val color: Color
)

@Composable
internal fun formatDueDate(epochMillis: Long): DueDateLabel {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfTomorrow = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfDayAfter = cal.timeInMillis

    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    return when {
        epochMillis < startOfToday -> {
            val formatted = dateFmt.format(Date(epochMillis))
            DueDateLabel(formatted, normal)
        }
        epochMillis < startOfTomorrow -> DueDateLabel("Today", TodayOrange)
        epochMillis < startOfDayAfter -> DueDateLabel("Tomorrow", normal)
        else -> DueDateLabel(dateFmt.format(Date(epochMillis)), normal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveFilterPills(
    filter: TaskFilter,
    allTags: List<TagEntity>,
    projects: List<ProjectEntity>,
    onUpdateFilter: (TaskFilter) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (filter.selectedTagIds.isNotEmpty()) {
            val tagNames = allTags.filter { it.id in filter.selectedTagIds }.joinToString(", ") { it.name }
            val modeLabel = if (filter.tagFilterMode == com.averycorp.prismtask.domain.model.TagFilterMode.ALL) "ALL" else "ANY"
            RemovableFilterChip(
                label = "Tags ($modeLabel): $tagNames",
                onRemove = { onUpdateFilter(filter.copy(selectedTagIds = emptyList())) }
            )
        }

        if (filter.selectedPriorities.isNotEmpty()) {
            val labels = filter.selectedPriorities.sorted().joinToString(", ") { p ->
                when (p) {
                    0 -> "None"
                    1 -> "Low"
                    2 -> "Med"
                    3 -> "High"
                    4 -> "Urgent"
                    else -> "$p"
                }
            }
            RemovableFilterChip(
                label = "Priority: $labels",
                onRemove = { onUpdateFilter(filter.copy(selectedPriorities = emptyList())) }
            )
        }

        if (filter.selectedProjectIds.isNotEmpty()) {
            val projNames = projects.filter { it.id in filter.selectedProjectIds }.joinToString(", ") { it.name }
            RemovableFilterChip(
                label = "Project: $projNames",
                onRemove = { onUpdateFilter(filter.copy(selectedProjectIds = emptyList())) }
            )
        }

        if (filter.dateRange != null) {
            val rangeLabel = when {
                filter.dateRange.start == null && filter.dateRange.end == null -> "No Date"
                else -> "Date range"
            }
            RemovableFilterChip(
                label = rangeLabel,
                onRemove = { onUpdateFilter(filter.copy(dateRange = null)) }
            )
        }

        if (filter.showCompleted) {
            RemovableFilterChip(
                label = "Completed",
                onRemove = { onUpdateFilter(filter.copy(showCompleted = false)) }
            )
        }

        if (filter.showArchived) {
            RemovableFilterChip(
                label = "Archived",
                onRemove = { onUpdateFilter(filter.copy(showArchived = false)) }
            )
        }

        if (filter.searchQuery.isNotBlank()) {
            RemovableFilterChip(
                label = "\"${filter.searchQuery}\"",
                onRemove = { onUpdateFilter(filter.copy(searchQuery = "")) }
            )
        }
    }
}

@Composable
internal fun RemovableFilterChip(
    label: String,
    onRemove: () -> Unit
) {
    AssistChip(
        onClick = onRemove,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove filter",
                modifier = Modifier.size(14.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}
