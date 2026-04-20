package com.averycorp.prismtask.ui.screens.habits.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.averycorp.prismtask.ui.theme.LocalPrismShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Small inline status badge used by [HabitItem] to surface booking
 * and previous-period state ("Booked", "Not Booked", "Last Week Done",
 * etc.). [activeColor] tints the pill when [active] is true; otherwise
 * the pill renders in the default surface tone.
 */
@Composable
internal fun StatusPill(
    label: String,
    active: Boolean,
    activeColor: Color
) {
    val bg = if (active) {
        activeColor.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (active) {
        activeColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(LocalPrismShapes.current.chip)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1
        )
    }
}

/**
 * Row of up-to-7 dots representing this week's completions vs. target:
 * filled dots = [completionsThisWeek], faded dots = remaining slots up
 * to [target] (capped at 7 to fit in the card row).
 */
@Composable
internal fun WeeklyDots(
    completionsThisWeek: Int,
    target: Int,
    color: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(target.coerceAtMost(7)) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < completionsThisWeek) {
                            color
                        } else {
                            color.copy(alpha = 0.2f)
                        }
                    )
            )
        }
    }
}
