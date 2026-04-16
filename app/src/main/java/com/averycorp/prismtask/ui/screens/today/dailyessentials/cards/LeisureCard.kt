package com.averycorp.prismtask.ui.screens.today.dailyessentials.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.LeisureCardState
import com.averycorp.prismtask.domain.usecase.LeisureKind

@Composable
fun LeisureCard(
    state: LeisureCardState,
    onTapBody: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (state.kind) {
        LeisureKind.MUSIC -> Color(0xFF8B5CF6)
        LeisureKind.FLEX -> Color(0xFF10B981)
    }
    val title = when (state.kind) {
        LeisureKind.MUSIC -> "Music"
        LeisureKind.FLEX -> "Flex Leisure"
    }
    val pick = state.pickedForToday
    val description = if (pick == null) {
        "$title — no pick for today"
    } else {
        "$title: $pick, ${if (state.doneForToday) "done" else "not done"}"
    }

    DailyEssentialCard(
        accent = accent,
        contentDescription = description,
        onClick = onTapBody,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = pick ?: "Tap to pick",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (state.doneForToday) TextDecoration.LineThrough else null,
                    color = if (state.doneForToday) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (pick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                ToggleIcon(done = state.doneForToday, accent = accent, onToggle = onToggleDone)
            }
        }
    }
}

@Composable
private fun ToggleIcon(done: Boolean, accent: Color, onToggle: () -> Unit) {
    if (done) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Mark not done",
            tint = accent,
            modifier = Modifier.clickable(onClick = onToggle)
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.RadioButtonUnchecked,
            contentDescription = "Mark done",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onToggle)
        )
    }
}
