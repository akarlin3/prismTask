package com.averycorp.prismtask.ui.screens.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationRuleListScreen(
    navController: NavHostController,
    viewModel: AutomationRuleListViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automation") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(PrismTaskRoute.AutomationTemplateLibrary.route)
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.LibraryBooks,
                            contentDescription = "Browse Templates"
                        )
                    }
                    IconButton(onClick = {
                        navController.navigate(PrismTaskRoute.AutomationLog.createRoute())
                    }) {
                        Icon(Icons.Filled.History, contentDescription = "Run History")
                    }
                }
            )
        }
    ) { padding ->
        if (rules.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rules, key = { it.id }) { row ->
                RuleCard(
                    row = row,
                    onToggle = { enabled -> viewModel.setEnabled(row.id, enabled) },
                    onRunNow = { viewModel.runNow(row.id) },
                    onViewLog = {
                        navController.navigate(PrismTaskRoute.AutomationLog.createRoute(row.id))
                    },
                    onDelete = { viewModel.delete(row.id) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Automation Rules Yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sample rules are seeded on first launch and appear here disabled. Toggle one on, or tap the library icon above to browse the full starter catalog.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RuleCard(
    row: AutomationRuleRow,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onViewLog: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                    if (!row.description.isNullOrBlank()) {
                        Text(
                            text = row.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = row.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.triggerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (row.usesAi) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = lastFiredLabel(row.lastFiredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (row.triggerIsManual) {
                    IconButton(onClick = onRunNow) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Run Now")
                    }
                }
                OverflowMenu(
                    onViewLog = onViewLog,
                    onDelete = if (row.isBuiltIn) null else onDelete
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(onViewLog: () -> Unit, onDelete: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("View Run History") },
                onClick = { expanded = false; onViewLog() }
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete Rule") },
                    onClick = { expanded = false; onDelete() }
                )
            }
        }
    }
}

private fun lastFiredLabel(millis: Long?): String {
    if (millis == null) return "Never fired"
    val ageMs = System.currentTimeMillis() - millis
    return when {
        ageMs < 60_000 -> "Just fired"
        ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
        ageMs < 86_400_000 -> "${ageMs / 3_600_000}h ago"
        else -> "${ageMs / 86_400_000}d ago"
    }
}
