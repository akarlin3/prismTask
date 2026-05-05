package com.averycorp.prismtask.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.billing.UserTier

/**
 * Energy check-in card shown at the top of the Today view for Pro users.
 * Lets the user select their energy level (low/medium/high), then shows an
 * AI-generated daily plan.
 */
@Composable
fun EnergyCheckInCard(
    visible: Boolean,
    isLoading: Boolean,
    selectedEnergy: String?,
    planMessage: String?,
    userTier: UserTier,
    onSelectEnergy: (String) -> Unit,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
    onViewTrends: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How's Your Energy Today?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (userTier == UserTier.FREE) {
                        TierBadge(requiredTier = UserTier.PRO)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (userTier == UserTier.FREE) {
                    // Free users see upgrade prompt
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AI-powered daily planning that adapts to your energy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onUpgrade
                    ) {
                        Text("Upgrade to Pro")
                    }
                } else if (selectedEnergy == null && !isLoading) {
                    // Pro user: show energy selector
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EnergyChip(
                            label = "Low",
                            emoji = "\uD83D\uDE34",
                            selected = false,
                            onClick = { onSelectEnergy("low") },
                            modifier = Modifier.weight(1f)
                        )
                        EnergyChip(
                            label = "Medium",
                            emoji = "\uD83D\uDE10",
                            selected = false,
                            onClick = { onSelectEnergy("medium") },
                            modifier = Modifier.weight(1f)
                        )
                        EnergyChip(
                            label = "High",
                            emoji = "\u26A1",
                            selected = false,
                            onClick = { onSelectEnergy("high") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Planning your day...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else if (planMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = planMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (userTier != UserTier.FREE && selectedEnergy != null) {
                    TextButton(
                        onClick = onViewTrends,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("View Trends")
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyChip(
    label: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "$emoji $label",
                style = MaterialTheme.typography.bodySmall
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
