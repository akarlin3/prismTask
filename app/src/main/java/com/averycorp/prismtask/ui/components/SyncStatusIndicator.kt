package com.averycorp.prismtask.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

sealed class SyncState {
    data object Synced : SyncState()
    data object Syncing : SyncState()
    data class Pending(val count: Int) : SyncState()
    data object Offline : SyncState()
    data class Error(val message: String) : SyncState()
    data object NotSignedIn : SyncState()
}

@Composable
fun SyncStatusIndicator(
    syncState: SyncState,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (syncState) {
        is SyncState.Synced -> {} // no indicator when synced
        is SyncState.NotSignedIn -> {} // no indicator
        is SyncState.Syncing -> {
            StatusChip(
                text = "Syncing...",
                color = MaterialTheme.colorScheme.primary,
                showSpinner = true,
                modifier = modifier
            )
        }
        is SyncState.Pending -> {
            StatusChip(
                text = "${syncState.count} pending",
                color = Color(0xFFF59E0B),
                onClick = onTap,
                modifier = modifier
            )
        }
        is SyncState.Offline -> {
            StatusChip(
                text = "Offline",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
        }
        is SyncState.Error -> {
            StatusChip(
                text = "Sync Error",
                color = MaterialTheme.colorScheme.error,
                onClick = onTap,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color,
    showSpinner: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = color
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
