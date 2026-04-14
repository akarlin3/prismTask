package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle

@Composable
fun AiSection(
    onNavigateToEisenhower: () -> Unit,
    onNavigateToSmartPomodoro: () -> Unit,
    onNavigateToDailyBriefing: () -> Unit,
    onNavigateToWeeklyPlanner: () -> Unit,
    onNavigateToTimeline: () -> Unit
) {
    SectionHeader("AI Features")

    Text(
        "AI features use Claude to analyze your tasks. Requires internet connection.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )

    SettingsRowWithSubtitle(
        title = "Eisenhower Matrix",
        subtitle = "AI-powered task categorization into urgency/importance quadrants",
        onClick = onNavigateToEisenhower
    )

    SettingsRowWithSubtitle(
        title = "Smart Focus Sessions",
        subtitle = "AI-planned Pomodoro sessions based on your tasks",
        onClick = onNavigateToSmartPomodoro
    )

    SettingsRowWithSubtitle(
        title = "Daily Briefing",
        subtitle = "Morning summary with top priorities and suggested task order",
        onClick = onNavigateToDailyBriefing
    )

    SettingsRowWithSubtitle(
        title = "Weekly Planner",
        subtitle = "AI-generated week plan distributing tasks across days",
        onClick = onNavigateToWeeklyPlanner
    )

    SettingsRowWithSubtitle(
        title = "Time Blocking",
        subtitle = "Auto-schedule your day with AI-optimized time blocks",
        onClick = onNavigateToTimeline
    )

    HorizontalDivider()
}
