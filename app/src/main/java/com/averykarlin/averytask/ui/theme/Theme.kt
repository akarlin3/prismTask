package com.averykarlin.averytask.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AveryTaskTheme(
    themeMode: String = "system",
    accentColor: String = "#2563EB",
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
        primaryContainer = if (useDark) accent.copy(alpha = 0.25f) else accent.copy(alpha = 0.12f)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
