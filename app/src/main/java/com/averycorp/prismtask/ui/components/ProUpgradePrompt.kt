package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.billing.UserTier

@Composable
fun UpgradePrompt(
    currentTier: UserTier,
    requiredTier: UserTier,
    feature: String,
    description: String,
    onUpgrade: (UserTier) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                // Feature requires ULTRA
                requiredTier == UserTier.ULTRA -> {
                    Text(
                        text = "Ultra Feature",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onUpgrade(UserTier.ULTRA) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C3AED)
                        )
                    ) {
                        Text("Upgrade to Ultra \u2014 \$9.99/month")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Maybe Later")
                    }
                }

                // FREE user, feature requires PRO
                currentTier == UserTier.FREE && requiredTier == UserTier.PRO -> {
                    Text(
                        text = "Pro Feature",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "7-day free trial",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onUpgrade(UserTier.PRO) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upgrade to Pro \u2014 \$3.99/month")
                    }
                    TextButton(onClick = { onUpgrade(UserTier.PREMIUM) }) {
                        Text("Or Get Premium With All Features \u2014 \$7.99/month")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Maybe Later")
                    }
                }

                // FREE user, feature requires PREMIUM
                currentTier == UserTier.FREE && requiredTier == UserTier.PREMIUM -> {
                    Text(
                        text = "Premium Feature",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706) // Amber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "7-day free trial",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onUpgrade(UserTier.PREMIUM) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706)
                        )
                    ) {
                        Text("Upgrade to Premium \u2014 \$7.99/month")
                    }
                    TextButton(onClick = { onUpgrade(UserTier.PRO) }) {
                        Text("Or Start With Pro \u2014 \$3.99/month")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Maybe Later")
                    }
                }

                // PRO user, feature requires PREMIUM
                currentTier == UserTier.PRO && requiredTier == UserTier.PREMIUM -> {
                    Text(
                        text = "Upgrade to Premium",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706) // Amber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You\u2019re on Pro. Unlock $feature with Premium.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onUpgrade(UserTier.PREMIUM) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706)
                        )
                    ) {
                        Text("Upgrade \u2014 \$7.99/month (+\$4.00 From Pro)")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Maybe Later")
                    }
                }
            }
        }
    }
}

/**
 * Legacy wrapper for backward compatibility with existing screens.
 */
@Composable
fun ProUpgradePrompt(
    feature: ProFeature,
    currentTier: UserTier = UserTier.FREE,
    requiredTier: UserTier = UserTier.PRO,
    onUpgrade: (UserTier) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    UpgradePrompt(
        currentTier = currentTier,
        requiredTier = requiredTier,
        feature = feature.label,
        description = feature.description,
        onUpgrade = onUpgrade,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

enum class ProFeature(val label: String, val description: String) {
    AI("AI Features", "Let AI organize your tasks and plan focus sessions"),
    SYNC("Cloud Sync", "Sync your tasks across devices with cloud backup"),
    COLLABORATION("Collaboration", "Share projects and work with your team"),
    DRIVE_BACKUP("Google Drive Backup", "Back up your data to Google Drive"),
    AI_BRIEFING("AI Briefing", "Get AI-powered daily briefings and task prioritization"),
    AI_WEEKLY_PLAN("AI Weekly Planner", "Let AI plan your week for optimal productivity"),
    AI_TIME_BLOCK("AI Time Blocking", "AI-powered automatic schedule optimization"),
    AI_CHAT("AI Coach", "Get personalized coaching and task help through natural conversation"),
    AI_COACHING("AI Coaching", "Get personalized help when you're stuck on a task"),
    AI_TASK_BREAKDOWN("AI Task Breakdown", "Unlimited AI-powered task breakdown into subtasks"),
    AI_DAILY_PLANNING("AI Daily Planning", "AI-powered daily planning that adapts to your energy"),
    AI_REENGAGEMENT("AI Welcome Back", "Personalized re-engagement after time away")
}
