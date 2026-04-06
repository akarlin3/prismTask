package com.averykarlin.averytask.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val milestones = setOf(7, 14, 21, 30, 60, 90, 100, 365)

@Composable
fun StreakBadge(
    streak: Int,
    modifier: Modifier = Modifier
) {
    if (streak <= 0) return

    val isMilestone = streak in milestones
    val fireEmoji = when {
        streak >= 30 -> "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25"
        streak >= 7 -> "\uD83D\uDD25\uD83D\uDD25"
        else -> "\uD83D\uDD25"
    }

    val scale = if (isMilestone) {
        val transition = rememberInfiniteTransition(label = "streakPulse")
        val pulseScale by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        pulseScale
    } else {
        1.0f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.scale(scale)
    ) {
        Text(
            text = "$fireEmoji $streak",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
