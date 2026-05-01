package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.prismtask.ui.theme.LocalPrismColors

/**
 * Compact circular badge that shows today's productivity score (0–100) in
 * the Today screen header. Hidden when the user has fewer than three days
 * of completion history — the threshold avoids showing meaningless 100%
 * scores to brand-new users where the score model trivially maxes out
 * (no tasks due → DEFAULT_RATE = 100).
 *
 * Tapping the badge navigates to the analytics dashboard via [onClick].
 */
@Composable
fun ProductivityScoreBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayScoreBadgeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val score = state.todayScore ?: return
    if (!state.hasEnoughHistory) return

    val colors = LocalPrismColors.current
    val tint: Color = when {
        score >= 80 -> colors.successColor
        score >= 60 -> colors.primary
        score >= 40 -> colors.warningColor
        else -> colors.destructiveColor
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Today's productivity score: $score out of 100. Tap to open analytics."
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$score",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
