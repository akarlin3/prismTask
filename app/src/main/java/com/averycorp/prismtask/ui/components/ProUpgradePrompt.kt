package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.billing.BillingPeriod
import com.averycorp.prismtask.data.billing.UserTier

/**
 * Simplified two-tier upgrade prompt. Shown when a FREE user taps a Pro
 * feature. Offers both monthly and annual billing options; the annual plan
 * includes a 7-day free trial.
 */
@Composable
fun UpgradePrompt(
    currentTier: UserTier,
    feature: String,
    description: String,
    onUpgrade: (BillingPeriod) -> Unit,
    onRestorePurchase: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTier == UserTier.PRO) {
        // Pro users should not see an upgrade prompt — fail safe.
        return
    }
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
            Text(
                text = "Upgrade to PrismTask Pro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProFeatureBullets()
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onUpgrade(BillingPeriod.ANNUAL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Annual \u2014 \$4.99/mo (Save 37%)",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Best Value \u2014 \$59.88/year \u2022 7-Day Free Trial",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onUpgrade(BillingPeriod.MONTHLY) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Monthly \u2014 \$7.99/mo",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onRestorePurchase) {
                Text("Restore Purchase")
            }
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    }
}

@Composable
private fun ProFeatureBullets() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProBullet("Cloud sync across all devices")
        ProBullet("AI productivity tools (Eisenhower, Pomodoro, Briefing)")
        ProBullet("AI Weekly Planner powered by Claude Sonnet")
        ProBullet("Full analytics, time tracking, and Drive backup")
        ProBullet("Collaboration and Integrations")
        ProBullet("Unlimited saved filters and custom templates")
    }
}

@Composable
private fun ProBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "\u2022 ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Legacy wrapper retained for call-sites that pass a [ProFeature] enum value.
 * Forwards to the simplified [UpgradePrompt].
 */
@Composable
fun ProUpgradePrompt(
    feature: ProFeature,
    currentTier: UserTier = UserTier.FREE,
    onUpgrade: (BillingPeriod) -> Unit,
    onRestorePurchase: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    UpgradePrompt(
        currentTier = currentTier,
        feature = feature.label,
        description = feature.description,
        onUpgrade = onUpgrade,
        onRestorePurchase = onRestorePurchase,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

enum class ProFeature(
    val label: String,
    val description: String
) {
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
    AI_REENGAGEMENT("AI Welcome Back", "Personalized re-engagement after time away"),
    SYLLABUS_IMPORT("Syllabus Import", "Import your course syllabus and auto-create tasks, events, and schedules")
}
