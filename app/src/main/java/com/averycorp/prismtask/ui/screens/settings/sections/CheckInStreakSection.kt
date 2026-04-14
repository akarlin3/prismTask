package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.SectionHeader

/**
 * Stat card showing the user's morning check-in streak (v1.4.0 V4 follow-up).
 *
 * Zero is rendered as an invitation instead of a number so users who
 * haven't started yet see a low-friction prompt rather than a sad "0".
 */
@Composable
fun CheckInStreakSection(streak: Int) {
    SectionHeader("Morning Check-In Streak")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (streak > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uD83D\uDD25",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "$streak-Day Streak",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (streak == 1) {
                        "You completed your first check-in today. One day at a time."
                    } else {
                        "Consecutive days you've checked in. Keep it going."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = "Start a morning check-in streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Open the Today screen in the morning and tap 'Let's Go' on the check-in prompt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    HorizontalDivider()
}
