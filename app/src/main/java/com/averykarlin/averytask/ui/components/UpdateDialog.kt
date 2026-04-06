package com.averykarlin.averytask.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averykarlin.averytask.data.remote.VersionInfo

@Composable
fun UpdateDialog(
    versionInfo: VersionInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (versionInfo.isMandatory) {
            {}
        } else {
            onDismiss
        },
        title = { Text("Update Available") },
        text = {
            Column {
                Text(
                    "Version ${versionInfo.versionName} is available.",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!versionInfo.releaseNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        versionInfo.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                versionInfo.apkSizeBytes?.let { size ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Size: %.1f MB".format(size / 1_048_576.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) { Text("Update") }
        },
        dismissButton = {
            if (!versionInfo.isMandatory) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    )
}
