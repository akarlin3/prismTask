package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRow

@Composable
fun BackupExportSection(
    isDriveExporting: Boolean,
    isDriveImporting: Boolean,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportJson: () -> Unit,
    onExportToDrive: () -> Unit,
    onImportFromDrive: () -> Unit
) {
    SectionHeader("Backup & Export")

    SettingsRow(title = "Export as JSON", onClick = onExportJson)
    SettingsRow(title = "Export as CSV", onClick = onExportCsv)
    SettingsRow(title = "Import from JSON", onClick = onImportJson)

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Google Drive",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedButton(
            onClick = onExportToDrive,
            enabled = !isDriveExporting && !isDriveImporting,
            modifier = Modifier.weight(1f)
        ) {
            if (isDriveExporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Saving...")
            } else {
                Text("Backup to Drive")
            }
        }
        OutlinedButton(
            onClick = onImportFromDrive,
            enabled = !isDriveImporting && !isDriveExporting,
            modifier = Modifier.weight(1f)
        ) {
            if (isDriveImporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restoring...")
            } else {
                Text("Restore from Drive")
            }
        }
    }

    HorizontalDivider()
}
