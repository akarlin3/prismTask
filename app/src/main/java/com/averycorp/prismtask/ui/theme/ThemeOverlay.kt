package com.averycorp.prismtask.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.tan

/**
 * Apply a per-theme decorative backdrop to the receiver [Modifier].
 *
 * - CYBERPUNK: horizontal CRT scanlines (7% alpha primary) — faint but visible.
 * - MATRIX:    vertical "digital rain" columns (10% alpha primary) + horizontal
 *              scanlines (5% alpha) layered together for the terminal aesthetic.
 * - SYNTHWAVE: radial gradient "sunset" glow from the upper-centre, plus a
 *              converging perspective-grid floor drawn at the bottom quarter of
 *              the composable to evoke the classic 80s grid horizon.
 * - VOID:      intentional no-op — the editorial minimal aesthetic stays clean.
 *
 * Attach to the root Box in MainActivity so every screen inherits the effect.
 */
@Composable
fun Modifier.themeOverlay(theme: PrismTheme): Modifier {
    val colors = LocalPrismColors.current
    return when (theme) {
        PrismTheme.CYBERPUNK -> scanLineOverlay(colors.primary, alpha = 0.07f, spacingDp = 4)
        PrismTheme.MATRIX    -> matrixRainOverlay(colors.primary)
        PrismTheme.SYNTHWAVE -> synthwaveBackdrop(colors.primary, colors.secondary)
        PrismTheme.VOID      -> this
    }
}

// ─── Cyberpunk / Matrix scanlines ─────────────────────────────────────────

@Composable
private fun Modifier.scanLineOverlay(
    lineColor: Color,
    alpha: Float = 0.07f,
    spacingDp: Int = 4
): Modifier {
    val density = LocalDensity.current
    val strokePx = with(density) { 1.dp.toPx() }
    val spacingPx = with(density) { spacingDp.dp.toPx() }
    val color = lineColor.copy(alpha = alpha)
    return this.drawBehind {
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokePx)
            y += spacingPx
        }
    }
}

// ─── Matrix: vertical rain columns + faint scanlines ──────────────────────

@Composable
private fun Modifier.matrixRainOverlay(primaryColor: Color): Modifier {
    val density = LocalDensity.current
    val colWidthPx = with(density) { 14.dp.toPx() }
    val scanStrokePx = with(density) { 1.dp.toPx() }
    val scanSpacingPx = with(density) { 4.dp.toPx() }
    val rainColor = primaryColor.copy(alpha = 0.08f)
    val scanColor = primaryColor.copy(alpha = 0.04f)
    return this.drawBehind {
        // Vertical rain columns
        var x = 0f
        while (x < size.width) {
            drawLine(rainColor, Offset(x, 0f), Offset(x, size.height), 1f)
            x += colWidthPx
        }
        // Horizontal scanlines
        var y = 0f
        while (y < size.height) {
            drawLine(scanColor, Offset(0f, y), Offset(size.width, y), scanStrokePx)
            y += scanSpacingPx
        }
    }
}

// ─── Synthwave: radial sunset glow + converging perspective grid ───────────

@Composable
private fun Modifier.synthwaveBackdrop(primaryColor: Color, secondaryColor: Color): Modifier {
    val density = LocalDensity.current
    val gridColor = primaryColor.copy(alpha = 0.22f)
    val gridColorFaint = primaryColor.copy(alpha = 0.06f)
    return this.drawBehind {
        val w = size.width
        val h = size.height

        // Radial sunset glow centered at 35% down from top
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.12f),
                    secondaryColor.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                center = Offset(w / 2f, h * 0.35f),
                radius = w * 0.7f
            ),
            center = Offset(w / 2f, h * 0.35f),
            radius = w * 0.7f
        )

        // Perspective grid floor — bottom 28% of the screen
        val floorTop = h * 0.72f
        val vanishX = w / 2f
        val vanishY = floorTop

        // Horizontal lines spaced in an accelerating pattern (simulates depth)
        val lineCount = 8
        for (i in 0..lineCount) {
            val t = i.toFloat() / lineCount
            val curveT = t * t   // ease-in: lines bunch up near horizon
            val y = vanishY + curveT * (h - vanishY)
            val alpha = 0.05f + t * 0.17f
            drawLine(
                color = primaryColor.copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Vertical lines converging to vanishing point
        val columnCount = 12
        for (i in 0..columnCount) {
            val xBase = (i.toFloat() / columnCount) * w
            val alpha = if (i == 0 || i == columnCount) 0.06f else gridColorFaint.alpha
            drawLine(
                color = primaryColor.copy(alpha = alpha),
                start = Offset(vanishX + (xBase - vanishX) * 0.05f, vanishY),
                end = Offset(xBase, h),
                strokeWidth = 1f
            )
        }
    }
}
