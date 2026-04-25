package com.averycorp.prismtask.ui.screens.builtinupdates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.model.PendingBuiltInUpdate
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltInUpdatesScreen(
    navController: NavController,
    viewModel: BuiltInUpdatesViewModel = hiltViewModel()
) {
    val pending by viewModel.pendingUpdates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Built-in Updates", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (pending.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Your built-in habits are up to date.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pending, key = { it.templateKey }) { update ->
                PendingUpdateCard(
                    update = update,
                    onReview = {
                        navController.navigate(
                            PrismTaskRoute.BuiltInUpdateDiff.createRoute(update.templateKey)
                        )
                    },
                    onDismiss = { viewModel.dismiss(update.templateKey, update.toVersion) },
                    onDetach = { viewModel.detach(update.templateKey) }
                )
            }
        }
    }
}

@Composable
private fun PendingUpdateCard(
    update: PendingBuiltInUpdate,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    onDetach: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = update.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "v${update.fromVersion} → v${update.toVersion}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChangeSummary(update)
            Row(
                onReview = onReview,
                onDismiss = onDismiss,
                onDetach = onDetach
            )
        }
    }
}

@Composable
private fun ChangeSummary(update: PendingBuiltInUpdate) {
    val parts = mutableListOf<String>()
    if (update.addedStepCount > 0) parts += "+${update.addedStepCount} added"
    if (update.removedStepCount > 0) parts += "-${update.removedStepCount} removed"
    if (update.modifiedStepCount > 0) parts += "~${update.modifiedStepCount} changed"
    if (update.habitFieldChangeCount > 0) parts += "${update.habitFieldChangeCount} field(s) updated"
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun Row(
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    onDetach: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(onClick = onReview) { Text("Review") }
        TextButton(onClick = onDismiss) { Text("Dismiss") }
        TextButton(onClick = onDetach) { Text("Detach") }
    }
}
