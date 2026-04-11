package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.data.remote.UpdateStatus
import com.averycorp.prismtask.ui.components.settings.SectionHeader

@Composable
fun AppUpdateSection(
    updateStatus: UpdateStatus,
    updateError: String?,
    latestReleaseTag: String?,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstallUpdate: () -> Unit
) {
    SectionHeader("Debugging")

    val isCheckingUpdate = updateStatus == UpdateStatus.CHECKING
    val isDownloadingUpdate = updateStatus == UpdateStatus.DOWNLOADING
    val isUpdateBusy = isCheckingUpdate || isDownloadingUpdate

    Button(
        onClick = {
            when (updateStatus) {
                UpdateStatus.UPDATE_AVAILABLE -> onDownloadAndInstallUpdate()
                else -> onCheckForUpdate()
            }
        },
        enabled = !isUpdateBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isUpdateBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = when (updateStatus) {
                UpdateStatus.CHECKING -> "Checking..."
                UpdateStatus.DOWNLOADING -> "Downloading..."
                UpdateStatus.UPDATE_AVAILABLE -> "Download & Install Update"
                UpdateStatus.READY_TO_INSTALL -> "Install Update"
                else -> "Check for Update"
            }
        )
    }

    val statusText = when (updateStatus) {
        UpdateStatus.IDLE -> null
        UpdateStatus.CHECKING -> "Checking for updates..."
        UpdateStatus.UPDATE_AVAILABLE -> latestReleaseTag?.let { "Update available ($it)" } ?: "Update available"
        UpdateStatus.NO_UPDATE -> "You're on the latest build (v${BuildConfig.VERSION_NAME})"
        UpdateStatus.DOWNLOADING -> "Downloading APK..."
        UpdateStatus.READY_TO_INSTALL -> "Ready to install"
        UpdateStatus.ERROR -> updateError?.let { "Error: $it" } ?: "Update failed"
    }
    if (statusText != null) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (updateStatus == UpdateStatus.ERROR)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    HorizontalDivider()
}
