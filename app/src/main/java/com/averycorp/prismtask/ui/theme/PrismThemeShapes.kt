package com.averycorp.prismtask.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Per-theme shape tokens derived from [PrismThemeAttrs]. Provides ready-to-use
 * [CornerBasedShape] instances for the four shape roles used across the app:
 *
 * - [chip]:       Chip / tag corners — flat for SHARP themes, pill for PILL themes.
 * - [button]:     Button / icon-button corners — uses [PrismThemeAttrs.radius].
 * - [card]:       Card / surface corners — uses [PrismThemeAttrs.cardRadius].
 * - [extraSmall]: Small helper corners (text fields, tiny pills) — half of [PrismThemeAttrs.radius].
 * - [large]:      Bottom-sheet / dialog corners — 1.5× [PrismThemeAttrs.cardRadius].
 *
 * Consume via [LocalPrismShapes] or let Material [MaterialTheme.shapes] (wired in
 * [PrismTaskTheme]) propagate them automatically to Chips, Buttons, Cards, etc.
 */
data class PrismThemeShapes(
    val chip: CornerBasedShape,
    val button: CornerBasedShape,
    val card: CornerBasedShape,
    val extraSmall: CornerBasedShape,
    val large: CornerBasedShape
)

fun PrismThemeAttrs.toShapes(): PrismThemeShapes = PrismThemeShapes(
    chip = if (chipShape == ChipShape.SHARP) RoundedCornerShape(0.dp) else RoundedCornerShape(50),
    button = RoundedCornerShape(radius.dp),
    card = RoundedCornerShape(cardRadius.dp),
    extraSmall = RoundedCornerShape((radius / 2).dp),
    large = RoundedCornerShape((cardRadius * 3 / 2).dp)
)

val LocalPrismShapes = staticCompositionLocalOf { prismThemeAttrs(PrismTheme.VOID).toShapes() }
