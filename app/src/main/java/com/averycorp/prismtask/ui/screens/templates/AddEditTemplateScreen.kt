package com.averycorp.prismtask.ui.screens.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.averycorp.prismtask.domain.model.RecurrenceType
import com.averycorp.prismtask.ui.components.RecurrenceDialog
import com.averycorp.prismtask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.launch

/**
 * Emoji set used for the template icon picker. Curated to cover the common
 * recurring-task categories people reach for first (personal, work, home,
 * errands, study, fitness) rather than an exhaustive emoji grid.
 */
private val TEMPLATE_ICONS = listOf(
    "\uD83D\uDCCB", // 📋
    "\uD83D\uDCDD", // 📝
    "\uD83D\uDCCC", // 📌
    "\uD83C\uDFE0", // 🏠
    "\uD83D\uDCBC", // 💼
    "\uD83D\uDED2", // 🛒
    "\uD83D\uDCDA", // 📚
    "\uD83C\uDFC3", // 🏃
    "\uD83E\uDDF9", // 🧹
    "\uD83D\uDC68\u200D\uD83D\uDCBB", // 👨‍💻
    "\uD83D\uDCE7", // 📧
    "\uD83D\uDCDE", // 📞
    "\u2708\uFE0F", // ✈️
    "\uD83C\uDFAF", // 🎯
    "\uD83D\uDCA1", // 💡
    "\u2B50"         // ⭐
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTemplateScreen(
    navController: NavController,
    viewModel: AddEditTemplateViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dismiss: () -> Unit = {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                navController.popBackStack()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { navController.popBackStack() },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.92f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sticky header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = dismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                    Text(
                        text = if (viewModel.isEdit) "Edit Template" else "New Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    if (viewModel.isEdit) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (viewModel.saveTemplate()) {
                                    dismiss()
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "Save",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TemplateInfoSection(viewModel)
                    HorizontalDivider()
                    TaskBlueprintSection(viewModel)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    BackHandler { dismiss() }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Template") },
            text = { Text("Delete \"${viewModel.name.ifBlank { "this template" }}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            viewModel.deleteTemplate()
                            dismiss()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Template info section (name, icon, category)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TemplateInfoSection(viewModel: AddEditTemplateViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Template Info")

        OutlinedTextField(
            value = viewModel.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Template Name") },
            isError = viewModel.nameError,
            supportingText = if (viewModel.nameError) {
                { Text("Name is required") }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("Icon")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TEMPLATE_ICONS.forEach { emoji ->
                IconOption(
                    emoji = emoji,
                    selected = viewModel.icon == emoji,
                    onClick = { viewModel.onIconChange(emoji) }
                )
            }
        }

        SectionLabel("Category")
        OutlinedTextField(
            value = viewModel.category,
            onValueChange = viewModel::onCategoryChange,
            label = { Text("Category (Optional)") },
            placeholder = { Text("E.g. Work, Personal, Errands") },
            singleLine = true,
            trailingIcon = if (viewModel.category.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.onCategoryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        if (viewModel.existingCategories.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                viewModel.existingCategories.forEach { cat ->
                    FilterChip(
                        selected = viewModel.category == cat,
                        onClick = {
                            viewModel.onCategoryChange(
                                if (viewModel.category == cat) "" else cat
                            )
                        },
                        label = { Text(cat) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Task blueprint section
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TaskBlueprintSection(viewModel: AddEditTemplateViewModel) {
    val projects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val tags by viewModel.availableTags.collectAsStateWithLifecycle()

    var showProjectPicker by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("Task Blueprint")
        Text(
            text = "These Fields Are Pre-Filled When You Use This Template.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = viewModel.templateTitle,
            onValueChange = viewModel::onTemplateTitleChange,
            label = { Text("Task Title (Pre-Filled)") },
            placeholder = { Text("Leave Empty to Use Template Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = viewModel.templateDescription,
            onValueChange = viewModel::onTemplateDescriptionChange,
            label = { Text("Description") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        // --- Priority ---
        SectionLabel("Priority")
        PriorityDotRow(
            selected = viewModel.templatePriority,
            onSelect = viewModel::onTemplatePriorityChange
        )

        // --- Project ---
        SectionLabel("Project")
        ProjectSelectorCard(
            selectedProject = projects.find { it.id == viewModel.templateProjectId },
            onClick = { showProjectPicker = true }
        )

        // --- Tags ---
        if (tags.isNotEmpty()) {
            SectionLabel("Tags")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    TagToggleChip(
                        tag = tag,
                        selected = tag.id in viewModel.templateTagIds,
                        onClick = { viewModel.onToggleTag(tag.id) }
                    )
                }
            }
        }

        // --- Duration ---
        SectionLabel("Estimated Duration")
        DurationChipRow(
            duration = viewModel.templateDuration,
            onSelect = viewModel::onTemplateDurationChange
        )

        // --- Recurrence ---
        SectionLabel("Recurrence")
        RecurrenceRow(
            rule = viewModel.templateRecurrence,
            onEdit = { showRecurrenceDialog = true },
            onClear = { viewModel.onTemplateRecurrenceChange(null) }
        )

        // --- Subtasks ---
        SectionLabel("Subtasks")
        SubtasksEditor(
            subtasks = viewModel.templateSubtasks,
            onAdd = viewModel::onAddSubtask,
            onRemove = viewModel::onRemoveSubtask
        )
    }

    if (showProjectPicker) {
        ProjectPickerSheet(
            projects = projects,
            selectedProjectId = viewModel.templateProjectId,
            onSelect = { id ->
                viewModel.onTemplateProjectIdChange(id)
                showProjectPicker = false
            },
            onDismiss = { showProjectPicker = false }
        )
    }

    if (showRecurrenceDialog) {
        RecurrenceDialog(
            initialRule = viewModel.templateRecurrence,
            onDismiss = { showRecurrenceDialog = false },
            onConfirm = { rule ->
                viewModel.onTemplateRecurrenceChange(rule)
                showRecurrenceDialog = false
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun IconOption(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected)
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PriorityDotRow(selected: Int, onSelect: (Int) -> Unit) {
    val priorityColors = LocalPriorityColors.current
    val levels = listOf(
        0 to priorityColors.none,
        1 to priorityColors.low,
        2 to priorityColors.medium,
        3 to priorityColors.high,
        4 to priorityColors.urgent
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        levels.forEach { (level, color) ->
            PriorityDot(
                color = color,
                selected = selected == level,
                onClick = { onSelect(level) }
            )
        }
    }
}

@Composable
private fun PriorityDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProjectSelectorCard(
    selectedProject: ProjectEntity?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedProject != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseColorOr(selectedProject.color, Color.Gray))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedProject.icon,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = selectedProject.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "No Project",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Change project",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPickerSheet(
    projects: List<ProjectEntity>,
    selectedProjectId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Select Project",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    ProjectPickerRow(
                        leading = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = "None",
                        selected = selectedProjectId == null,
                        onClick = { onSelect(null) }
                    )
                }
                items(projects, key = { it.id }) { project ->
                    ProjectPickerRow(
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseColorOr(project.color, Color.Gray))
                            )
                        },
                        label = "${project.icon} ${project.name}",
                        selected = selectedProjectId == project.id,
                        onClick = { onSelect(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectPickerRow(
    leading: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            leading()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TagToggleChip(
    tag: TagEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tagColor = parseColorOr(tag.color, Color.Gray)
    val bg = if (selected) tagColor else Color.Transparent
    val textColor = if (selected) Color.White else tagColor
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = 1.5.dp,
                color = tagColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DurationChipRow(
    duration: Int?,
    onSelect: (Int?) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val presets = listOf(
        "15m" to 15,
        "30m" to 30,
        "1h" to 60,
        "1.5h" to 90,
        "2h" to 120
    )
    val matchesPreset = duration != null && presets.any { it.second == duration }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (label, minutes) ->
            FilterChip(
                selected = duration == minutes,
                onClick = {
                    onSelect(if (duration == minutes) null else minutes)
                },
                label = { Text(label) }
            )
        }
        FilterChip(
            selected = duration != null && !matchesPreset,
            onClick = { showCustomDialog = true },
            label = {
                Text(
                    if (duration != null && !matchesPreset) "${duration}m" else "Custom"
                )
            }
        )
        if (duration != null) {
            FilterChip(
                selected = false,
                onClick = { onSelect(null) },
                label = { Text("Clear") }
            )
        }
    }

    if (showCustomDialog) {
        var text by remember { mutableStateOf(duration?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom Duration") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new ->
                        text = new.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelect(text.toIntOrNull()?.takeIf { it > 0 })
                    showCustomDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecurrenceRow(
    rule: RecurrenceRule?,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    if (rule == null) {
        TextButton(onClick = onEdit) {
            Text("Set Recurrence...")
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatRecurrenceSummary(rule),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onClear) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SubtasksEditor(
    subtasks: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit
) {
    var newText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val submit = {
        if (newText.isNotBlank()) {
            onAdd(newText)
            newText = ""
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        subtasks.forEachIndexed { index, title ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Subtask",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedTextField(
            value = newText,
            onValueChange = { newText = it },
            placeholder = { Text("Add Subtask...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = submit) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Subtask",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun parseColorOr(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}

private fun formatRecurrenceSummary(rule: RecurrenceRule): String {
    val interval = rule.interval.coerceAtLeast(1)
    return when (rule.type) {
        RecurrenceType.DAILY ->
            if (interval == 1) "Every Day" else "Every $interval Days"
        RecurrenceType.WEEKLY -> {
            val prefix = if (interval == 1) "Every Week" else "Every $interval Weeks"
            val days = rule.daysOfWeek?.takeIf { it.isNotEmpty() }?.let { list ->
                val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                list.sorted().joinToString(", ") { names.getOrElse(it - 1) { "" } }
            }
            if (days != null) "$prefix on $days" else prefix
        }
        RecurrenceType.MONTHLY -> {
            val prefix = if (interval == 1) "Every Month" else "Every $interval Months"
            rule.dayOfMonth?.let { "$prefix on Day $it" } ?: prefix
        }
        RecurrenceType.YEARLY ->
            if (interval == 1) "Every Year" else "Every $interval Years"
        RecurrenceType.CUSTOM -> "Custom"
    }
}
