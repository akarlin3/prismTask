package com.averycorp.prismtask.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.ui.theme.LocalPriorityColors
import com.averycorp.prismtask.ui.theme.LocalPrismShapes

/**
 * Bottom action bar shown while multi-select mode is active in TaskListScreen.
 * Exposes the bulk-edit actions as a row of icon buttons with a "✕ N Selected"
 * affordance on the left. Each action is delegated via a callback — the bar
 * itself is stateless so it can be hoisted into any screen that adopts
 * multi-select.
 *
 * Layout follows the Material 3 bottom bar pattern: surfaceContainer color with
 * tonal elevation, respecting the navigation-bar insets so the icons aren't
 * hidden behind the system nav bar on gesture-nav devices.
 */
@Composable
fun BatchEditBar(
    selectedCount: Int,
    onDeselectAll: () -> Unit,
    onComplete: () -> Unit,
    onReschedule: () -> Unit,
    onEditTags: () -> Unit,
    onSetPriority: (Int) -> Unit,
    onMoveToProject: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Anchored priority menu state lives here so the DropdownMenu can
    // attach directly to the flag icon button instead of floating at the
    // top-left of the bottom bar.
    var showPriorityMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: "✕ N Selected" — tap clears selection entirely.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onDeselectAll)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Deselect All",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$selectedCount Selected",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Right: icon buttons — Complete, Reschedule, Tags, Priority, Move, Delete.
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatchActionButton(
                    icon = Icons.Default.Check,
                    label = "Done",
                    onClick = onComplete
                )
                BatchActionButton(
                    icon = Icons.Default.CalendarToday,
                    label = "Date",
                    onClick = onReschedule
                )
                BatchActionButton(
                    icon = Icons.AutoMirrored.Filled.Label,
                    label = "Tags",
                    onClick = onEditTags
                )
                Box {
                    BatchActionButton(
                        icon = Icons.Default.Flag,
                        label = "Priority",
                        onClick = { showPriorityMenu = true }
                    )
                    BatchPriorityMenu(
                        expanded = showPriorityMenu,
                        onDismiss = { showPriorityMenu = false },
                        onPick = onSetPriority
                    )
                }
                BatchActionButton(
                    icon = Icons.Default.Folder,
                    label = "Move",
                    onClick = onMoveToProject
                )
                BatchActionButton(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BatchActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Small dropdown anchored to the priority button that lists all five
 * priority levels with colored dots. Selecting a row dismisses the menu
 * and emits the chosen level via [onPick].
 */
@Composable
fun BatchPriorityMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val priorityColors = LocalPriorityColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        listOf(
            0 to "None",
            1 to "Low",
            2 to "Med",
            3 to "High",
            4 to "Urgent"
        ).forEach { (level, label) ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(priorityColors.forLevel(level))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(label)
                    }
                },
                onClick = {
                    onPick(level)
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Dialog listing every known tag as a toggle chip. Chips reflect three
 * possible states across the selected-task set:
 *
 * - ALL: every selected task has this tag → filled/active
 * - SOME: some but not all tasks have this tag → outlined with a "—" indicator
 * - NONE: no selected task has this tag → outlined/inactive
 *
 * Tapping a chip cycles via the rules documented on the [TagTriState] enum
 * and accumulates the add/remove sets so the final "Done" button can apply
 * everything as one atomic batch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchTagsDialog(
    allTags: List<TagEntity>,
    initialStates: Map<Long, TagTriState>,
    onDismiss: () -> Unit,
    onConfirm: (addIds: Set<Long>, removeIds: Set<Long>) -> Unit
) {
    // Track the user-visible state per tag id. Any tag not in the map is
    // treated as inactive (NONE).
    var tagStates by remember { mutableStateOf(initialStates) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tags") },
        text = {
            if (allTags.isEmpty()) {
                Text(
                    text = "No tags yet. Create tags from the Tag Management screen first.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allTags.forEach { tag ->
                        val current = tagStates[tag.id] ?: TagTriState.NONE
                        BatchTagChip(
                            tag = tag,
                            state = current,
                            onClick = {
                                // Tri-state → binary transition. Both SOME
                                // and NONE snap to ACTIVE on tap (normalize
                                // the mixed state); ACTIVE toggles back to
                                // NONE to remove the tag from everything.
                                val next = when (current) {
                                    TagTriState.ACTIVE -> TagTriState.NONE
                                    TagTriState.SOME, TagTriState.NONE -> TagTriState.ACTIVE
                                }
                                tagStates = tagStates + (tag.id to next)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Compute the diff between initial and current states:
                    // tags that became ACTIVE → add, tags that became NONE
                    // → remove. Tags that didn't change are ignored.
                    val addIds = mutableSetOf<Long>()
                    val removeIds = mutableSetOf<Long>()
                    for (tag in allTags) {
                        val before = initialStates[tag.id] ?: TagTriState.NONE
                        val after = tagStates[tag.id] ?: TagTriState.NONE
                        if (before == after) continue
                        when (after) {
                            TagTriState.ACTIVE -> addIds += tag.id
                            TagTriState.NONE -> removeIds += tag.id
                            TagTriState.SOME -> Unit // shouldn't happen
                        }
                    }
                    onConfirm(addIds, removeIds)
                }
            ) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

enum class TagTriState { ACTIVE, SOME, NONE }

/**
 * Helper that computes the initial tri-state map for a set of selected
 * tasks given each task's current tag assignments. A tag is ACTIVE iff
 * every selected task has it, NONE iff no selected task has it, and SOME
 * otherwise.
 */
fun computeInitialTagStates(
    selectedTaskIds: Collection<Long>,
    tagsByTask: Map<Long, List<TagEntity>>
): Map<Long, TagTriState> {
    if (selectedTaskIds.isEmpty()) return emptyMap()
    val perTaskTagIds: List<Set<Long>> = selectedTaskIds.map { id ->
        tagsByTask[id].orEmpty().map { it.id }.toSet()
    }
    val allTagIds: Set<Long> = perTaskTagIds.flatten().toSet()
    return allTagIds.associateWith { tagId ->
        val count = perTaskTagIds.count { tagId in it }
        when (count) {
            selectedTaskIds.size -> TagTriState.ACTIVE
            0 -> TagTriState.NONE
            else -> TagTriState.SOME
        }
    }
}

@Composable
private fun BatchTagChip(
    tag: TagEntity,
    state: TagTriState,
    onClick: () -> Unit
) {
    val tagColor = try {
        Color(android.graphics.Color.parseColor(tag.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val (bg, border, content) = when (state) {
        TagTriState.ACTIVE -> Triple(
            tagColor.copy(alpha = 0.25f),
            tagColor,
            tagColor
        )
        TagTriState.SOME -> Triple(
            tagColor.copy(alpha = 0.08f),
            tagColor.copy(alpha = 0.6f),
            tagColor.copy(alpha = 0.85f)
        )
        TagTriState.NONE -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = Modifier
            .clip(LocalPrismShapes.current.chip)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            TagTriState.ACTIVE -> Icon(
                Icons.Default.Check,
                contentDescription = "Active",
                modifier = Modifier.size(14.dp),
                tint = content
            )
            TagTriState.SOME -> Icon(
                Icons.Default.HorizontalRule,
                contentDescription = "Mixed",
                modifier = Modifier.size(14.dp),
                tint = content
            )
            TagTriState.NONE -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tagColor)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = if (state == TagTriState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Dialog used by the bulk "Move to Project" action. Lists every project as
 * a radio row (with colored dot), prepends a "None" option for removing
 * the project association, and appends a "Create New Project..." footer
 * that swaps the content area for an inline name field.
 */
@Composable
fun BatchMoveToProjectDialog(
    projects: List<ProjectEntity>,
    currentProjectId: Long?,
    onDismiss: () -> Unit,
    onMove: (projectId: Long?) -> Unit,
    onCreateAndMove: (name: String) -> Unit
) {
    var selectedId by remember { mutableStateOf<Long?>(currentProjectId) }
    var isCreating by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreating) "Create Project" else "Move to Project") },
        text = {
            if (isCreating) {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "None" option clears the project_id on every selected
                    // task — useful when you want to unparent tasks that were
                    // mistakenly added to a project.
                    item(key = "none") {
                        ProjectRadioRow(
                            label = "None",
                            color = null,
                            selected = selectedId == null,
                            onClick = { selectedId = null },
                            leadingIconFallback = Icons.Default.FolderOff
                        )
                    }
                    items(projects, key = { it.id }) { project ->
                        val projectColor = try {
                            Color(android.graphics.Color.parseColor(project.color))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        ProjectRadioRow(
                            label = project.name,
                            color = projectColor,
                            selected = selectedId == project.id,
                            onClick = { selectedId = project.id },
                            leadingIconFallback = null
                        )
                    }
                    // Footer: swap into creation mode.
                    item(key = "create") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    isCreating = true
                                    newProjectName = ""
                                }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Create New Project",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreating) {
                TextButton(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            onCreateAndMove(newProjectName.trim())
                        }
                    },
                    enabled = newProjectName.isNotBlank()
                ) { Text("Create & Move") }
            } else {
                TextButton(onClick = { onMove(selectedId) }) { Text("Move") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isCreating) {
                    isCreating = false
                } else {
                    onDismiss()
                }
            }) {
                Text(if (isCreating) "Back" else "Cancel")
            }
        }
    )
}

@Composable
private fun ProjectRadioRow(
    label: String,
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIconFallback: androidx.compose.ui.graphics.vector.ImageVector?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(4.dp))
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        } else if (leadingIconFallback != null) {
            Icon(
                leadingIconFallback,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
