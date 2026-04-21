package com.averycorp.prismtask.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal exposing the active [PrismThemeColors] palette. Consumers
 * can read intent-named tokens (e.g. `LocalPrismColors.current.primary`) and
 * stay consistent regardless of which [PrismTheme] the user has selected.
 */
val LocalPrismColors = staticCompositionLocalOf { prismThemeColors(PrismTheme.VOID) }

/**
 * CompositionLocal exposing the active [PrismThemeFonts] trio (body, display,
 * mono) for the selected [PrismTheme]. Use `.body`, `.display`, or `.mono`
 * to access the individual [FontFamily] values.
 */
val LocalPrismFonts = staticCompositionLocalOf { prismThemeFonts(PrismTheme.VOID) }

/**
 * CompositionLocal exposing the currently-selected [PrismTheme]. This lets
 * screens branch on the theme for effects that aren't expressed purely in
 * colors or fonts (e.g. opting into per-theme display font via
 * [prismDisplayFont]).
 */
val LocalPrismTheme = staticCompositionLocalOf { PrismTheme.VOID }
