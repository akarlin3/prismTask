package com.averycorp.prismtask.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.prismtask.data.remote.api.HabitCorrelationItem
import com.averycorp.prismtask.data.repository.HabitCorrelationsOutcome
import com.averycorp.prismtask.ui.theme.LocalPrismColors

/**
 * Pro-only section on TaskAnalyticsScreen that surfaces AI-generated
 * habit correlations. The user kicks the analysis off with a button —
 * because the backend rate-limits to one call per day, we don't want
 * to fire on every scroll into view.
 *
 * Renders four distinct states (success, AI features disabled, rate
 * limited, generic error) with copy that matches what the user can
 * actually do about each.
 */
@Composable
fun HabitCorrelationsSection(
    accent: Color,
    modifier: Modifier = Modifier,
    viewModel: HabitCorrelationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPrismColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Habit Correlations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "How each habit affects your productivity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!state.isLoading && state.outcome !is HabitCorrelationsOutcome.RateLimited) {
                    Button(onClick = viewModel::analyze) {
                        Text("Analyze")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Asking the AI to compare your habit days against your task completion…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Card
            }

            when (val outcome = state.outcome) {
                null -> Text(
                    text = "Tap Analyze to compare your habits against your task completion. " +
                        "The analysis runs once per day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is HabitCorrelationsOutcome.Success -> {
                    val payload = outcome.response
                    if (payload.correlations.isEmpty()) {
                        Text(
                            text = payload.topInsight.ifBlank { "Not enough habit history to draw conclusions yet." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        payload.correlations.forEach { item ->
                            CorrelationCard(item = item, accent = accent)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (payload.topInsight.isNotBlank()) {
                            Text(
                                text = payload.topInsight,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = accent
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (payload.recommendation.isNotBlank()) {
                            Text(
                                text = payload.recommendation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HabitCorrelationsOutcome.AiFeaturesDisabled -> Text(
                    text = "AI features are disabled in your Settings. Re-enable them to use " +
                        "habit correlation analysis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.warningColor
                )

                HabitCorrelationsOutcome.RateLimited -> Text(
                    text = "You've already run today's analysis. Try again in 24 hours — the " +
                        "underlying habit data updates daily anyway.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HabitCorrelationsOutcome.NotPro -> Text(
                    text = "Habit correlation analysis is a Pro feature. Upgrade to unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.warningColor
                )

                is HabitCorrelationsOutcome.BackendUnavailable -> Text(
                    text = "Couldn't reach the analysis service. Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.destructiveColor
                )
            }
        }
    }
}

@Composable
private fun CorrelationCard(item: HabitCorrelationItem, accent: Color) {
    val colors = LocalPrismColors.current
    val badgeColor = when (item.correlation.lowercase()) {
        "positive", "strong_positive" -> colors.successColor
        "weak_positive" -> accent
        "negative", "strong_negative" -> colors.destructiveColor
        "weak_negative" -> colors.warningColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val badgeLabel = when (item.correlation.lowercase()) {
        "positive", "strong_positive" -> "Strong Positive"
        "weak_positive" -> "Weak Positive"
        "negative", "strong_negative" -> "Negative"
        "weak_negative" -> "Weak Negative"
        else -> "Neutral"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.habit,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductivityBar(
                    label = "Done",
                    value = item.doneProductivity,
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                ProductivityBar(
                    label = "Skipped",
                    value = item.notDoneProductivity,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
            }
            if (item.interpretation.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.interpretation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ProductivityBar(
    label: String,
    value: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "$label · ${value.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (value / 100.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    }
}
