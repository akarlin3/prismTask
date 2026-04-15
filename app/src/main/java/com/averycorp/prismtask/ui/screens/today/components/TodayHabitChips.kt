package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.data.repository.HabitWithStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Horizontal scrolling row of habit chips shown on the Today screen.
 * Tapping a chip toggles that habit's completion for today; a trailing
 * "See All" chip navigates to the full habit list.
 */
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

/**
 * Compact reminder card surfaced on the Today screen for habits the user
 * has booked for today — e.g. an upcoming appointment or commitment.
 * Tapping navigates to the habit detail.
 */
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
                try {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                } catch (
                    _: Exception
                ) {
                }
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
