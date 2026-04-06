package com.averykarlin.averytask.ui.theme

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
fun AveryTaskTheme(
    themeMode: String = "system",
    accentColor: String = "#2563EB",
    backgroundColorOverride: String = "",
    surfaceColorOverride: String = "",
    errorColorOverride: String = "",
    fontScale: Float = 1.0f,
    priorityColors: PriorityColors = PriorityColors(),
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

    CompositionLocalProvider(LocalPriorityColors provides priorityColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
