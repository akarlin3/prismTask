package com.averykarlin.averytask.ui.screens.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averykarlin.averytask.data.repository.HabitWithStatus
import com.averykarlin.averytask.ui.components.EmptyState
import com.averykarlin.averytask.ui.components.StreakBadge
import com.averykarlin.averytask.ui.navigation.AveryTaskRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    navController: NavController,
    viewModel: HabitListViewModel = hiltViewModel()
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    var habitToDelete by remember { mutableStateOf<HabitWithStatus?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habits", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(AveryTaskRoute.AddEditHabit.createRoute()) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Habit",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        if (habits.isEmpty()) {
            EmptyState(
                icon = Icons.Default.FitnessCenter,
                title = "Build better habits!",
                subtitle = "Tap + to start tracking.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(habits, key = { it.habit.id }) { habitWithStatus ->
                    HabitItem(
                        habitWithStatus = habitWithStatus,
                        onToggle = {
                            viewModel.onToggleCompletion(
                                habitWithStatus.habit.id,
                                habitWithStatus.isCompletedToday
                            )
                        },
                        onClick = {
                            navController.navigate(
                                AveryTaskRoute.HabitAnalytics.createRoute(habitWithStatus.habit.id)
                            )
                        },
                        onLongClick = { habitToDelete = habitWithStatus }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    habitToDelete?.let { hws ->
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = { Text("Delete Habit") },
            text = { Text("Delete \"${hws.habit.name}\"? All completion history will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteHabit(hws.habit.id)
                    habitToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HabitItem(
    habitWithStatus: HabitWithStatus,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val habit = habitWithStatus.habit
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.color))
    } catch (_: Exception) {
        Color(0xFF4A90D9)
    }

    val scale by animateFloatAsState(
        targetValue = if (habitWithStatus.isCompletedToday) 1.0f else 1.0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "checkScale"
    )

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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(habitColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = habit.icon,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name + streak info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (habitWithStatus.currentStreak > 0) {
                        StreakBadge(streak = habitWithStatus.currentStreak)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "day streak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "${habitWithStatus.completionsThisWeek}/${habit.targetFrequency} this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Weekly progress dots
                Spacer(modifier = Modifier.height(4.dp))
                WeeklyDots(
                    completionsThisWeek = habitWithStatus.completionsThisWeek,
                    target = if (habit.frequencyPeriod == "weekly") habit.targetFrequency else 7,
                    color = habitColor
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Circular checkbox
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .then(
                        if (habitWithStatus.isCompletedToday) {
                            Modifier.background(habitColor)
                        } else {
                            Modifier.border(2.dp, habitColor, CircleShape)
                        }
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (habitWithStatus.isCompletedToday) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyDots(
    completionsThisWeek: Int,
    target: Int,
    color: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(target.coerceAtMost(7)) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < completionsThisWeek) color
                        else color.copy(alpha = 0.2f)
                    )
            )
        }
    }
}
