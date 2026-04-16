package com.averycorp.prismtask.ui.screens.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.components.ContributionGrid
import com.averycorp.prismtask.ui.components.StreakBadge
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitAnalyticsScreen(
    navController: NavController,
    viewModel: HabitAnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val habit = state.habit

    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit?.color ?: "#4A90D9"))
    } catch (_: Exception) {
        Color(0xFF4A90D9)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habit Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (habit == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading...", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Header: icon + name + streak badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(habitColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = habit.icon, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    habit.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (habit.showStreak) {
                    StreakBadge(streak = state.currentStreak)
                }
            }

            // Stats cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (habit.showStreak) {
                    StatCard("Current", "\uD83D\uDD25 ${state.currentStreak}", habitColor, Modifier.weight(1f))
                    StatCard("Longest Run", "\uD83C\uDFC6 ${state.longestStreak}", habitColor, Modifier.weight(1f))
                }
                StatCard("Total", "\u2705 ${state.totalCompletions}", habitColor, Modifier.weight(1f))
            }

            // Contribution grid
            Text("Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ContributionGrid(
                completionsByDay = state.completionsByDay,
                targetPerDay = habit.targetFrequency,
                habitColor = habitColor,
                firstDayOfWeek = state.firstDayOfWeek,
                modifier = Modifier.fillMaxWidth()
            )

            // Completion rates
            Text("Completion Rates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            RateBar("7 days", state.rate7d, habitColor)
            RateBar("30 days", state.rate30d, habitColor)
            RateBar("90 days", state.rate90d, habitColor)

            // Weekly trend
            if (state.weeklyTotals.isNotEmpty()) {
                Text("Weekly Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                WeeklyTrendChart(
                    data = state.weeklyTotals,
                    color = habitColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            // Day of week chart
            if (state.dayOfWeekAverages.isNotEmpty()) {
                Text("By Day of Week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                DayOfWeekChart(
                    averages = state.dayOfWeekAverages,
                    bestDay = state.bestDay,
                    worstDay = state.worstDay,
                    color = habitColor,
                    firstDayOfWeek = state.firstDayOfWeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }

            // Action buttons
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { navController.navigate(PrismTaskRoute.AddEditHabit.createRoute(habit.id)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                Button(
                    onClick = {
                        viewModel.archiveHabit()
                        navController.popBackStack()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Archive", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RateBar(label: String, rate: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
        LinearProgressIndicator(
            progress = { rate },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${(rate * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun WeeklyTrendChart(data: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val maxVal = data.maxOrNull()?.coerceAtLeast(1) ?: 1
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (data.size < 2) return@Canvas
            val stepX = size.width / (data.size - 1)
            val path = Path()
            data.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (v.toFloat() / maxVal * size.height)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            data.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (v.toFloat() / maxVal * size.height)
                drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun DayOfWeekChart(
    averages: Map<DayOfWeek, Float>,
    bestDay: DayOfWeek?,
    worstDay: DayOfWeek?,
    color: Color,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    modifier: Modifier = Modifier
) {
    val days = (0 until 7).map { DayOfWeek.of((firstDayOfWeek.value - 1 + it) % 7 + 1) }
    val labels = days.map { it.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) }
    val maxVal = averages.values.maxOrNull()?.coerceAtLeast(0.1f) ?: 0.1f

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { i, day ->
                val avg = averages[day] ?: 0f
                val heightFraction = (avg / maxVal).coerceIn(0.05f, 1f)
                val barColor = when (day) {
                    bestDay -> Color(0xFF4CAF50)
                    worstDay -> Color(0xFFFF9800)
                    else -> color
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height((60 * heightFraction).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(labels[i], style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
