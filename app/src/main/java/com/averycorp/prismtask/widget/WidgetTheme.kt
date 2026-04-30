package com.averycorp.prismtask.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ImageProvider
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.R
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
 * Each widget reads the active widget theme inside `provideGlance`
 * (see [loadWidgetPalette]), builds the palette via [widgetThemePalette],
 * then passes it down to its content composables so a single instance
 * renders coherently in one frame.
 *
 * Atmospherics — Cyberpunk scanlines, Synthwave radial sunset, Matrix
 * phosphor scanlines — ride along on [surfaceBackground] as a layered
 * Android drawable, applied via `background(ImageProvider(...))` on the
 * widget root. Void uses a flat surface drawable so the API is uniform.
 */
data class WidgetThemePalette(
    val theme: PrismTheme,
    // Surfaces
    val background: ColorProvider,
    val surface: ColorProvider,
    val surfaceVariant: ColorProvider,
    val border: ColorProvider,
    val surfaceBackground: ImageProvider,
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
    // Eisenhower quadrants — foreground accent + 10% tinted background
    // matching the `hexA(color, 0.10)` cell fill in the JSX widget mockup.
    val quadrantQ1: ColorProvider,
    val quadrantQ2: ColorProvider,
    val quadrantQ3: ColorProvider,
    val quadrantQ4: ColorProvider,
    val quadrantQ1Bg: ColorProvider,
    val quadrantQ2Bg: ColorProvider,
    val quadrantQ3Bg: ColorProvider,
    val quadrantQ4Bg: ColorProvider,
    // Semantic accents (used by Inbox / Medication / Sparkline widgets)
    val infoColor: ColorProvider,
    val warningColor: ColorProvider,
    val successColor: ColorProvider,
    // Misc
    val onColored: ColorProvider,
    // Shape
    val widgetCornerRadius: Dp
)

/** Default palette returned when the user's selection cannot be loaded. */
private val DEFAULT_PRISM_THEME = PrismTheme.VOID

/** Glance widget surface radius (Android 12+ system widget radius). */
private val WIDGET_RADIUS = 22.dp

private fun atmosphericBackground(theme: PrismTheme): Int = when (theme) {
    PrismTheme.CYBERPUNK -> R.drawable.widget_bg_cyberpunk
    PrismTheme.SYNTHWAVE -> R.drawable.widget_bg_synthwave
    PrismTheme.MATRIX -> R.drawable.widget_bg_matrix
    PrismTheme.VOID -> R.drawable.widget_bg_void
}

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
        surfaceBackground = ImageProvider(atmosphericBackground(theme)),
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
        quadrantQ1 = ColorProvider(c.quadrantQ1),
        quadrantQ2 = ColorProvider(c.quadrantQ2),
        quadrantQ3 = ColorProvider(c.quadrantQ3),
        quadrantQ4 = ColorProvider(c.quadrantQ4),
        quadrantQ1Bg = ColorProvider(c.quadrantQ1.copy(alpha = 0.10f)),
        quadrantQ2Bg = ColorProvider(c.quadrantQ2.copy(alpha = 0.10f)),
        quadrantQ3Bg = ColorProvider(c.quadrantQ3.copy(alpha = 0.10f)),
        quadrantQ4Bg = ColorProvider(c.quadrantQ4.copy(alpha = 0.10f)),
        infoColor = ColorProvider(c.infoColor),
        warningColor = ColorProvider(c.warningColor),
        successColor = ColorProvider(c.successColor),
        onColored = ColorProvider(contrastOn(c.primary)),
        widgetCornerRadius = WIDGET_RADIUS
    )
}

/**
 * Reads the active widget theme and returns the matching [WidgetThemePalette].
 *
 * Resolution order:
 * 1. The widget-specific override stored under
 *    [ThemePreferences.getWidgetThemeOverride] — non-null when the user
 *    has explicitly picked a widget theme distinct from the app theme.
 * 2. The user's app-wide [PrismTheme] from [ThemePreferences.getPrismTheme].
 * 3. [DEFAULT_PRISM_THEME] (`VOID`) on any read failure.
 *
 * The override is per-app (not per-widget) — every Glance widget shares
 * the same theme. Per-instance overrides could be layered on later via
 * [WidgetConfigDataStore] without changing this signature.
 */
suspend fun loadWidgetPalette(context: Context): WidgetThemePalette {
    val prefs = ThemePreferences(context.applicationContext)
    val override = runCatching { prefs.getWidgetThemeOverride().first() }.getOrNull()?.takeIf { it.isNotBlank() }
    val themeName = override ?: runCatching { prefs.getPrismTheme().first() }.getOrNull()
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
