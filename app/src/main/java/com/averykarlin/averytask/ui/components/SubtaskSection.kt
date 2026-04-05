package com.averykarlin.averytask.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.averykarlin.averytask.data.local.entity.TaskEntity

@Composable
fun SubtaskSection(
    parentTaskId: Long,
    subtasks: List<TaskEntity>,
    onToggleComplete: (subtaskId: Long, isCompleted: Boolean) -> Unit,
    onAddSubtask: (title: String, parentTaskId: Long) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    requestFocus: Boolean = false,
    onFocusHandled: () -> Unit = {}
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "arrow_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
    ) {
        if (subtasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                val completed = subtasks.count { it.isCompleted }
                Text(
                    text = "$completed/${subtasks.size} subtask${if (subtasks.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                subtasks.forEach { subtask ->
                    SubtaskRow(
                        subtask = subtask,
                        onToggleComplete = { onToggleComplete(subtask.id, subtask.isCompleted) }
                    )
                }
                AddSubtaskRow(
                    onAdd = { title -> onAddSubtask(title, parentTaskId) },
                    requestFocus = requestFocus,
                    onFocusHandled = onFocusHandled
                )
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    subtask: TaskEntity,
    onToggleComplete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = subtask.isCompleted,
            onCheckedChange = { onToggleComplete() },
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null,
            color = if (subtask.isCompleted)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        PriorityDot(subtask.priority)
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun PriorityDot(priority: Int) {
    val color = when (priority) {
        1 -> Color(0xFF4A90D9)
        2 -> Color(0xFFF5C542)
        3 -> Color(0xFFE8872A)
        4 -> Color(0xFFD93025)
        else -> Color(0xFFAAAAAA)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun AddSubtaskRow(
    onAdd: (String) -> Unit,
    requestFocus: Boolean = false,
    onFocusHandled: () -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusHandled()
        }
    }

    val submit = {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onAdd(trimmed)
            text = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = {
                Text(
                    "Add subtask...",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        IconButton(
            onClick = submit,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add subtask",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
