package com.averycorp.prismtask.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── TerminalLabel ────────────────────────────────────────────────────────────

/**
 * Renders secondary [text] with per-theme styling:
 *
 * - **Void (editorial)**: text uppercased with 1.4sp letter-spacing — the
 *   spaced small-caps feel from the print/magazine reference.
 * - **Matrix (terminal)**: text prefixed with `// ` in mono at 60% primary
 *   alpha — inline comment convention.
 * - **All other themes**: plain [Text] using [style] and [color] unchanged.
 *
 * Targets: Today stat lines, header date labels, task row dates, habit stats,
 * settings row subtitles — ~15 callsites across 5 screen files.
 */
@Composable
fun TerminalLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val attrs = LocalPrismAttrs.current
    if (attrs.editorial) {
        Text(
            text = text.uppercase(),
            modifier = modifier,
            style = style.copy(letterSpacing = 1.4.sp),
            color = color
        )
        return
    }
    if (!attrs.terminal) {
        Text(text = text, modifier = modifier, style = style, color = color)
        return
    }
    val colors = LocalPrismColors.current
    val monoFont = LocalPrismFonts.current.mono
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "//",
            style = style,
            fontFamily = monoFont,
            color = colors.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = style,
            fontFamily = monoFont,
            color = colors.muted
        )
    }
}

// ─── TerminalSectionHeader ────────────────────────────────────────────────────

/**
 * Renders `# title` in [PrismThemeColors.primary] using the active mono font
 * when [PrismThemeAttrs.terminal] is true. The title is lowercased to match
 * the command-label convention of the Matrix theme.
 *
 * Called by [com.averycorp.prismtask.ui.components.settings.SectionHeader] and
 * [com.averycorp.prismtask.ui.screens.today.components.SectionHeaderRow] when
 * the Matrix theme is active; callers supply the padding modifier.
 */
@Composable
fun TerminalSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPrismColors.current
    val monoFont = LocalPrismFonts.current.mono
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "# ",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = monoFont,
            color = colors.primary
        )
        Text(
            text = title.lowercase(),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = monoFont,
            color = colors.primary
        )
    }
}

// ─── TerminalPrompt ───────────────────────────────────────────────────────────

/**
 * Renders a `$` terminal-prompt character in [PrismThemeColors.primary] at
 * 60% alpha. Place inside a [Row] alongside the input field when
 * [PrismThemeAttrs.terminal] is active (Matrix Quick-Add bar).
 */
@Composable
fun TerminalPrompt(modifier: Modifier = Modifier) {
    val colors = LocalPrismColors.current
    val monoFont = LocalPrismFonts.current.mono
    Text(
        text = "$",
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = monoFont,
        color = colors.primary.copy(alpha = 0.6f)
    )
}

// ─── TerminalCursor ───────────────────────────────────────────────────────────

/**
 * Blinking underscore cursor: 500 ms on / 500 ms off, driven by a
 * [keyframes] [infiniteRepeatable] with no continuous interpolation —
 * battery impact equivalent to a single 1 Hz clock tick (matches the JS
 * `animation: 'blink 1s steps(2) infinite'` spec exactly).
 *
 * No-op when [PrismThemeAttrs.terminal] is false; safe to call unconditionally
 * from the Matrix-specific quick-add bar layout.
 */
@Composable
fun TerminalCursor(modifier: Modifier = Modifier) {
    val attrs = LocalPrismAttrs.current
    if (!attrs.terminal) return
    val colors = LocalPrismColors.current
    val monoFont = LocalPrismFonts.current.mono
    val transition = rememberInfiniteTransition(label = "terminalBlink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "cursorAlpha"
    )
    Text(
        text = "_",
        modifier = modifier.alpha(alpha),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = monoFont,
        color = colors.primary,
        fontSize = 14.sp
    )
}

// ─── terminalCount ────────────────────────────────────────────────────────────

/**
 * Returns the count wrapped in square brackets (e.g. "[3]") when [terminal]
 * is true, or the plain `count.toString()` otherwise. Use for badge counts,
 * task/habit counts, and any numeric label that should adopt the Matrix
 * bracket-notation style.
 */
fun terminalCount(count: Int, terminal: Boolean): String =
    if (terminal) "[$count]" else count.toString()
