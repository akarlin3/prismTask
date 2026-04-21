package com.averycorp.prismtask.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
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
 * Returns a [Modifier] that paints a two-layer colored glow behind the composable,
 * matching the JS `glowShadow()` formula exactly:
 *
 * - [GlowLevel.NONE]   → no-op
 * - [GlowLevel.SOFT]   → single layer: blur 10dp, α 0.38
 * - [GlowLevel.STRONG] → inner (blur 14dp, α 0.60) + outer (blur 28dp, α 0.20)
 * - [GlowLevel.HEAVY]  → inner (blur 20dp, α 0.50) + outer (blur 40dp, α 0.25)
 *
 * Both passes share one [drawBehind] block. [intensity] scales both blur radii
 * and alpha values proportionally (default 1.0).
 */
fun Modifier.prismGlow(color: Color, level: GlowLevel, intensity: Float = 1f): Modifier {
    if (level == GlowLevel.NONE) return this
    return this.drawBehind {
        when (level) {
            GlowLevel.SOFT -> {
                drawGlowCircle(color, (10.dp * intensity).toPx(), 0.38f * intensity)
            }
            GlowLevel.STRONG -> {
                drawGlowCircle(color, (14.dp * intensity).toPx(), 0.60f * intensity)
                drawGlowCircle(color, (28.dp * intensity).toPx(), 0.20f * intensity)
            }
            GlowLevel.HEAVY -> {
                drawGlowCircle(color, (20.dp * intensity).toPx(), 0.50f * intensity)
                drawGlowCircle(color, (40.dp * intensity).toPx(), 0.25f * intensity)
            }
            GlowLevel.NONE -> Unit
        }
    }
}

private fun DrawScope.drawGlowCircle(color: Color, blurPx: Float, alpha: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        paint.color = color.copy(alpha = alpha.coerceIn(0f, 1f))
        canvas.drawCircle(
            center = Offset(size.width / 2f, size.height / 2f),
            radius = minOf(size.width, size.height) / 2f,
            paint = paint
        )
    }
}

// ─── Scanlines (Cyberpunk / Matrix CRT overlay) ───────────────────────────

/**
 * Overlays horizontal CRT scanlines on top of the composable, matching the
 * JS `repeating-linear-gradient` spec:
 *
 *   per-line alpha = (16/255) × [outerAlpha]  (replicates CSS `${primary}10`)
 *   blend mode     = [BlendMode.Screen]         (additive highlight)
 *
 * Cyberpunk: [spacingDp]=3dp, [outerAlpha]=0.55 → effective α ≈ 0.035
 * Matrix: [spacingDp]=2dp, [outerAlpha]=0.70 → effective α ≈ 0.044
 *
 * Only the [PrismTaskTheme] root calls this; individual composables should
 * never apply it directly.
 */
fun Modifier.scanlines(color: Color, spacingDp: Dp, outerAlpha: Float): Modifier {
    val lineAlpha = (16f / 255f) * outerAlpha
    return this.drawWithContent {
        drawContent()
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = color.copy(alpha = lineAlpha.coerceIn(0f, 1f))
                blendMode = BlendMode.Screen
            }
            val spacingPx = spacingDp.toPx()
            var y = 0f
            while (y <= size.height) {
                canvas.drawLine(Offset(0f, y), Offset(size.width, y), paint)
                y += spacingPx
            }
        }
    }
}

// ─── Corner brackets (Cyberpunk HUD) ──────────────────────────────────────

/**
 * Draws four Cyberpunk-style corner brackets (⌜⌝⌞⌟) in front of the receiver.
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
    return this.drawWithContent {
        drawContent()
        val arm = sizeDP.toPx()
        val sw = stroke.toPx()
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

// ─── PrismBracket composable (Cyberpunk section headers) ──────────────────

/**
 * Wraps [content] with a `[` … `]` bracket pair in [PrismThemeColors.primary]
 * and the mono font. The brackets are only rendered when [PrismThemeAttrs.brackets]
 * is true (Cyberpunk theme); all other themes render a plain [Row] no-op so
 * callers don't need to branch.
 *
 * Apply sparingly — only at major screen-level section headers (Today sections,
 * Settings section headers). Do NOT apply to every sub-heading or card title.
 */
@Composable
fun PrismBracket(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val attrs = LocalPrismAttrs.current
    if (!attrs.brackets) {
        Row(modifier = modifier, content = content)
        return
    }
    val prismColors = LocalPrismColors.current
    val monoFont = LocalPrismFonts.current.mono
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "[", color = prismColors.primary, fontFamily = monoFont)
        content()
        Text(text = "]", color = prismColors.primary, fontFamily = monoFont)
    }
}

// ─── PrismHudDivider (Cyberpunk dashed separator) ─────────────────────────

/**
 * Themed horizontal divider. Cyberpunk renders a dashed 1dp line in
 * [PrismThemeColors.primary] at 60% alpha, matching the JS `border: 1px dashed
 * ${primary}60` spec. All other themes fall back to a standard
 * [HorizontalDivider] using [MaterialTheme.colorScheme.outlineVariant].
 */
