package com.averycorp.prismtask.ui.screens.builtinupdates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.averycorp.prismtask.domain.model.FieldChange
import com.averycorp.prismtask.domain.model.StepChange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDiffScreen(
    navController: NavController,
    viewModel: TemplateDiffViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val diff = state.diff

    LaunchedEffect(state.applied) {
        if (state.applied) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = diff?.let { "${displayName(it.templateKey)}: v${it.fromVersion} → v${it.toVersion}" }
                            ?: "Update",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.notFound || diff == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "No update available.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (diff.habitFieldChanges.isNotEmpty()) {
                Section(title = "Habit Fields") {
                    diff.habitFieldChanges.forEach { change ->
                        FieldRow(
                            change = change,
                            checked = change.fieldName in state.selections.acceptedFieldNames,
                            onToggle = { viewModel.toggleField(change.fieldName) }
                        )
                    }
                }
            }
            if (diff.addedSteps.isNotEmpty()) {
                Section(title = "Added Steps") {
                    diff.addedSteps.forEach { step ->
                        SimpleCheckRow(
                            label = step.label,
                            checked = step.stepId in state.selections.acceptedAddedStepIds,
                            onToggle = { viewModel.toggleAddedStep(step.stepId) }
                        )
                    }
                }
            }
            if (diff.modifiedSteps.isNotEmpty()) {
                Section(title = "Changed Steps") {
                    diff.modifiedSteps.forEach { change ->
                        StepChangeRow(
                            change = change,
                            checked = change.stepId in state.selections.acceptedModifiedStepIds,
                            onToggle = { viewModel.toggleModifiedStep(change.stepId) }
                        )
                    }
                }
            }
            if (diff.removedSteps.isNotEmpty()) {
                Section(title = "Removed Steps") {
                    Text(
                        "Default off — uncheck to keep these on your habit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    diff.removedSteps.forEach { step ->
                        SimpleCheckRow(
                            label = step.label,
                            checked = step.stepId in state.selections.acceptedRemovedStepIds,
                            onToggle = { viewModel.toggleRemovedStep(step.stepId) }
                        )
                    }
                }
            }
            if (diff.preservedUserSteps.isNotEmpty()) {
                Section(title = "Your Added Steps (always preserved)") {
                    diff.preservedUserSteps.forEach { step ->
                        Text("• ${step.label}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = { navController.popBackStack() }) {
                    Text("Cancel")
                }
                Button(onClick = viewModel::applySelected) {
                    Text("Apply Selected")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun FieldRow(change: FieldChange, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = "${change.fieldName}: ${change.currentValue ?: "—"} → ${change.proposedValue ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (change.userModified) {
                Text(
                    text = "you edited this — uncheck to keep your version",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SimpleCheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepChangeRow(change: StepChange, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.padding(start = 4.dp)) {
            if (change.labelChanged) {
                Text(
                    text = "\"${change.current.label}\" → \"${change.proposed.label}\"",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(text = change.current.label, style = MaterialTheme.typography.bodyMedium)
            }
            val sub = buildList {
                if (change.durationChanged) add("duration")
                if (change.tierChanged) add("tier")
                if (change.phaseChanged) add("phase")
                if (change.sortOrderChanged) add("order")
                if (change.noteChanged) add("note")
            }
            if (sub.isNotEmpty()) {
                Text(
                    text = sub.joinToString(", ") + " changed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun displayName(templateKey: String): String = templateKey
    .removePrefix("builtin_")
    .replace('_', ' ')
    .split(' ')
    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
