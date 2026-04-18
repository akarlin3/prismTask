package com.averycorp.prismtask.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * CompositionLocal exposing the active [PrismThemeColors] palette. Consumers
 * can read intent-named tokens (e.g. `LocalPrismColors.current.primary`) and
 * stay consistent regardless of which [PrismTheme] the user has selected.
 */
val LocalPrismColors = staticCompositionLocalOf { prismThemeColors(PrismTheme.VOID) }

/**
 * CompositionLocal exposing the active body [FontFamily] for the selected
 * [PrismTheme]. Defaults to [FontFamily.SansSerif] for previews or any
 * composable that renders outside the normal theme scope.
 */
val LocalPrismFonts = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

/**
 * CompositionLocal exposing the currently-selected [PrismTheme]. This lets
 * screens branch on the theme for effects that aren't expressed purely in
 * colors or fonts (e.g. opting into per-theme display font via
 * [prismDisplayFont]).
 */
val LocalPrismTheme = staticCompositionLocalOf { PrismTheme.VOID }

