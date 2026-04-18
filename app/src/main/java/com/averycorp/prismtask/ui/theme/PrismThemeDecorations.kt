package com.averycorp.prismtask.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─── Glow shadow helpers ───────────────────────────────────────────────────

/**
 * Returns a [Modifier] that paints a soft colored glow behind the composable.
 * Internally uses [BlurMaskFilter] on an offscreen [Canvas]. The effect is
 * skipped when [attrs.glow] is [GlowLevel.NONE] so Void layouts stay clean.
 */
fun Modifier.prismGlow(color: Color, attrs: PrismThemeAttrs, intensity: Float = 1f): Modifier {
    if (attrs.glow == GlowLevel.NONE) return this
    val radius = when (attrs.glow) {
        GlowLevel.HEAVY -> 20.dp
        GlowLevel.STRONG -> 14.dp
        GlowLevel.SOFT -> 10.dp
        GlowLevel.NONE -> 0.dp
    }
    val blurRadius = radius * intensity
    return this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                asFrameworkPaint().maskFilter =
                    BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            paint.color = color.copy(alpha = 0.55f * intensity)
            canvas.drawCircle(
                center = Offset(size.width / 2f, size.height / 2f),
                radius = minOf(size.width, size.height) / 2f,
                paint = paint
            )
        }
    }
}

// ─── Corner brackets (Cyberpunk HUD) ──────────────────────────────────────

/**
 * Draws four Cyberpunk-style corner brackets (⌜⌝⌞⌟) behind the receiver.
 * Only active when [attrs.brackets] is true; a no-op for all other themes.
 *
 * @param color   Bracket stroke color — typically [PrismThemeColors.primary].
 * @param sizeDP  Length of each bracket arm in dp.
 * @param stroke  Bracket stroke width in dp.
 */
fun Modifier.cornerBrackets(
    color: Color,
    attrs: PrismThemeAttrs,
    sizeDP: Dp = 12.dp,
    stroke: Dp = 1.5.dp
): Modifier {
    if (!attrs.brackets) return this
    return this.drawBehind {
        val arm = sizeDP.toPx()
        val sw = stroke.toPx()
        val paint = Stroke(width = sw, cap = StrokeCap.Square)
        val bracketColor = color.copy(alpha = 0.8f)

        // Top-left
        drawLine(bracketColor, Offset(0f, arm), Offset(0f, 0f), sw)
        drawLine(bracketColor, Offset(0f, 0f), Offset(arm, 0f), sw)
        // Top-right
        drawLine(bracketColor, Offset(size.width - arm, 0f), Offset(size.width, 0f), sw)
        drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width, arm), sw)
        // Bottom-left
        drawLine(bracketColor, Offset(0f, size.height - arm), Offset(0f, size.height), sw)
        drawLine(bracketColor, Offset(0f, size.height), Offset(arm, size.height), sw)
        // Bottom-right
        drawLine(bracketColor, Offset(size.width - arm, size.height), Offset(size.width, size.height), sw)
        drawLine(bracketColor, Offset(size.width, size.height - arm), Offset(size.width, size.height), sw)
    }
}

// ─── Timer / progress ring helpers ────────────────────────────────────────

/**
 * Draws a themed progress arc inside a [Canvas] scope. Handles:
 * - Cyberpunk: square stroke caps + outer tick marks around the ring.
 * - Synthwave: gradient stroke from [primaryColor] → [secondaryColor] + glow filter.
 * - Matrix: dashed track stroke + square caps.
 * - Void: round caps + no glow.
 *
 * @param progress    0.0–1.0 fill fraction.
 * @param size        Diameter of the ring in pixels.
 * @param strokeWidth Ring stroke width in pixels.
 * @param primaryColor   Accent color (filled arc).
 * @param secondaryColor Secondary accent (Synthwave gradient end).
 * @param trackColor     Background track color.
 * @param attrs          Active [PrismThemeAttrs].
 */
fun DrawScope.drawThemedProgressRing(
    progress: Float,
    size: Float,
    strokeWidth: Float,
    primaryColor: Color,
    secondaryColor: Color,
    trackColor: Color,
    attrs: PrismThemeAttrs
) {
    val radius = size / 2f - strokeWidth / 2f
    val center = Offset(size / 2f, size / 2f)
    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
    val arcSize = androidx.compose.ui.geometry.Size(size - strokeWidth, size - strokeWidth)
    val sweepAngle = progress.coerceIn(0f, 1f) * 360f

    val cap = if (attrs.terminal || attrs.brackets) StrokeCap.Square else StrokeCap.Round

    // Track ring
    val trackPathEffect = if (attrs.terminal) {
        PathEffect.dashPathEffect(floatArrayOf(3f.dp.toPx(), 4f.dp.toPx()), 0f)
    } else null

    drawArc(
        color = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Square,
            pathEffect = trackPathEffect
        )
    )

    // Progress arc — Synthwave uses a gradient brush
    if (attrs.sunset && progress > 0f) {
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(primaryColor, secondaryColor, primaryColor),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = cap)
        )
    } else if (progress > 0f) {
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = cap)
        )
    }

    // Cyberpunk tick marks around the ring
    if (attrs.brackets) {
        val tickCount = 12
        val outerR = radius + strokeWidth / 2f + 6f
        val innerR = outerR + 4f
        for (i in 0 until tickCount) {
            val angle = (i.toFloat() / tickCount) * 2f * PI.toFloat()
            val x1 = center.x + cos(angle) * outerR
            val y1 = center.y + sin(angle) * outerR
            val x2 = center.x + cos(angle) * innerR
            val y2 = center.y + sin(angle) * innerR
            drawLine(
                color = primaryColor.copy(alpha = 0.4f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/**
 * Full-ring Cyberpunk tick-mark overlay for the larger timer dial. Draws 60
 * minute marks (12 major, 48 minor) outside the progress ring radius.
 */
fun DrawScope.drawCyberpunkTimerTicks(
    ringRadius: Float,
    strokeWidth: Float,
    primaryColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerBase = ringRadius + strokeWidth / 2f + 3f
    for (i in 0 until 60) {
        val angle = (i.toFloat() / 60f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val isMajor = i % 5 == 0
        val r1 = outerBase
        val r2 = outerBase + if (isMajor) 10f else 5f
        drawLine(
            color = primaryColor.copy(alpha = if (isMajor) 0.7f else 0.25f),
            start = Offset(center.x + cos(angle) * r1, center.y + sin(angle) * r1),
            end = Offset(center.x + cos(angle) * r2, center.y + sin(angle) * r2),
            strokeWidth = if (isMajor) 1.3.dp.toPx() else 0.8.dp.toPx()
        )
    }
}
