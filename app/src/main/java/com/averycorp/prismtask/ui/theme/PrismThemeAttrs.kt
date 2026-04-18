package com.averycorp.prismtask.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Per-theme shape and decoration tokens that drive visual flourishes beyond
 * colors and fonts: corner radii, glow intensity, CRT scanlines, Cyberpunk
 * HUD brackets, Synthwave grid floor, Matrix terminal prompts, and Void
 * editorial hairlines. Screens read these via [LocalPrismAttrs] to branch
 * on structural / decorative differences between themes.
 *
 * - [chipShape]:      Sharp rectangular vs. fully-pill chip corners.
 * - [radius]:         Chip corner radius in dp.
 * - [cardRadius]:     Card corner radius in dp.
 * - [glow]:           Intensity of primary-colored glow shadows.
 * - [brackets]:       Cyberpunk HUD corner brackets on hero cards.
 * - [hudDividers]:    Cyberpunk dashed separator lines.
 * - [sunset]:         Synthwave radial gradient behind hero cards.
 * - [gridFloor]:      Synthwave perspective-grid floor overlay.
 * - [terminal]:       Matrix terminal prompts, blinking caret, rain lines.
 * - [scanlines]:      Cyberpunk / Matrix CRT scanline overlay.
 * - [editorial]:      Void hairline borders and generous whitespace.
 * - [displayUpper]:   Whether display/headline text is uppercased.
 * - [displayTracking]:Letter-spacing multiplier applied to display text (em).
 * - [density]:        Tight (compact) or Airy (generous) vertical rhythm.
 */
data class PrismThemeAttrs(
    val chipShape: ChipShape,
    val radius: Int,
    val cardRadius: Int,
    val glow: GlowLevel,
    val brackets: Boolean,
    val hudDividers: Boolean,
    val sunset: Boolean,
    val gridFloor: Boolean,
    val terminal: Boolean,
    val scanlines: Boolean,
    val editorial: Boolean,
    val displayUpper: Boolean,
    val displayTracking: Float,
    val density: ThemeDensity
)

enum class ChipShape { SHARP, PILL }
enum class GlowLevel { NONE, SOFT, STRONG, HEAVY }
enum class ThemeDensity { TIGHT, AIRY }

private val CyberpunkAttrs = PrismThemeAttrs(
    chipShape = ChipShape.SHARP,
    radius = 2,
    cardRadius = 4,
    glow = GlowLevel.STRONG,
    brackets = true,
    hudDividers = true,
    sunset = false,
    gridFloor = false,
    terminal = false,
    scanlines = true,
    editorial = false,
    displayUpper = true,
    displayTracking = 0.06f,
    density = ThemeDensity.TIGHT
)

private val SynthwaveAttrs = PrismThemeAttrs(
    chipShape = ChipShape.PILL,
    radius = 18,
    cardRadius = 22,
    glow = GlowLevel.HEAVY,
    brackets = false,
    hudDividers = false,
    sunset = true,
    gridFloor = true,
    terminal = false,
    scanlines = false,
    editorial = false,
    displayUpper = true,
    displayTracking = 0.08f,
    density = ThemeDensity.AIRY
)

private val MatrixAttrs = PrismThemeAttrs(
    chipShape = ChipShape.SHARP,
    radius = 0,
    cardRadius = 0,
    glow = GlowLevel.SOFT,
    brackets = false,
    hudDividers = false,
    sunset = false,
    gridFloor = false,
    terminal = true,
    scanlines = true,
    editorial = false,
    displayUpper = false,
    displayTracking = 0.02f,
    density = ThemeDensity.TIGHT
)

private val VoidAttrs = PrismThemeAttrs(
    chipShape = ChipShape.PILL,
    radius = 10,
    cardRadius = 14,
    glow = GlowLevel.NONE,
    brackets = false,
    hudDividers = false,
    sunset = false,
    gridFloor = false,
    terminal = false,
    scanlines = false,
    editorial = true,
    displayUpper = false,
    displayTracking = -0.02f,
    density = ThemeDensity.AIRY
)

/** Returns the [PrismThemeAttrs] for [theme]. Results are stable singletons. */
fun prismThemeAttrs(theme: PrismTheme): PrismThemeAttrs = when (theme) {
    PrismTheme.CYBERPUNK -> CyberpunkAttrs
    PrismTheme.SYNTHWAVE -> SynthwaveAttrs
    PrismTheme.MATRIX -> MatrixAttrs
    PrismTheme.VOID -> VoidAttrs
}

/**
 * CompositionLocal exposing the active [PrismThemeAttrs] decoration tokens.
 * Defaults to [VoidAttrs] so composables that render outside [PrismTaskTheme]
 * (e.g. Compose previews) always have a valid, non-crashing value.
 */
val LocalPrismAttrs = staticCompositionLocalOf { prismThemeAttrs(PrismTheme.VOID) }
