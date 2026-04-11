package com.averycorp.prismtask.ui.screens.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteConversationScreen(
    navController: NavController,
    sharedText: String? = null,
    viewModel: PasteConversationViewModel = hiltViewModel()
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val createdCount by viewModel.createdCount.collectAsStateWithLifecycle()

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            viewModel.onInputChange(sharedText)
            viewModel.extract()
        }
    }

    LaunchedEffect(createdCount) {
        if (createdCount != null) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extract Tasks", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Paste conversation text (Claude, ChatGPT, email, meeting notes).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onInputChange,
                label = { Text("Conversation Text") },
                minLines = 6,
                maxLines = 16,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.extract() },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Extract Tasks")
            }

            if (candidates.isEmpty()) {
                Text(
                    if (input.isBlank()) "Paste some text above and tap Extract Tasks."
                    else "Tap Extract Tasks to find action items.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${candidates.size} candidate${if (candidates.size == 1) "" else "s"} found",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                candidates.forEachIndexed { index, candidate ->
                    CandidateRow(
                        candidate = candidate,
                        onToggle = { viewModel.toggle(index) },
                        onTitleChange = { viewModel.editTitle(index, it) }
                    )
                }
                Button(
                    onClick = { viewModel.createSelected() },
                    enabled = candidates.any { it.selected },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedCount = candidates.count { it.selected }
                    Text("Create $selectedCount Task${if (selectedCount == 1) "" else "s"}")
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: EditableCandidate,
    onToggle: () -> Unit,
    onTitleChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = candidate.selected, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = candidate.title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Confidence ${(candidate.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
