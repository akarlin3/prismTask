package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sticky compact header bar shown in the Scaffold topBar slot.
 */
@Composable
internal fun CompactProgressHeader(
    completed: Int,
    total: Int,
    progress: Float,
    progressStyle: String = "ring",
    onAnalyticsClick: (() -> Unit)? = null
) {
    val dateLabel = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "headerProgress"
    )

    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(progress >= 1f && total > 0) {
        if (progress >= 1f && total > 0) {
            celebrate = true
            delay(900)
            celebrate = false
        }
    }
    val barColor by animateColorAsState(
        targetValue = when {
            progress >= 1f -> CompletedGreen
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(400),
        label = "headerBarColor"
    )
    val barScale by animateFloatAsState(
        targetValue = if (celebrate) 1.6f else 1f,
        animationSpec = tween(350),
        label = "headerBarScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(120.dp)) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when (progressStyle) {
                "ring" -> {
                    Box(
                        modifier = Modifier.size((36f * barScale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeWidth = 4.dp
                        )
                        CircularProgressIndicator(
                            progress = { animatedProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            color = barColor,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "$completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                "percentage" -> {
                    Text(
                        text = "$completed done",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                else -> {
                    LinearProgressIndicator(
                        progress = { animatedProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height((4f * barScale).dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "$completed done",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.onSurface
            )

            if (onAnalyticsClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onAnalyticsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = "Task Analytics",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
