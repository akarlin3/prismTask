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
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun AiSection(
    onNavigateToEisenhower: () -> Unit,
    onNavigateToSmartPomodoro: () -> Unit,
    onNavigateToDailyBriefing: () -> Unit,
    onNavigateToWeeklyPlanner: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    eisenhowerAutoClassifyEnabled: Boolean = true,
    onEisenhowerAutoClassifyChanged: (Boolean) -> Unit = {},
    aiFeaturesEnabled: Boolean = true,
    onAiFeaturesEnabledChanged: (Boolean) -> Unit = {}
) {
    SectionHeader("AI Features")

    Text(
        "AI features use Anthropic's Claude API to analyze your tasks, habits, " +
            "schedules, and — for natural-language batch commands — your medication " +
            "names. When the master toggle below is off, no PrismTask data is sent to " +
            "Anthropic and the AI-powered features become unavailable. " +
            "See Privacy Policy for full disclosure.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )

    SettingsToggleRow(
        title = "Use Claude AI for advanced features",
        subtitle = if (aiFeaturesEnabled) {
            "On — task / habit / project / medication names are sent to Anthropic for " +
                "AI-powered features. Anthropic does not train on inputs and deletes " +
                "them within 30 days under standard API terms."
        } else {
            "Off — no PrismTask data is sent to Anthropic. AI-powered features below " +
                "are inactive until you turn this back on."
        },
        checked = aiFeaturesEnabled,
        onCheckedChange = onAiFeaturesEnabledChanged
    )

    SettingsToggleRow(
        title = "Auto-Classify Tasks (Eisenhower)",
        subtitle = "Automatically assign an Eisenhower quadrant when you create a task. Manual moves are always preserved.",
        checked = eisenhowerAutoClassifyEnabled,
        onCheckedChange = onEisenhowerAutoClassifyChanged
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
