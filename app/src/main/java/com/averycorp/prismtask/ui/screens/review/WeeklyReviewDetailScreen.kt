package com.averycorp.prismtask.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReviewDetailScreen(
    navController: NavController,
    viewModel: WeeklyReviewDetailViewModel = hiltViewModel()
) {
    val review by viewModel.review.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = review?.let { "Week of ${dateFormat.format(Date(it.weekStartDate))}" }
                        ?: "Weekly Review"
                    Text(titleText, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val entity = review
        if (entity == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Review not found.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val content = remember(entity.id) {
            WeeklyReviewContent.of(entity.metricsJson, entity.aiInsightsJson)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.padding(top = 4.dp))
            ActivitySummaryCard(summary = content.activitySummary)
            CategoryBreakdown(summary = content.activitySummary)

            if (content.narrative.isNotBlank()) {
                NarrativeProseCard(content.narrative)
            }
            NarrativeList(
                title = "Patterns",
                emoji = "🔍",
                items = content.patterns,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer
            )
            NarrativeList(
                title = "Next Week Focus",
                emoji = "✨",
                items = content.nextWeekFocus,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (content.wins.isNotEmpty()) {
                NarrativeList(
                    title = "Wins",
                    emoji = "🌱",
                    items = content.wins,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    onContainer = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (content.slips.isNotEmpty()) {
                NarrativeList(
                    title = "Slips",
                    emoji = "📝",
                    items = content.slips,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    onContainer = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun ActivitySummaryCard(summary: WeeklyReviewContent.ActivitySummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard("Completed", summary.completed.toString(), Modifier.weight(1f))
        MetricCard("Slipped", summary.slipped.toString(), Modifier.weight(1f))
        MetricCard("Rescheduled", summary.rescheduled.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NarrativeProseCard(narrative: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Text(
            narrative,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NarrativeList(
    title: String,
    emoji: String,
    items: List<String>,
    containerColor: Color,
    onContainer: Color
) {
    if (items.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
            }
            Spacer(Modifier.padding(4.dp))
            items.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(summary: WeeklyReviewContent.ActivitySummary) {
    val nonZero = summary.byCategory
        .filterKeys { it.isNotBlank() }
        .filterValues { it > 0 }
        .entries
        .sortedByDescending { it.value }
    if (nonZero.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "By Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.padding(4.dp))
            nonZero.forEach { (key, count) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = key.lowercase().replaceFirstChar { it.titlecase() }.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
