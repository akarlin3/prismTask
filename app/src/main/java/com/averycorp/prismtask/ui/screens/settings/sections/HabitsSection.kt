package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle

@Composable
fun HabitsSection(
    streakMaxMissedDays: Int,
    onStreakMaxMissedDaysChange: (Int) -> Unit
) {
    var showStreakDialog by remember { mutableStateOf(false) }

    if (showStreakDialog) {
        StreakMaxMissedDaysDialog(
            current = streakMaxMissedDays,
            onConfirm = {
                onStreakMaxMissedDaysChange(it)
                showStreakDialog = false
            },
            onDismiss = { showStreakDialog = false }
        )
    }

    SectionHeader("Habits")

    SettingsRowWithSubtitle(
        title = "Streak Grace Period",
        subtitle = subtitleForMissedDays(streakMaxMissedDays),
        onClick = { showStreakDialog = true }
    )

    HorizontalDivider()
}

private fun subtitleForMissedDays(days: Int): String = when (days) {
    1 -> "1 missed day ends a streak"
    else -> "$days missed days end a streak"
}

@Composable
private fun StreakMaxMissedDaysDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val min = HabitListPreferences.MIN_STREAK_MAX_MISSED_DAYS
    val max = HabitListPreferences.MAX_STREAK_MAX_MISSED_DAYS
    var value by remember(current) { mutableIntStateOf(current.coerceIn(min, max)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Streak Grace Period") },
        text = {
            Column {
                Text(
                    text = subtitleForMissedDays(value),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toInt().coerceIn(min, max) },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = (max - min) - 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$min day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$max days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Choose how many consecutive missed days break a daily-habit streak. " +
                        "At 1, any missed day ends the streak (original behavior). Higher values " +
                        "forgive occasional gaps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
