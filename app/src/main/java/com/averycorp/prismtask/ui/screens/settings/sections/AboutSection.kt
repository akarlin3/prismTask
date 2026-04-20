package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.ui.components.settings.SectionHeader

@Composable
fun AboutSection(
    latestReleaseTag: String?,
    onRefreshWidgets: (() -> Unit)? = null
) {
    SectionHeader("About")

    Text(
        text = "PrismTask v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    Text(
        text = "Latest GitHub Release: ${latestReleaseTag ?: "Loading..."}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text = "Made by Avery Karlin",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Hidden for v1.0 — widgets disabled until v1.2 re-enable
    if (BuildConfig.WIDGETS_ENABLED && onRefreshWidgets != null) {
        TextButton(onClick = onRefreshWidgets) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text("Refresh Widgets")
        }
    }
}
