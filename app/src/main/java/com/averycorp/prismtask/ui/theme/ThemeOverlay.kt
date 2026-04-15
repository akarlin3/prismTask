package com.averycorp.prismtask.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Apply a per-theme decorative overlay to the receiver [Modifier]. The
 * CYBERPUNK and MATRIX themes layer a very faint repeating horizontal
 * scan-line pattern on top of the underlying content to evoke a CRT /
 * terminal feel; SYNTHWAVE and VOID are intentionally no-ops so the
 * gradient / minimal aesthetics stay clean.
 *
 * Meant to be attached to the root Scaffold of the app so every screen
 * inherits the effect for themes that want it.
 */
@Composable
fun Modifier.themeOverlay(theme: PrismTheme): Modifier {
    val colors = LocalPrismColors.current
    return when (theme) {
        PrismTheme.CYBERPUNK, PrismTheme.MATRIX -> scanLineOverlay(colors.primary)
        PrismTheme.SYNTHWAVE, PrismTheme.VOID -> this
    }
}

/**
 * Draws a faint repeating horizontal line pattern (1dp lines spaced
 * 4dp apart) at 3% alpha of [lineColor]. The effect is painted behind
 * the receiver's content via [Modifier.drawBehind].
 */
@Composable
private fun Modifier.scanLineOverlay(lineColor: Color): Modifier {
    val density = LocalDensity.current
    val strokePx = with(density) { 1.dp.toPx() }
    val spacingPx = with(density) { 4.dp.toPx() }
    val overlayColor = lineColor.copy(alpha = 0.03f)
    return this.drawBehind {
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = overlayColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokePx
            )
            y += spacingPx
        }
    }
}
