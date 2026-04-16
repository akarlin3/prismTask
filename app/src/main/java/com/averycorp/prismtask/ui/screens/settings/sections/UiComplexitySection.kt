package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.model.UiComplexityTier
import com.averycorp.prismtask.ui.components.UiTierCard
import com.averycorp.prismtask.ui.components.settings.SectionHeader

/**
 * Settings section that lets the user pick their UI complexity tier.
 * Renders three selection cards and an expandable advisory text block.
 *
 * Switching UP applies immediately with a Snackbar message (handled by caller).
 * Switching DOWN shows a confirmation dialog.
 */
@Composable
fun UiComplexitySection(
    currentTier: UiComplexityTier,
    onTierSelected: (UiComplexityTier) -> Unit,
    onUpgradeMessage: ((String) -> Unit)? = null
) {
    var pendingDowngrade by remember { mutableStateOf<UiComplexityTier?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Experience Level")

        // Tier selection cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UiComplexityTier.entries.forEach { tier ->
                UiTierCard(
                    tier = tier,
                    selected = currentTier == tier,
                    onClick = {
                        if (tier == currentTier) return@UiTierCard
                        if (tier.ordinal < currentTier.ordinal) {
                            // Switching DOWN — show confirmation
                            pendingDowngrade = tier
                        } else {
                            // Switching UP — apply immediately
                            onTierSelected(tier)
                            onUpgradeMessage?.invoke(
                                "Switched to ${tier.displayName}. More options are now available."
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Advisory expand/collapse
        var advisoryExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .clickable { advisoryExpanded = !advisoryExpanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Help Me Choose",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (advisoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (advisoryExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(
            visible = advisoryExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdvisoryBlock(
                    label = "Basic",
                    text = "New to task managers or prefer a distraction-free experience? " +
                        "Basic shows only what you need: add tasks, check them off, and stay on track."
                )
                AdvisoryBlock(
                    label = "Standard",
                    text = "The default. You get tags, projects, recurring tasks, habits, " +
                        "Pomodoro, and smart sorting \u2014 everything most users need, without the complexity."
                )
                AdvisoryBlock(
                    label = "Power",
                    text = "You know what you want. Power mode exposes every setting, every sort " +
                        "option, every filter, and every task field \u2014 nothing is hidden."
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Downgrade confirmation dialog
    pendingDowngrade?.let { newTier ->
        AlertDialog(
            onDismissRequest = { pendingDowngrade = null },
            title = { Text("Switch to ${newTier.displayName}?") },
            text = {
                Text(
                    "Some settings you've configured may be hidden until you switch back. " +
                        "Your data won't be lost."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onTierSelected(newTier)
                    pendingDowngrade = null
                }) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDowngrade = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdvisoryBlock(label: String, text: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
