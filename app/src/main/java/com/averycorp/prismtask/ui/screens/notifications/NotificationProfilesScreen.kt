package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

/**
 * Lists every notification profile, shows which is active, and lets the
 * user switch between them. Deleting a built-in profile is disabled so
 * the starter set always remains.
 */
@Composable
fun NotificationProfilesScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeProfileId.collectAsStateWithLifecycle()

    NotificationSubScreenScaffold("Profiles", navController) {
        SubHeader("Active profile")
        Text(
            "Your active profile applies to every incoming notification, unless a per-category override is in place.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        profiles.forEach { profile ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setActiveProfile(profile.id) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = profile.id == activeId,
                    onClick = { viewModel.setActiveProfile(profile.id) }
                )
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = profileSummary(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!profile.isBuiltIn) {
                    IconButton(onClick = { viewModel.deleteProfile(profile) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete profile")
                    }
                }
            }
        }

        SectionSpacer()
        Text(
            "Auto-switch rules let PrismTask change the active profile based on time, day, OS Focus mode, or a calendar event. Configure rules inside each profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun profileSummary(profile: com.averycorp.prismtask.data.local.entity.NotificationProfileEntity): String {
    val offsets = profile.offsets().joinToString(" \u2022 ") { offsetLabel(it) }
    return buildString {
        append(offsets.ifBlank { "At due time" })
        append(" \u2014 ")
        append(profile.urgencyTierKey.replaceFirstChar { it.uppercase() })
        if (profile.silent) append(" \u2022 Silent")
        if (profile.escalation) append(" \u2022 Escalates")
    }
}

private fun offsetLabel(ms: Long): String = when {
    ms == 0L -> "At due"
    ms >= 86_400_000 -> "${ms / 86_400_000}d before"
    ms >= 3_600_000 -> "${ms / 3_600_000}h before"
    ms >= 60_000 -> "${ms / 60_000}m before"
    else -> "${ms}ms"
}
