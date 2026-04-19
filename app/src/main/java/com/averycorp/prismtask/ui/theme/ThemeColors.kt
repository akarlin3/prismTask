package com.averycorp.prismtask.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette tokens for a [PrismTheme]. Named to describe intent rather than
 * Material slot so the same token maps cleanly across Cyberpunk, Synthwave,
 * Matrix and Void variants.
 *
 * - [background]:       Deepest screen background
 * - [surface]:          Card / tile background
 * - [surfaceVariant]:   Slightly elevated surface (nested cards, popovers)
 * - [border]:           Subtle divider / card stroke (typically alpha-tinted)
 * - [primary]:          Main accent color (CTAs, progress, focus ring)
 * - [secondary]:        Secondary accent (links, highlights, chart series)
 * - [onBackground]:     Primary text on [background] / [surface]
 * - [onSurface]:        Secondary text, icons, metadata
 * - [muted]:            Hint / placeholder / disabled text
 * - [urgentAccent]:     Urgent task icon / stripe color
 * - [urgentSurface]:    Urgent task background pill
 * - [tagSurface]:       Generic tag pill background
 * - [tagText]:          Generic tag pill text
 */
data class PrismThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val primary: Color,
    val secondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val muted: Color,
    val urgentAccent: Color,
    val urgentSurface: Color,
    val tagSurface: Color,
    val tagText: Color
)

private val CyberpunkColors = PrismThemeColors(
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF0D0D18),
    surfaceVariant = Color(0xFF111120),
    // #00f5ff at 10% alpha -> 0x1A00F5FF
    border = Color(0x1A00F5FF),
    primary = Color(0xFF00F5FF),
    secondary = Color(0xFFFF00AA),
    onBackground = Color(0xFFE0F8FF),
    onSurface = Color(0xFFA0CCD4),
    muted = Color(0xFF4A8A9A),
    urgentAccent = Color(0xFFFF00AA),
    urgentSurface = Color(0xFF1A0010),
    tagSurface = Color(0xFF001A1A),
    tagText = Color(0xFF00F5FF)
)

private val SynthwaveColors = PrismThemeColors(
    background = Color(0xFF0D0717),
    surface = Color(0xFF130820),
    surfaceVariant = Color(0xFF1A0F2E),
    // #6e3fff at 18% alpha -> 0x2E6E3FFF (themes.js: rgba(110,63,255,0.18))
    border = Color(0x2E6E3FFF),
    primary = Color(0xFFFF2D87),
    secondary = Color(0xFF6E3FFF),
    onBackground = Color(0xFFF0D0FF),
    onSurface = Color(0xFFB080D0),
    muted = Color(0xFF5E3A7A),
    urgentAccent = Color(0xFFFF2D87),
    urgentSurface = Color(0xFF1F0015),
    tagSurface = Color(0xFF12082A),
    tagText = Color(0xFF6E3FFF)
)

private val MatrixColors = PrismThemeColors(
    background = Color(0xFF010D03),
    surface = Color(0xFF010F04),
    surfaceVariant = Color(0xFF021206),
    // #00ff41 at 14% alpha -> 0x2300FF41 (themes.js: rgba(0,255,65,0.14))
    border = Color(0x2300FF41),
    primary = Color(0xFF00FF41),
    secondary = Color(0xFFAAFF00),
    onBackground = Color(0xFFB0FFB8),
    onSurface = Color(0xFF70CC80),
    muted = Color(0xFF1A5E25),
    urgentAccent = Color(0xFFAAFF00),
    urgentSurface = Color(0xFF0A1400),
    tagSurface = Color(0xFF001A06),
    tagText = Color(0xFF00FF41)
)

private val VoidColors = PrismThemeColors(
    background = Color(0xFF111113),
    surface = Color(0xFF161618),
    surfaceVariant = Color(0xFF1E1E22),
    // #2e2e34 at 0x80 alpha (token spec: white ~20% alpha)
    border = Color(0x802E2E34),
    primary = Color(0xFFC8B8FF),
    secondary = Color(0xFF8888CC),
    onBackground = Color(0xFFDCDCE4),
    onSurface = Color(0xFFA0A0AB),
    muted = Color(0xFF3E3E4A),
    urgentAccent = Color(0xFFE8A0A0),
    urgentSurface = Color(0xFF261616),
    tagSurface = Color(0xFF1A1A26),
    tagText = Color(0xFF8888CC)
)

/**
 * Returns the [PrismThemeColors] palette associated with [theme]. Palettes
 * are stable across process lifetime — callers can cache the result.
 */
fun prismThemeColors(theme: PrismTheme): PrismThemeColors = when (theme) {
    PrismTheme.CYBERPUNK -> CyberpunkColors
    PrismTheme.SYNTHWAVE -> SynthwaveColors
    PrismTheme.MATRIX -> MatrixColors
    PrismTheme.VOID -> VoidColors
}
