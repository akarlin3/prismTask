package com.averycorp.prismtask.ui.screens.settings.sections

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.ui.components.settings.SectionHeader

@Composable
fun SubscriptionSection(
    userTier: UserTier,
    onLaunchUpgrade: (Activity, UserTier) -> Unit,
    onRestorePurchases: () -> Unit
) {
    val context = LocalContext.current
    SectionHeader("Subscription")
    when (userTier) {
        UserTier.ULTRA -> {
            Text(
                text = "\u2B50 PrismTask Ultra",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7C3AED),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "You have access to all features with Claude Sonnet AI",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Manage Subscription")
            }
        }
        UserTier.PREMIUM -> {
            Text(
                text = "PrismTask Premium",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Upgrade to Ultra for enhanced AI powered by Claude Sonnet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = {
                    val activity = context as? Activity ?: return@Button
                    onLaunchUpgrade(activity, UserTier.ULTRA)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED)
                )
            ) {
                Text("Upgrade to Ultra \u2014 \$9.99/month")
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Manage Subscription")
            }
        }
        UserTier.PRO -> {
            Text(
                text = "PrismTask Pro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Upgrade to Premium for AI briefing, planner, collaboration, and more",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = {
                    val activity = context as? Activity ?: return@Button
                    onLaunchUpgrade(activity, UserTier.PREMIUM)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706)
                )
            ) {
                Text("Upgrade to Premium \u2014 \$7.99/month")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    val activity = context as? Activity ?: return@Button
                    onLaunchUpgrade(activity, UserTier.ULTRA)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED)
                )
            ) {
                Text("Upgrade to Ultra \u2014 \$9.99/month")
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Manage Subscription")
            }
        }
        UserTier.FREE -> {
            Text(
                text = "PrismTask Free",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            SubscriptionComparisonCard()
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val activity = context as? Activity ?: return@Button
                    onLaunchUpgrade(activity, UserTier.PRO)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Pro \u2014 \$3.99/month")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    val activity = context as? Activity ?: return@Button
                    onLaunchUpgrade(activity, UserTier.PREMIUM)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706)
                )
            ) {
                Text("Start Premium \u2014 \$7.99/month")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    val activity = context as? Activity ?: return@Button
                    onLaunchUpgrade(activity, UserTier.ULTRA)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED)
                )
            ) {
                Text("Start Ultra \u2014 \$9.99/month")
            }
            TextButton(onClick = onRestorePurchases) {
                Text("Restore Purchases")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SubscriptionComparisonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "",
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Free",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Pro\n\$3.99",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Prem\n\$7.99",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Ultra\n\$9.99",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7C3AED),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            ComparisonRow("Core Tasks & Habits", free = true, pro = true, premium = true, ultra = true)
            ComparisonRow("Calendar Sync", free = true, pro = true, premium = true, ultra = true)
            ComparisonRow("Templates (Local)", free = true, pro = true, premium = true, ultra = true)
            ComparisonRow("Cloud Sync", free = false, pro = true, premium = true, ultra = true)
            ComparisonRow("AI Eisenhower & Pomodoro", free = false, pro = true, premium = true, ultra = true)
            ComparisonRow("Analytics & Time Tracking", free = false, pro = true, premium = true, ultra = true)
            ComparisonRow("AI Briefing & Planner", free = false, pro = false, premium = true, ultra = true)
            ComparisonRow("Collaboration", free = false, pro = false, premium = true, ultra = true)
            ComparisonRow("Integrations", free = false, pro = false, premium = true, ultra = true)
            ComparisonRow("Claude Sonnet AI", free = false, pro = false, premium = false, ultra = true)
        }
    }
}

@Composable
private fun ComparisonRow(
    feature: String,
    free: Boolean,
    pro: Boolean,
    premium: Boolean,
    ultra: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TierCheck(enabled = free, modifier = Modifier.weight(0.8f))
        TierCheck(enabled = pro, modifier = Modifier.weight(0.8f))
        TierCheck(enabled = premium, modifier = Modifier.weight(0.8f))
        TierCheck(enabled = ultra, modifier = Modifier.weight(0.8f))
    }
}

@Composable
private fun TierCheck(enabled: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (enabled) "\u2705" else "\u2014",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
