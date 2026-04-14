package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Temporary settings entry that opens the notification diagnostic screen.
 * Only rendered for debug builds or admin users. Remove before release.
 */
@Composable
fun NotificationDiagnosticSection(
    onOpenNotificationDiagnostic: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Notification Diagnostics",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    Text(
        text = "Inspect notification permissions, channels, and exact-alarm status. Fire test notifications to troubleshoot Samsung delivery issues. Temporary — will be removed before release.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    OutlinedButton(
        onClick = onOpenNotificationDiagnostic,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open Notification Diagnostics")
    }
}
