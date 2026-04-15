package com.averycorp.prismtask.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun parseColorOrNull(hex: String): Color? {
    if (hex.isBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PrismTaskTheme(
    prismTheme: PrismTheme = PrismTheme.VOID,
    themeMode: String = "system",
    accentColor: String = "#2563EB",
    backgroundColorOverride: String = "",
    surfaceColorOverride: String = "",
    errorColorOverride: String = "",
    fontScale: Float = 1.0f,
    priorityColors: PriorityColors = PriorityColors(),
    reduceMotion: Boolean = false,
    highContrast: Boolean = false,
    largeTouchTargets: Boolean = false,
    compactMode: Boolean = false,
    cardCornerRadius: Int = 12,
    showCardBorders: Boolean = true,
    content: @Composable () -> Unit
) {
    // PrismThemes are always dark-surface palettes, so we always start from
    // Material's darkColorScheme() and layer the palette on top. themeMode is
    // retained for backwards compatibility with legacy callers but no longer
    // toggles between light/dark color schemes — the PrismTheme defines the
    // canvas.
    @Suppress("UNUSED_VARIABLE")
    val legacyThemeMode = themeMode

    val prismColors = prismThemeColors(prismTheme)

    val accent = try {
        Color(android.graphics.Color.parseColor(accentColor))
    } catch (_: Exception) {
        prismColors.primary
    }

    val baseScheme = darkColorScheme()
    val colorScheme = baseScheme.copy(
        // Map PrismThemeColors onto the Material ColorScheme so all existing
        // MaterialTheme.colorScheme.* lookups automatically reflect the
        // active PrismTheme.
        background = parseColorOrNull(backgroundColorOverride) ?: prismColors.background,
        surface = parseColorOrNull(surfaceColorOverride) ?: prismColors.surface,
        surfaceVariant = prismColors.surfaceVariant,
        primary = accent,
        primaryContainer = accent.copy(alpha = 0.25f),
        secondary = prismColors.secondary,
        onBackground = prismColors.onBackground,
        onSurface = prismColors.onBackground,
        onSurfaceVariant = prismColors.onSurface,
        outline = prismColors.border,
        error = parseColorOrNull(errorColorOverride) ?: baseScheme.error
    )

    val scaledTypography = remember(fontScale) {
        scaledTypography(Typography, fontScale)
    }

    // High-contrast mode: boost text contrast on the dark PrismTheme canvas
    // by pinning onSurface/onBackground to fully opaque white and tightening
    // the outline. Accent colors continue to read correctly.
    val effectiveScheme = if (highContrast) {
        colorScheme.copy(
            onSurface = Color.White,
            onBackground = Color.White,
            outline = Color(0xFFCCCCCC)
        )
    } else {
        colorScheme
    }

    CompositionLocalProvider(
        LocalPriorityColors provides priorityColors,
        LocalPrismTheme provides prismTheme,
        LocalPrismColors provides prismColors,
        LocalPrismFonts provides prismThemeFonts(prismTheme),
        com.averycorp.prismtask.ui.a11y.LocalReducedMotion provides reduceMotion,
        com.averycorp.prismtask.ui.a11y.LocalHighContrast provides highContrast,
        com.averycorp.prismtask.ui.a11y.LocalLargeTouchTargets provides largeTouchTargets,
        LocalCompactMode provides compactMode,
        LocalCardCornerRadius provides cardCornerRadius.coerceIn(0, 24).dp,
        LocalShowCardBorders provides showCardBorders
    ) {
        MaterialTheme(
            colorScheme = effectiveScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
