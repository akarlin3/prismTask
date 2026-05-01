package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.ui.components.CircularCheckbox
import com.averycorp.prismtask.ui.components.QuickAddBar
import com.averycorp.prismtask.ui.theme.LocalPriorityColors
import com.averycorp.prismtask.ui.theme.LocalPrismShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PlanSortMode { PRIORITY, DUE_DATE, PROJECT }

private data class PlanGroup(
    val key: String,
    val title: String,
    val tasks: List<TaskEntity>,
    val expanded: Boolean,
    val onToggle: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PlanForTodaySheet(
    plannedTasks: List<TaskEntity>,
    overdueTasks: List<TaskEntity>,
    upcomingTasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    startOfToday: Long,
    topTemplates: List<TaskTemplateEntity>,
    onPlan: (Long) -> Unit,
    onPlanMany: (List<Long>) -> Unit,
    onPlanAllOverdue: () -> Unit,
    onUnplan: (Long) -> Unit,
    onUseTemplate: (Long) -> Unit,
    onOpenManageTemplates: () -> Unit,
    onDismiss: () -> Unit,
    onMultiCreate: (String) -> Unit = {},
    onBatchCommand: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlanSortMode.DUE_DATE) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var showTemplatePickerSheet by remember { mutableStateOf(false) }

    var overdueExpanded by remember { mutableStateOf(true) }
    var tomorrowExpanded by remember { mutableStateOf(true) }
    var thisWeekExpanded by remember { mutableStateOf(true) }
    var nextWeekExpanded by remember { mutableStateOf(true) }
    var laterExpanded by remember { mutableStateOf(false) }
    var noDateExpanded by remember { mutableStateOf(false) }

    val projectsById = remember(projects) { projects.associateBy { it.id } }

    val filter: (TaskEntity) -> Boolean = { task ->
        searchQuery.isBlank() || task.title.contains(searchQuery, ignoreCase = true)
    }

    val sortComparator: Comparator<TaskEntity> = when (sortMode) {
        PlanSortMode.PRIORITY -> compareByDescending<TaskEntity> { it.priority }
            .thenBy { it.dueDate ?: Long.MAX_VALUE }
        PlanSortMode.DUE_DATE -> compareBy<TaskEntity> { it.dueDate ?: Long.MAX_VALUE }
            .thenByDescending { it.priority }
        PlanSortMode.PROJECT -> compareBy<TaskEntity> {
            it.projectId?.let { id -> projectsById[id]?.name } ?: "zzz"
        }.thenByDescending { it.priority }
    }

    val filteredUpcoming = upcomingTasks.filter(filter).sortedWith(sortComparator)
    val filteredOverdue = overdueTasks.filter(filter).sortedWith(sortComparator)
    val filteredPlanned = plannedTasks.filter(filter).sortedWith(sortComparator)

    val dayMs = 86_400_000L
    val tomorrowStart = startOfToday + dayMs
    val dayAfterTomorrowStart = tomorrowStart + dayMs
    val thisWeekEnd = startOfToday + 7 * dayMs
    val nextWeekEnd = startOfToday + 14 * dayMs

    val tomorrowTasks = filteredUpcoming.filter {
        it.dueDate != null && it.dueDate in tomorrowStart until dayAfterTomorrowStart
    }
    val thisWeekTasks = filteredUpcoming.filter {
        it.dueDate != null && it.dueDate in dayAfterTomorrowStart until thisWeekEnd
    }
    val nextWeekTasks = filteredUpcoming.filter { it.dueDate != null && it.dueDate in thisWeekEnd until nextWeekEnd }
    val laterTasks = filteredUpcoming.filter { it.dueDate != null && it.dueDate >= nextWeekEnd }
    val noDateTasks = filteredUpcoming.filter { it.dueDate == null }

    val hasAnyUpcoming = filteredOverdue.isNotEmpty() ||
        tomorrowTasks.isNotEmpty() ||
        thisWeekTasks.isNotEmpty() ||
        nextWeekTasks.isNotEmpty() ||
        laterTasks.isNotEmpty() ||
        noDateTasks.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plan for Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (multiSelectMode) {
                    TextButton(onClick = {
                        multiSelectMode = false
                        selectedIds = emptySet()
                    }) {
                        Text("Cancel")
                    }
                }
            }

            if (topTemplates.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "\uD83D\uDCCB Templates",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(topTemplates, key = { it.id }) { template ->
                            TemplateQuickChip(
                                icon = template.icon,
                                label = template.name,
                                onClick = { onUseTemplate(template.id) }
                            )
                        }
                        item {
                            TextButton(
                                onClick = { showTemplatePickerSheet = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "More Templates...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            QuickAddBar(
                viewModel = hiltViewModel(key = "plan_sheet_quickadd"),
                plannedDateOverride = startOfToday,
                alwaysExpanded = true,
                placeholder = "Add task for today...",
                onMultiCreate = onMultiCreate,
                onBatchCommand = onBatchCommand
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search tasks\u2026") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlanSortMode.values().forEach { mode ->
                    val label = when (mode) {
                        PlanSortMode.PRIORITY -> "Priority"
                        PlanSortMode.DUE_DATE -> "Due Date"
                        PlanSortMode.PROJECT -> "Project"
                    }
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val showEmptyState = filteredPlanned.isEmpty() && !hasAnyUpcoming

            if (showEmptyState) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "No tasks match your search"
                        } else {
                            "No upcoming tasks to plan. Create one above!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredPlanned.isNotEmpty()) {
                        item(key = "hdr_planned") {
                            PlanGroupHeader(
                                title = "\uD83D\uDCCC Planned for Today",
                                count = filteredPlanned.size,
                                color = MaterialTheme.colorScheme.primary,
                                expanded = true,
                                onToggle = null
                            )
                        }
                        items(filteredPlanned, key = { "planned_${it.id}" }) { task ->
                            SheetTaskCard(
                                task = task,
                                project = task.projectId?.let { projectsById[it] },
                                isPlanned = true,
                                multiSelectMode = multiSelectMode,
                                isSelected = task.id in selectedIds,
                                onTap = {
                                    if (multiSelectMode) {
                                        selectedIds = if (task.id in selectedIds) {
                                            selectedIds - task.id
                                        } else {
                                            selectedIds + task.id
                                        }
                                    } else {
                                        onUnplan(task.id)
                                    }
                                },
                                onLongPress = {
                                    if (!multiSelectMode) {
                                        multiSelectMode = true
                                        selectedIds = setOf(task.id)
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (filteredOverdue.isNotEmpty()) {
                        item(key = "hdr_overdue") {
                            PlanGroupHeader(
                                title = "From Earlier",
                                count = filteredOverdue.size,
                                color = neutralGray(),
                                expanded = overdueExpanded,
                                onToggle = { overdueExpanded = !overdueExpanded },
                                trailing = {
                                    TextButton(
                                        onClick = { onPlanAllOverdue() },
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(
                                            "Plan All",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                        if (overdueExpanded) {
                            items(filteredOverdue, key = { "overdue_${it.id}" }) { task ->
                                SheetTaskCard(
                                    task = task,
                                    project = task.projectId?.let { projectsById[it] },
                                    isPlanned = false,
                                    isOverdue = true,
                                    multiSelectMode = multiSelectMode,
                                    isSelected = task.id in selectedIds,
                                    onTap = {
                                        if (multiSelectMode) {
                                            selectedIds = if (task.id in selectedIds) {
                                                selectedIds - task.id
                                            } else {
                                                selectedIds + task.id
                                            }
                                        } else {
                                            onPlan(task.id)
                                        }
                                    },
                                    onLongPress = {
                                        if (!multiSelectMode) {
                                            multiSelectMode = true
                                            selectedIds = setOf(task.id)
                                        }
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    val groups: List<PlanGroup> = listOf(
                        PlanGroup("tomorrow", "Tomorrow", tomorrowTasks, tomorrowExpanded) { tomorrowExpanded = !tomorrowExpanded },
                        PlanGroup("this_week", "This Week", thisWeekTasks, thisWeekExpanded) { thisWeekExpanded = !thisWeekExpanded },
                        PlanGroup("next_week", "Next Week", nextWeekTasks, nextWeekExpanded) { nextWeekExpanded = !nextWeekExpanded },
                        PlanGroup("later", "Later", laterTasks, laterExpanded) { laterExpanded = !laterExpanded },
                        PlanGroup("no_date", "No Date", noDateTasks, noDateExpanded) { noDateExpanded = !noDateExpanded }
                    )

                    groups.forEach { group ->
                        if (group.tasks.isEmpty()) return@forEach
                        item(key = "hdr_${group.key}") {
                            PlanGroupHeader(
                                title = group.title,
                                count = group.tasks.size,
                                color = MaterialTheme.colorScheme.primary,
                                expanded = group.expanded,
                                onToggle = group.onToggle
                            )
                        }
                        if (group.expanded) {
                            items(group.tasks, key = { "${group.key}_${it.id}" }) { task ->
                                SheetTaskCard(
                                    task = task,
                                    project = task.projectId?.let { projectsById[it] },
                                    isPlanned = false,
                                    multiSelectMode = multiSelectMode,
                                    isSelected = task.id in selectedIds,
                                    onTap = {
                                        if (multiSelectMode) {
                                            selectedIds = if (task.id in selectedIds) {
                                                selectedIds - task.id
                                            } else {
                                                selectedIds + task.id
                                            }
                                        } else {
                                            onPlan(task.id)
                                        }
                                    },
                                    onLongPress = {
                                        if (!multiSelectMode) {
                                            multiSelectMode = true
                                            selectedIds = setOf(task.id)
                                        }
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    item(key = "bottom_pad") {
                        Spacer(modifier = Modifier.height(if (multiSelectMode) 80.dp else 16.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = multiSelectMode && selectedIds.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onPlanMany(selectedIds.toList())
                            selectedIds = emptySet()
                            multiSelectMode = false
                        }
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Plan Selected (${selectedIds.size})")
                    }
                }
            }
        }
    }

    if (showTemplatePickerSheet) {
        com.averycorp.prismtask.ui.screens.templates.TemplatePickerSheet(
            onDismiss = { showTemplatePickerSheet = false },
            onUseTemplate = { template ->
                onUseTemplate(template.id)
                showTemplatePickerSheet = false
            },
            onManageTemplates = {
                showTemplatePickerSheet = false
                onOpenManageTemplates()
            }
        )
    }
}

@Composable
private fun TemplateQuickChip(
    icon: String?,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = LocalPrismShapes.current.chip,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon ?: "\uD83D\uDCCB",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlanGroupHeader(
    title: String,
    count: Int,
    color: Color,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable { onToggle() } else Modifier)
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else if (onToggle != null) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetTaskCard(
    task: TaskEntity,
    project: ProjectEntity?,
    isPlanned: Boolean,
    isOverdue: Boolean = false,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isPlanned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelectMode) {
                CircularCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() },
                    size = 20.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (task.priority > 0) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LocalPriorityColors.current.forLevel(task.priority))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.dueDate != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dateFormat.format(Date(task.dueDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (project != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(LocalPrismShapes.current.chip)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isPlanned) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(LocalPrismShapes.current.chip)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "\uD83D\uDCCC Planned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
