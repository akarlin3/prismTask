package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.model.notifications.EscalationChain
import com.averycorp.prismtask.domain.model.notifications.EscalationStep
import com.averycorp.prismtask.domain.model.notifications.EscalationStepAction
import com.averycorp.prismtask.domain.model.notifications.UrgencyTier
import com.averycorp.prismtask.domain.usecase.NotificationProfileResolver
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationEscalationScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var chain by remember { mutableStateOf(profile.escalation) }

    NotificationSubScreenScaffold("Escalation chain", navController) {
        SettingsToggleRow(
            title = "Enable escalation",
            subtitle = "Fire an increasingly intrusive sequence until interacted with",
            checked = chain.enabled,
            onCheckedChange = { chain = chain.copy(enabled = it) }
        )
        SettingsToggleRow(
            title = "Stop on interaction",
            subtitle = "Any tap / snooze / dismiss cancels remaining steps",
            checked = chain.stopOnInteraction,
            onCheckedChange = { chain = chain.copy(stopOnInteraction = it) }
        )

        LabeledSlider(
            label = "Max attempts",
            value = chain.maxAttempts.toFloat().coerceIn(1f, 10f),
            valueRange = 1f..10f,
            steps = 8,
            format = { "${it.toInt()}" },
            onChange = { chain = chain.copy(maxAttempts = it.toInt()) }
        )

        SectionSpacer()
        SubHeader("Steps")
        if (chain.steps.isEmpty()) {
            Text("No steps yet \u2014 add one below.", style = MaterialTheme.typography.bodyMedium)
        }
        chain.steps.forEachIndexed { index, step ->
            EscalationStepRow(
                step = step,
                onUpdate = { updated ->
                    val next = chain.steps.toMutableList().also { it[index] = updated }
                    chain = chain.copy(steps = next)
                },
                onDelete = {
                    chain = chain.copy(steps = chain.steps.filterIndexed { i, _ -> i != index })
                }
            )
        }

        OutlinedButton(
            onClick = {
                chain = chain.copy(
                    steps = chain.steps + EscalationStep(
                        action = EscalationStepAction.STANDARD_ALERT,
                        delayMs = 2 * 60 * 1000L
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) { Text("Add step") }

        OutlinedButton(
            onClick = { chain = EscalationChain.DEFAULT_AGGRESSIVE },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) { Text("Use built-in aggressive chain") }

        Button(
            onClick = {
                val entity = profiles.firstOrNull { it.id == profile.id } ?: return@Button
                val json = NotificationProfileResolver.DEFAULT.encodeEscalationChain(chain)
                viewModel.commitProfileEdit(entity.copy(escalationChainJson = json, escalation = chain.enabled))
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Save") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EscalationStepRow(
    step: EscalationStep,
    onUpdate: (EscalationStep) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(step.action.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove step")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EscalationStepAction.values().forEach { action ->
                FilterChip(
                    selected = action == step.action,
                    onClick = { onUpdate(step.copy(action = action)) },
                    label = { Text(action.label) }
                )
            }
        }
        LabeledSlider(
            label = "Delay after previous step",
            value = (step.delayMs / 60_000f).coerceIn(0f, 60f),
            valueRange = 0f..60f,
            format = { "${it.toInt()} min" },
            onChange = { onUpdate(step.copy(delayMs = it.toLong() * 60_000L)) }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UrgencyTier.values().forEach { tier ->
                val checked = tier in step.triggerTiers || step.triggerTiers.isEmpty()
                FilterChip(
                    selected = checked,
                    onClick = {
                        val tiers = step.triggerTiers.toMutableSet()
                        if (tier in tiers) tiers.remove(tier) else tiers.add(tier)
                        onUpdate(step.copy(triggerTiers = tiers))
                    },
                    label = { Text(tier.label) }
                )
            }
        }
    }
}
