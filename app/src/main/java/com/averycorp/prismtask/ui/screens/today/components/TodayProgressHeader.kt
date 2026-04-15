package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import com.averycorp.prismtask.ui.theme.LocalPrismFonts
import com.averycorp.prismtask.ui.theme.LocalPrismTheme
import com.averycorp.prismtask.ui.theme.PrismTheme
import com.averycorp.prismtask.ui.theme.prismDisplayFont
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
    val colors = LocalPrismColors.current
    val fonts = LocalPrismFonts.current
    val prismTheme = LocalPrismTheme.current
    val displayFont = prismDisplayFont(prismTheme)

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
        targetValue = colors.primary,
        animationSpec = tween(400),
        label = "headerBarColor"
    )
    // Cyberpunk / Synthwave themes get a primary→secondary gradient fill
    // on the linear progress indicator; Matrix / Void render a solid
    // primary fill. The brush is used only by the linear style — the
    // ring indicator always takes a single color.
    val useGradient = prismTheme == PrismTheme.CYBERPUNK || prismTheme == PrismTheme.SYNTHWAVE
    val progressBrush = remember(prismTheme, colors.primary, colors.secondary) {
        if (useGradient) {
            Brush.linearGradient(listOf(colors.primary, colors.secondary))
        } else {
            Brush.linearGradient(listOf(colors.primary, colors.primary))
        }
    }
    val barScale by animateFloatAsState(
        targetValue = if (celebrate) 1.6f else 1f,
        animationSpec = tween(350),
        label = "headerBarScale"
    )

    Surface(
        color = colors.background,
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
                    fontFamily = displayFont,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = fonts,
                    color = colors.muted,
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
                            color = colors.surface,
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
                            fontFamily = fonts,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                "percentage" -> {
                    Text(
                        text = "$completed done",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = displayFont,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                else -> {
                    // Gradient (Cyberpunk / Synthwave) uses a Box-based fill
                    // since Material's LinearProgressIndicator only accepts a
                    // single Color. Matrix / Void render a solid-primary
                    // LinearProgressIndicator for the standard look.
                    if (useGradient) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((4f * barScale).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .height((4f * barScale).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(progressBrush)
                            )
                        }
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height((4f * barScale).dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = barColor,
                            trackColor = colors.surface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "$completed done",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = fonts,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
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
                        tint = colors.muted
                    )
                }
            }
        }
    }
}