@Composable
fun PrismHudDivider(modifier: Modifier = Modifier) {
    val attrs = LocalPrismAttrs.current
    val prismColors = LocalPrismColors.current
    when {
        attrs.hudDividers -> {
            // Cyberpunk: dashed primary line at 60% alpha
            val lineColor = prismColors.primary.copy(alpha = 0.6f)
            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .height(1.dp)
            ) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                        0f
                    )
                )
            }
        }
        attrs.sunset -> {
            // Synthwave: solid primary line at 18% alpha
            HorizontalDivider(
                modifier = modifier,
                color = prismColors.primary.copy(alpha = 0.18f)
            )
        }
        else -> {
            HorizontalDivider(
                modifier = modifier,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

// ─── Synthwave perspective grid floor ─────────────────────────────────────

/**
 * Draws a Synthwave-style perspective grid floor as a background decoration.
 * Active only when [PrismThemeAttrs.gridFloor] is true (Synthwave theme).
 *
 * Visual spec (matches JS `perspective(380px) rotateX(62°)` CSS transform):
 * - 260dp grid region, bottom-anchored; 20dp world-space line spacing.
 * - Horizontal lines projected via `screenY = H − worldY·cosT·p/(p + worldY·sinT)`.
 * - Vertical lines converge from equal x-steps at the bottom to the vanishing
 *   point at `(width/2, H − p·cosT/sinT)`.
 * - Sun glow: blurred radial gradient (primary→secondary→transparent) at the horizon.
 * - Gradient fade mask (transparent→opaque bottom-up) via DstIn saveLayer.
 * - Line color: `primary.copy(alpha = 0.21f)`, 1dp stroke.
 */
fun Modifier.gridFloor(): Modifier = composed {
    val attrs = LocalPrismAttrs.current
    if (!attrs.gridFloor) return@composed Modifier
    val primary = LocalPrismColors.current.primary
    val secondary = LocalPrismColors.current.secondary
    drawBehind {
        val perspPx = 380.dp.toPx()
        val tRad = 62f * (PI.toFloat() / 180f)
        val cosT = cos(tRad)
        val sinT = sin(tRad)
        val gridHeightPx = 260.dp.toPx()
        if (size.height < gridHeightPx) return@drawBehind
        val worldSpacingPx = 20.dp.toPx()
        val vanishingX = size.width / 2f
        val vanishingY = size.height - perspPx * cosT / sinT
        val gridTopY = size.height - gridHeightPx
        val gridColor = primary.copy(alpha = 0.21f)
        val strokePx = 1.dp.toPx()

        drawIntoCanvas { canvas ->
            val layerBounds = Rect(0f, gridTopY, size.width, size.height)
            canvas.saveLayer(layerBounds, Paint())

            // Sun glow at the horizon (top of grid region)
            val sunPaint = Paint().apply {
                asFrameworkPaint().maskFilter =
                    BlurMaskFilter(18.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                shader = RadialGradientShader(
                    center = Offset(size.width / 2f, gridTopY),
                    radius = 190.dp.toPx(),
                    colors = listOf(
                        primary.copy(alpha = 0.25f),
                        secondary.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    colorStops = listOf(0f, 0.5f, 1f),
                    tileMode = TileMode.Clamp
                )
            }
            canvas.drawRect(layerBounds, sunPaint)

            // Grid line paint
            val gridPaint = Paint().apply {
                color = gridColor
                strokeWidth = strokePx
                isAntiAlias = true
            }

            // Horizontal lines (perspective projected from world-space rows)
            var worldY = 0f
            while (worldY <= gridHeightPx) {
                val screenY = size.height - worldY * cosT * perspPx / (perspPx + worldY * sinT)
                if (screenY < vanishingY) break
                canvas.drawLine(Offset(0f, screenY), Offset(size.width, screenY), gridPaint)
                worldY += worldSpacingPx
            }

            // Vertical lines: equal x-spacing at bottom, converge to vanishing point
            var xBottom = 0f
            while (xBottom <= size.width) {
                canvas.drawLine(
                    Offset(xBottom, size.height),
                    Offset(vanishingX, vanishingY),
                    gridPaint
                )
                xBottom += worldSpacingPx
            }

            // Gradient fade: transparent at grid top → opaque at grid bottom (DstIn)
            val maskPaint = Paint().apply {
                blendMode = BlendMode.DstIn
                shader = LinearGradientShader(
                    from = Offset(0f, gridTopY),
                    to = Offset(0f, size.height),
                    colors = listOf(Color.Transparent, Color.Black),
                    tileMode = TileMode.Clamp
                )
            }
            canvas.drawRect(layerBounds, maskPaint)

            canvas.restore()
        }
    }
}

// ─── Sunset card gradient (Synthwave) ─────────────────────────────────────

/**
 * Paints a diagonal sunset gradient (surface → surfaceVariant, ~135°) as the
 * background of the receiver. Active only when [PrismThemeAttrs.sunset] is true.
 *
 * Use this on any composable where you want the Synthwave card wash. Callers
 * that use it on a Material3 [Card] must pair it with
 * `containerColor = Color.Transparent` so the Card surface does not occlude
 * the gradient.
 */
fun Modifier.sunsetBackground(): Modifier = composed {
    val attrs = LocalPrismAttrs.current
    if (!attrs.sunset) return@composed Modifier
    val surface = LocalPrismColors.current.surface
    val surfaceVariant = LocalPrismColors.current.surfaceVariant
    this.then(
        Modifier.drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(surface, surfaceVariant),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
        }
    )
}

/**
 * Convenience alias for [sunsetBackground] intended for use inside Card
 * `modifier` chains. Returns the sunset gradient modifier when Synthwave is
 * active; returns [Modifier] unchanged otherwise. Callers should set
 * `containerColor = if (attrs.sunset) Color.Transparent else colors.surface`
 * so the Card surface does not hide the gradient.
 */
fun Modifier.prismCardBackground(): Modifier = sunsetBackground()

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
    } else {
        null
    }

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
