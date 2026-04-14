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

@Composable
fun DebugLogAdminSection(
    onViewDebugLog: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Debug Log",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    Text(
        text = "View the recent logcat output for this app process. Useful for diagnosing crashes and unexpected behavior.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    OutlinedButton(
        onClick = onViewDebugLog,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("View Debug Log")
    }
}
