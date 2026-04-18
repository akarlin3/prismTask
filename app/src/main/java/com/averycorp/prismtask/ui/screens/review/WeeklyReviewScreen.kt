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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.api.WeeklyReviewResponse
import com.averycorp.prismtask.domain.usecase.WeeklyReviewStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReviewScreen(
    navController: NavController,
    viewModel: WeeklyReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userTier by viewModel.userTier.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Review", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = uiState) {
                WeeklyReviewUiState.Idle, WeeklyReviewUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(
                                "Aggregating your week\u2026",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                WeeklyReviewUiState.Empty -> EmptyStateView()

                is WeeklyReviewUiState.Success -> SuccessView(
                    thisWeek = s.thisWeek,
                    localNarrative = s.localNarrative,
                    backendResponse = s.backendResponse,
                    userTier = userTier,
                    dateFormat = dateFormat
                )

                is WeeklyReviewUiState.Error -> {
                    // Dismissible banner + local fallback content. The
                    // spec explicitly calls for the local narrative to
                    // render here so the user still sees something
                    // useful when the backend is unreachable.
                    ErrorBanner(
                        message = s.message,
                        onDismiss = { viewModel.dismissErrorBanner() }
                    )
                    SuccessView(
                        thisWeek = s.thisWeek,
                        localNarrative = s.localNarrative,
                        backendResponse = null,
                        userTier = userTier,
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Nothing to review this week yet.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Come back after you complete some tasks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SuccessView(
    thisWeek: WeeklyReviewStats,
    localNarrative: WeeklyReviewNarrative,
    backendResponse: WeeklyReviewResponse?,
    userTier: UserTier,
    dateFormat: SimpleDateFormat
) {
    Text(
        text = "${dateFormat.format(Date(thisWeek.weekStart))} – ${dateFormat.format(Date(thisWeek.weekEnd - 1))}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReviewMetricCard("Completed", thisWeek.completed.toString(), Modifier.weight(1f))
        ReviewMetricCard("Slipped", thisWeek.slipped.toString(), Modifier.weight(1f))
        ReviewMetricCard(
            "Rate",
            "${(thisWeek.completionRate * 100).toInt()}%",
            Modifier.weight(1f)
        )
    }

    // Narrative sections: Pro-tier Sonnet output takes precedence when
    // present. Each section hides if empty (NarrativeSection handles
    // this via early return). Falls back to local narrative for Free
    // users and for Pro users on a backend fallback.
    if (backendResponse != null) {
        if (backendResponse.narrative.isNotBlank()) {
            NarrativeProseCard(narrative = backendResponse.narrative)
        }
        NarrativeSection(
            title = "Wins",
            emoji = "\uD83C\uDF31",
            items = backendResponse.wins,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        )
        NarrativeSection(
            title = "Slips",
            emoji = "\uD83D\uDCDD",
            items = backendResponse.slips,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onContainer = MaterialTheme.colorScheme.onSurface
        )
        NarrativeSection(
            title = "Patterns",
            emoji = "\uD83D\uDD0D",
            items = backendResponse.patterns,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer
        )
        NarrativeSection(
            title = "Focus For Next Week",
            emoji = "\u2728",
            items = backendResponse.nextWeekFocus,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
        )
    } else {
        NarrativeSection(
            title = "What Went Well",
            emoji = "\uD83C\uDF31",
            items = localNarrative.wins,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        )
        NarrativeSection(
            title = "What Slipped",
            emoji = "\uD83D\uDCDD",
            items = localNarrative.misses,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onContainer = MaterialTheme.colorScheme.onSurface
        )
        NarrativeSection(
            title = "Suggestions For Next Week",
            emoji = "\u2728",
            items = localNarrative.suggestions,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    if (thisWeek.slippedTasks.isNotEmpty()) {
        CarryForwardSection(tasks = thisWeek.slippedTasks)
    }

    // Subtle Pro upsell for Free users. Non-blocking — the local
    // narrative above is already rendered. Just an affordance in case
    // the user wants the richer AI review.
    if (userTier != UserTier.PRO) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Upgrade to Pro for AI-generated insights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
private fun CarryForwardSection(tasks: List<TaskEntity>) {
    Text(
        text = "Carry Forward",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    tasks.take(5).forEach { task ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Text(
                text = task.title,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReviewMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NarrativeSection(
    title: String,
    emoji: String,
    items: List<String>,
    containerColor: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color
) {
    if (items.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
            }
            Spacer(modifier = Modifier.padding(4.dp))
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
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}
