package com.averycorp.prismtask.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

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
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val baseScheme = if (useDark) DarkColors else LightColors

    val accent = try {
        Color(android.graphics.Color.parseColor(accentColor))
    } catch (_: Exception) {
        baseScheme.primary
    }

    val colorScheme = baseScheme.copy(
        primary = accent,
        primaryContainer = if (useDark) accent.copy(alpha = 0.25f) else accent.copy(alpha = 0.12f),
        background = parseColorOrNull(backgroundColorOverride) ?: baseScheme.background,
        surface = parseColorOrNull(surfaceColorOverride) ?: baseScheme.surface,
        error = parseColorOrNull(errorColorOverride) ?: baseScheme.error
    )

    val scaledTypography = remember(fontScale) {
        scaledTypography(Typography, fontScale)
    }

    // High-contrast mode: darken surfaces and boost text contrast by pinning
    // onSurface/onBackground to fully opaque tones and tightening the error
    // color. This is intentionally conservative so the existing accent color
    // continues to read correctly.
    val effectiveScheme = if (highContrast) {
        colorScheme.copy(
            onSurface = if (useDark) Color.White else Color.Black,
            onBackground = if (useDark) Color.White else Color.Black,
            outline = if (useDark) Color(0xFFCCCCCC) else Color(0xFF333333)
        )
    } else colorScheme

    CompositionLocalProvider(
        LocalPriorityColors provides priorityColors,
        com.averycorp.prismtask.ui.a11y.LocalReducedMotion provides reduceMotion,
        com.averycorp.prismtask.ui.a11y.LocalHighContrast provides highContrast,
        com.averycorp.prismtask.ui.a11y.LocalLargeTouchTargets provides largeTouchTargets
    ) {
        MaterialTheme(
            colorScheme = effectiveScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
