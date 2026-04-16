package com.averycorp.prismtask.ui.screens.today.dailyessentials.cards

import androidx.compose.foundation.layout.Arrangement
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
import com.averycorp.prismtask.domain.usecase.HabitCardState

@Composable
fun HabitCard(
    state: HabitCardState,
    contentDescriptionPrefix: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = runCatching { Color(android.graphics.Color.parseColor(state.color)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val status = if (state.completedToday) "done today" else "not done today"

    DailyEssentialCard(
        accent = accent,
        contentDescription = "$contentDescriptionPrefix — ${state.name}, $status",
        onClick = onToggle,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(text = state.icon, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = state.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (state.completedToday) TextDecoration.LineThrough else null,
                color = if (state.completedToday) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f, fill = true)
            )
            if (state.completedToday) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
