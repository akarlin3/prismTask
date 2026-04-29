package com.averycorp.prismtask.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.ui.theme.PrismTheme
import com.averycorp.prismtask.ui.theme.PrismThemeColors
import com.averycorp.prismtask.ui.theme.prismThemeColors
import kotlinx.coroutines.flow.first

/**
 * Glance-side mirror of the in-app [PrismTheme] palettes.
 *
 * Widgets render outside the app process and cannot read
 * `LocalPrismColors`, so we project [PrismThemeColors] into the token
 * shape the widget composables actually consume — score-band colors,
 * priority stripes, streak accents, etc. — and expose every entry as a
 * Glance [ColorProvider].
 *
 * Each widget reads the user's selected [PrismTheme] from
 * [ThemePreferences] inside `provideGlance`, builds the palette via
 * [widgetThemePalette], then passes it down to its content composables
 * so a single instance renders coherently in one frame.
 *
 * Visual atmospherics (Cyberpunk scanlines, Synthwave sunset, Matrix
 * rain, Void hairlines) come from the in-app theme overlays and don't
 * have direct Glance analogues — the palette captures color intent only.
 */
data class WidgetThemePalette(
    val theme: PrismTheme,
    // Surfaces
    val background: ColorProvider,
    val surface: ColorProvider,
    val surfaceVariant: ColorProvider,
    val border: ColorProvider,
    // Text
    val onBackground: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    // Accents
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val secondary: ColorProvider,
    val primaryContainer: ColorProvider,
    val onPrimaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val onSecondaryContainer: ColorProvider,
    // Errors / overdue
    val error: ColorProvider,
    val overdue: ColorProvider,
    val overdueBg: ColorProvider,
    // Priority stripe (urgent → none)
    val priorityUrgent: ColorProvider,
    val priorityHigh: ColorProvider,
    val priorityMedium: ColorProvider,
    val priorityLow: ColorProvider,
    val priorityNone: ColorProvider,
    // Productivity score bands
    val scoreGreen: ColorProvider,
    val scoreGreenBg: ColorProvider,
    val scoreOrange: ColorProvider,
    val scoreOrangeBg: ColorProvider,
    val scoreRed: ColorProvider,
    val scoreRedBg: ColorProvider,
    // Habit / streak
    val habitComplete: ColorProvider,
    val habitCompleteBg: ColorProvider,
    val habitIncomplete: ColorProvider,
    val streakFire: ColorProvider,
    val streakGold: ColorProvider,
    // Timer
    val timerWork: ColorProvider,
    val timerBreak: ColorProvider,
    val timerStop: ColorProvider,
    // Calendar
    val calendarEvent: ColorProvider,
    // Misc
    val onColored: ColorProvider,
    // Shape
    val widgetCornerRadius: Dp
)

/** Default palette returned when the user's selection cannot be loaded. */
private val DEFAULT_PRISM_THEME = PrismTheme.VOID

/** Glance widget surface radius (Android 12+ system widget radius). */
private val WIDGET_RADIUS = 22.dp

/**
 * Build a [WidgetThemePalette] from the [PrismTheme] palette tokens.
 *
 * Container tints (primaryContainer / habitCompleteBg / overdueBg) are
 * synthesized as alpha-mixed primaries — Glance widget surfaces are
 * always opaque so static tints read consistently across launchers.
 */
fun widgetThemePalette(theme: PrismTheme): WidgetThemePalette {
    val c = prismThemeColors(theme)
    return WidgetThemePalette(
        theme = theme,
        background = ColorProvider(c.background),
        surface = ColorProvider(c.surface),
        surfaceVariant = ColorProvider(c.surfaceVariant),
        border = ColorProvider(c.border),
        onBackground = ColorProvider(c.onBackground),
        onSurface = ColorProvider(c.onBackground),
        onSurfaceVariant = ColorProvider(c.onSurface),
        primary = ColorProvider(c.primary),
        onPrimary = ColorProvider(contrastOn(c.primary)),
        secondary = ColorProvider(c.secondary),
        primaryContainer = ColorProvider(c.primary.copy(alpha = 0.20f)),
        onPrimaryContainer = ColorProvider(c.primary),
        secondaryContainer = ColorProvider(c.secondary.copy(alpha = 0.18f)),
        onSecondaryContainer = ColorProvider(c.secondary),
        error = ColorProvider(c.destructiveColor),
        overdue = ColorProvider(c.destructiveColor),
        overdueBg = ColorProvider(c.destructiveColor.copy(alpha = 0.16f)),
        priorityUrgent = ColorProvider(c.destructiveColor),
        priorityHigh = ColorProvider(c.warningColor),
        priorityMedium = ColorProvider(c.primary),
        priorityLow = ColorProvider(c.muted),
        priorityNone = ColorProvider(c.muted),
        scoreGreen = ColorProvider(c.successColor),
        scoreGreenBg = ColorProvider(c.successColor.copy(alpha = 0.18f)),
        scoreOrange = ColorProvider(c.warningColor),
        scoreOrangeBg = ColorProvider(c.warningColor.copy(alpha = 0.18f)),
        scoreRed = ColorProvider(c.destructiveColor),
        scoreRedBg = ColorProvider(c.destructiveColor.copy(alpha = 0.18f)),
        habitComplete = ColorProvider(c.successColor),
        habitCompleteBg = ColorProvider(c.successColor.copy(alpha = 0.20f)),
        habitIncomplete = ColorProvider(c.muted.copy(alpha = 0.55f)),
        streakFire = ColorProvider(c.warningColor),
        streakGold = ColorProvider(c.warningColor),
        timerWork = ColorProvider(c.primary),
        timerBreak = ColorProvider(c.successColor),
        timerStop = ColorProvider(c.destructiveColor),
        calendarEvent = ColorProvider(c.infoColor),
        onColored = ColorProvider(contrastOn(c.primary)),
        widgetCornerRadius = WIDGET_RADIUS
    )
}

/**
 * Reads the user's selected [PrismTheme] from [ThemePreferences] and
 * returns the matching [WidgetThemePalette]. Falls back to [DEFAULT_PRISM_THEME]
 * when the preference is unset or cannot be parsed (e.g., during the
 * initial widget placement before app launch).
 */
suspend fun loadWidgetPalette(context: Context): WidgetThemePalette {
    val themeName = runCatching {
        ThemePreferences(context.applicationContext).getPrismTheme().first()
    }.getOrNull()
    val theme = runCatching { PrismTheme.valueOf(themeName ?: "") }.getOrDefault(DEFAULT_PRISM_THEME)
    return widgetThemePalette(theme)
}

private fun contrastOn(color: Color): Color {
    val lum = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (lum > 0.55f) Color(0xFF0A0A0F) else Color.White
}

/**
 * Maps a task priority (1..4) to its themed stripe color. Centralised so
 * widgets and tests share one mapping.
 */
fun priorityColorFor(priority: Int, palette: WidgetThemePalette): ColorProvider = when (priority) {
    4 -> palette.priorityUrgent
    3 -> palette.priorityHigh
    2 -> palette.priorityMedium
    1 -> palette.priorityLow
    else -> palette.priorityNone
}
