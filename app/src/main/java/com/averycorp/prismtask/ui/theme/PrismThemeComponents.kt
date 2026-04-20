package com.averycorp.prismtask.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

// ─── ThemedSubScreenTitle ──────────────────────────────────────────────────

/**
 * Theme-aware TopAppBar title for all secondary / sub-screens.
 *
 * Rendering per theme:
 * - **Void (editorial)**: `"Title."` — trailing period in [PrismThemeColors.primary],
 *   Fraunces display font at FontWeight.Medium.
 * - **Matrix (terminal)**: title uppercased in mono font — matches the
 *   all-caps command aesthetic used across Matrix screen titles.
 * - **Cyberpunk / Synthwave / default**: title in [PrismThemeFonts.display] at
 *   FontWeight.Bold with [PrismThemeAttrs.displayTracking] letter-spacing.
 *
 * Used by every settings sub-screen (15 files) and any secondary screen
 * that needs per-theme title polish without duplicating branching logic.
 *
 * @param title Human-readable screen name, e.g. "Appearance", "New Task".
 */
@Composable
fun ThemedSubScreenTitle(title: String) {
    val attrs = LocalPrismAttrs.current
    val colors = LocalPrismColors.current
    val displayFont = LocalPrismFonts.current.display
    val monoFont = LocalPrismFonts.current.mono
    when {
        attrs.editorial -> BasicText(
            text = buildAnnotatedString {
                append(title)
                withStyle(SpanStyle(color = colors.primary)) { append(".") }
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = displayFont,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground
            )
        )
        attrs.terminal -> Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = monoFont,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )
        )
        else -> Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = displayFont,
                fontWeight = FontWeight.Bold,
                letterSpacing = attrs.displayTracking.sp,
                color = colors.onBackground
            )
        )
    }
}
