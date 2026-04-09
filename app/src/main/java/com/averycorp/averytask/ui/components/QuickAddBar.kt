package com.averycorp.averytask.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.averytask.domain.usecase.ParsedTask
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddBar(
    viewModel: QuickAddViewModel = hiltViewModel(),
    onTaskCreated: () -> Unit = {},
    modifier: Modifier = Modifier,
    plannedDateOverride: Long? = null,
    alwaysExpanded: Boolean = false,
    placeholder: String = "Add task... (try: Buy milk tomorrow #groceries !high)"
) {
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val parsedPreview by viewModel.parsedPreview.collectAsStateWithLifecycle()
    val isExpanded by viewModel.isExpanded.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    val expandedState = alwaysExpanded || isExpanded
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (!expandedState) {
            TextButton(
                onClick = { viewModel.onToggleExpand() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Quick Add")
            }
        } else {
            if (!alwaysExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.onToggleExpand() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.onSubmit(plannedDateOverride)
                            onTaskCreated()
                        },
                        enabled = inputText.isNotBlank() && !isSubmitting
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (inputText.isNotBlank()) {
                        viewModel.onSubmit(plannedDateOverride)
                        onTaskCreated()
                    }
                }),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            AnimatedVisibility(
                visible = parsedPreview != null && inputText.isNotBlank(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                parsedPreview?.let { parsed ->
                    ParsedPreview(parsed)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParsedPreview(parsed: ParsedTask) {
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        parsed.dueDate?.let { millis ->
            PreviewChip(
                label = dateFormat.format(Date(millis)),
                icon = "\uD83D\uDCC5",
                color = MaterialTheme.colorScheme.primary
            )
        }

        parsed.dueTime?.let { millis ->
            PreviewChip(
                label = timeFormat.format(Date(millis)),
                icon = "\uD83D\uDD50",
                color = MaterialTheme.colorScheme.primary
            )
        }

        parsed.tags.forEach { tag ->
            PreviewChip(
                label = "#$tag",
                icon = null,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        parsed.projectName?.let { name ->
            PreviewChip(
                label = name,
                icon = "\uD83D\uDCC1",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (parsed.priority > 0) {
            val priorityLabel = when (parsed.priority) {
                1 -> "Low"
                2 -> "Medium"
                3 -> "High"
                4 -> "Urgent"
                else -> ""
            }
            val priorityColor = LocalPriorityColors.current.forLevel(parsed.priority)
            PreviewChip(label = priorityLabel, icon = null, color = priorityColor)
        }

        parsed.recurrenceHint?.let { hint ->
            PreviewChip(
                label = hint.replaceFirstChar { it.uppercase() },
                icon = "\uD83D\uDD01",
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PreviewChip(
    label: String,
    icon: String?,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Text(icon, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
