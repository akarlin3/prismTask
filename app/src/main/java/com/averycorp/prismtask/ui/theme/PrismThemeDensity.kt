package com.averycorp.prismtask.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Per-theme density tokens derived from [PrismThemeAttrs.density].
 *
 * TIGHT (Cyberpunk / Matrix): compact vertical rhythm, minimal breathing room.
 * AIRY  (Synthwave / Void):   generous spacing, open layout.
 *
 * Use these tokens for:
 * - [screenPadding]:    Scaffold/screen root horizontal/vertical padding.
 * - [cardPadding]:      Outermost padding inside a Card or surface container.
 * - [listItemSpacing]:  `Arrangement.spacedBy(listItemSpacing)` in lazy lists.
 * - [spacingSmall/Medium/Large]: General-purpose spacing at 2/4/6× [spacingBase].
 *
 * Do NOT use for icon-to-text gaps, chip internal padding, or Spacer heights
 * with intentional fixed values — those stay hardcoded.
 */
data class PrismThemeDensity(
    val spacingBase: Dp,
    val spacingSmall: Dp,
    val spacingMedium: Dp,
    val spacingLarge: Dp,
    val cardPadding: Dp,
    val listItemSpacing: Dp,
    val screenPadding: Dp
)

private val TightDensity = PrismThemeDensity(
    spacingBase = 4.dp,
    spacingSmall = 8.dp,
    spacingMedium = 16.dp,
    spacingLarge = 24.dp,
    cardPadding = 12.dp,
    listItemSpacing = 8.dp,
    screenPadding = 16.dp
)

private val AiryDensity = PrismThemeDensity(
    spacingBase = 6.dp,
    spacingSmall = 12.dp,
    spacingMedium = 24.dp,
    spacingLarge = 36.dp,
    cardPadding = 16.dp,
    listItemSpacing = 12.dp,
    screenPadding = 20.dp
)

fun PrismThemeAttrs.toDensity(): PrismThemeDensity = when (density) {
    ThemeDensity.TIGHT -> TightDensity
    ThemeDensity.AIRY -> AiryDensity
}

val LocalPrismDensity = staticCompositionLocalOf { prismThemeAttrs(PrismTheme.VOID).toDensity() }
