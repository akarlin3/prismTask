package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.screens.settings.formatLastSync

@Composable
fun BackendSyncSection(
    backendConnected: Boolean,
    backendLastSyncAt: Long,
    isBackendSyncing: Boolean,
    isCloudExporting: Boolean,
    isCloudImporting: Boolean,
    onBackendSync: () -> Unit,
    onBackendDisconnect: () -> Unit,
    onExportToCloud: () -> Unit,
    onImportFromCloud: () -> Unit,
    onOpenAuthDialog: () -> Unit
) {
    SectionHeader("Backend Sync")

    Text(
        text = "Sync your data with the PrismTask backend. This is separate from Firebase sync \u2014 use either or both.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (backendConnected) {
        Text(
            text = if (backendLastSyncAt > 0L) {
                "Last Sync: ${formatLastSync(backendLastSyncAt)}"
            } else {
                "Last Sync: Never"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        AnimatedVisibility(visible = isBackendSyncing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Button(
                onClick = onBackendSync,
                enabled = !isBackendSyncing,
                modifier = Modifier.weight(1f)
            ) {
                if (isBackendSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync with Backend")
                }
            }
            OutlinedButton(
                onClick = onBackendDisconnect,
                enabled = !isBackendSyncing
            ) {
                Text("Disconnect")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            OutlinedButton(
                onClick = onExportToCloud,
                enabled = !isCloudExporting && !isCloudImporting,
                modifier = Modifier.weight(1f)
            ) {
                if (isCloudExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting...")
                } else {
                    Text("Export to Cloud")
                }
            }
            OutlinedButton(
                onClick = onImportFromCloud,
                enabled = !isCloudImporting && !isCloudExporting,
                modifier = Modifier.weight(1f)
            ) {
                if (isCloudImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importing...")
                } else {
                    Text("Import from Cloud")
                }
            }
        }
    } else {
        Text(
            text = "Not connected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Button(
            onClick = onOpenAuthDialog,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Connect to Backend")
        }
    }

    HorizontalDivider()
}
