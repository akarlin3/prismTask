package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.HabitWithStatus
import com.averycorp.prismtask.ui.components.CircularCheckbox
import com.averycorp.prismtask.ui.components.QuickAddBar
import com.averycorp.prismtask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val NeutralGray = Color(0xFF9E9E9E)
internal val CompletedGreen = Color(0xFF4CAF50)

/**
 * Sticky compact header bar shown in the Scaffold topBar slot.
 */
@Composable
internal fun CompactProgressHeader(
    completed: Int,
    total: Int,
    progress: Float,
    progressStyle: String = "ring"
) {
    val dateLabel = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "headerProgress"
    )

    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(progress >= 1f && total > 0) {
        if (progress >= 1f && total > 0) {
            celebrate = true
            delay(900)
            celebrate = false
        }
    }
    val barColor by animateColorAsState(
        targetValue = when {
            progress >= 1f -> CompletedGreen
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(400),
        label = "headerBarColor"
    )
    val barScale by animateFloatAsState(
        targetValue = if (celebrate) 1.6f else 1f,
        animationSpec = tween(350),
        label = "headerBarScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(120.dp)) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when (progressStyle) {
                "ring" -> {
                    Box(
                        modifier = Modifier.size((36f * barScale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeWidth = 4.dp
                        )
                        CircularProgressIndicator(
                            progress = { animatedProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            color = barColor,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "$completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                "percentage" -> {
                    Text(
                        text = "$completed done",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                else -> {
                    LinearProgressIndicator(
                        progress = { animatedProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height((4f * barScale).dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "$completed done",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun AllCaughtUpCard(
    taskCount: Int,
    habitCount: Int,
    habitTotal: Int,
    onPlanTomorrow: () -> Unit
) {
    val subtitle = "Everything's done. Seriously, go do something fun."
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CompletedGreen.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uD83C\uDF89",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "All Caught Up!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = onPlanTomorrow) {
                Text("Plan Tomorrow")
            }
        }
    }
}

@Composable
internal fun CollapsibleSection(
    emoji: String,
    title: String,
    count: Int,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    countLabel: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(280))
    ) {
        SectionHeaderRow(
            emoji = emoji,
            title = title,
            count = count,
            countLabel = countLabel,
            accentColor = accentColor,
            expanded = expanded,
            onToggle = onToggle
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SectionHeaderRow(
    emoji: String,
    title: String,
    count: Int,
    countLabel: String?,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(220),
        label = "chevronRotation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = countLabel ?: "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun HabitChipRow(
    habits: List<HabitWithStatus>,
    onToggle: (HabitWithStatus) -> Unit,
    onSeeAll: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(habits, key = { "habit_chip_${it.habit.id}" }) { hws ->
            HabitChip(
                habitWithStatus = hws,
                onTap = { onToggle(hws) }
            )
        }
        item(key = "habit_see_all") {
            SeeAllChip(onClick = onSeeAll)
        }
    }
}

@Composable
internal fun BookableHabitReminderCard(
    habitWithStatus: HabitWithStatus,
    onClick: () -> Unit
) {
    val habit = habitWithStatus.habit
    val habitColor = remember(habit.color) {
        try {
            Color(android.graphics.Color.parseColor(habit.color))
        } catch (_: Exception) {
            Color(0xFF4A90D9)
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val noteStr = habit.bookedNote?.let { " \u2014 $it" } ?: ""
    val dateStr = habit.bookedDate?.let { dateFormat.format(Date(it)) } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = habitColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = habit.icon, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "\uD83D\uDCC5 $dateStr$noteStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}

@Composable
private fun HabitChip(
    habitWithStatus: HabitWithStatus,
    onTap: () -> Unit
) {
    val habit = habitWithStatus.habit
    val habitColor = remember(habit.color) {
        try {
            Color(android.graphics.Color.parseColor(habit.color))
        } catch (_: Exception) {
            Color(0xFF4A90D9)
        }
    }
    val isComplete = habitWithStatus.isCompletedToday
    val target = habitWithStatus.dailyTarget.coerceAtLeast(1)
    val done = habitWithStatus.completionsToday.coerceAtMost(target)
    val ringProgress = if (isComplete) 1f else done.toFloat() / target.toFloat()

    val containerColor = if (isComplete) {
        habitColor.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var tapped by remember { mutableStateOf(false) }
    val chipScale by animateFloatAsState(
        targetValue = if (tapped) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "habit_scale",
        finishedListener = { tapped = false }
    )
    Card(
        modifier = Modifier
            .width(118.dp)
            .scale(chipScale)
            .clickable {
                tapped = true
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onTap()
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { ringProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(28.dp),
                    color = if (isComplete) CompletedGreen else habitColor,
                    trackColor = habitColor.copy(alpha = 0.18f),
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = habit.icon,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = habit.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (target > 1 && !isComplete) {
                Text(
                    text = "$done/$target",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isComplete) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelSmall,
                    color = CompletedGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SeeAllChip(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(96.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun FloatingQuickAddBar(
    autoStartVoice: Boolean = false,
    onVoiceAutoStartConsumed: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            QuickAddBar(
                autoStartVoice = autoStartVoice,
                onVoiceMessage = { }
            )
            LaunchedEffect(autoStartVoice) {
                if (autoStartVoice) onVoiceAutoStartConsumed()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SwipeableTaskItem(
    task: TaskEntity,
    tags: List<TagEntity>,
    isOverdue: Boolean = false,
    isPlanned: Boolean = false,
    onComplete: () -> Unit,
    onClick: () -> Unit,
    onReschedule: () -> Unit = {},
    onMoveToProject: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMoveToTomorrow: () -> Unit = {}
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tomorrowBlue = Color(0xFF5C8CC7)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onComplete()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onMoveToTomorrow()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    val isSwiping = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
    val iconScale by animateFloatAsState(
        targetValue = if (isSwiping) 1.2f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe_icon_scale"
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val backgroundColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> CompletedGreen
                SwipeToDismissBoxValue.EndToStart -> tomorrowBlue
                else -> Color.Transparent
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                SwipeToDismissBoxValue.EndToStart -> Icons.AutoMirrored.Filled.ArrowForward
                else -> Icons.Default.Check
            }
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
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tomorrow",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.scale(iconScale)
                        )
                    }
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.scale(iconScale)
                    )
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularCheckbox(
                    checked = false,
                    onCheckedChange = { onComplete() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.priority > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LocalPriorityColors.current.forLevel(task.priority))
                            )
                        }
                        if (isOverdue && task.dueDate != null) {
                            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                            Text(
                                text = fmt.format(Date(task.dueDate)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isPlanned && task.dueDate != null) {
                            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                            Text(
                                text = "Due: ${fmt.format(Date(task.dueDate))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isPlanned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Planned",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        tags.take(3).forEach { tag ->
                            val tagColor = try {
                                Color(android.graphics.Color.parseColor(tag.color))
                            } catch (_: Exception) { MaterialTheme.colorScheme.outline }
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tagColor
                            )
                        }
                    }
                }
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
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDCC5  Reschedule") },
                            onClick = {
                                showOverflowMenu = false
                                onReschedule()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDCC1  Move To Project") },
                            onClick = {
                                showOverflowMenu = false
                                onMoveToProject()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDCCB  Duplicate") },
                            onClick = {
                                showOverflowMenu = false
                                onDuplicate()
                            }
                        )
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
    }
}

@Composable
internal fun CompletedTaskItem(task: TaskEntity, onUncomplete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUncomplete() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularCheckbox(checked = true, onCheckedChange = { onUncomplete() })
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
