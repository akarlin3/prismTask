package com.averycorp.prismtask.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette tokens for a [PrismTheme]. Named to describe intent rather than
 * Material slot so the same token maps cleanly across Cyberpunk, Synthwave,
 * Matrix and Void variants.
 *
 * ### Surface hierarchy
 * - [background]:       Deepest screen background
 * - [surface]:          Card / tile background
 * - [surfaceVariant]:   Slightly elevated surface (nested cards, popovers)
 * - [border]:           Subtle divider / card stroke (typically alpha-tinted)
 *
 * ### Accent hierarchy
 * - [primary]:          Main accent color (CTAs, progress, focus ring)
 * - [secondary]:        Secondary accent (links, highlights, chart series)
 *
 * ### Text / icon
 * - [onBackground]:     Primary text on [background] / [surface]
 * - [onSurface]:        Secondary text, icons, metadata
 * - [muted]:            Hint / placeholder / disabled text
 *
 * ### Urgency (maps to Material error slot)
 * - [urgentAccent]:     Urgent task icon / stripe color
 * - [urgentSurface]:    Urgent task background pill
 *
 * ### Tags
 * - [tagSurface]:       Generic tag pill background
 * - [tagText]:          Generic tag pill text
 *
 * ### Semantic state colors
 * - [successColor]:     Positive outcome — completion, streaks, good performance
 * - [warningColor]:     Caution / moderate urgency — due-today, medium-load days
 * - [destructiveColor]: Delete / critical failure — distinct from [urgentAccent]
 *                       which maps to Material's error slot
 * - [infoColor]:        Neutral informational state
 *
 * ### Swipe action backgrounds
 * - [swipeComplete]:    Complete / check-off action reveal
 * - [swipeDelete]:      Delete action reveal
 * - [swipeReschedule]:  Reschedule action reveal
 * - [swipeArchive]:     Archive action reveal
 * - [swipeMove]:        Move / reorder action reveal
 * - [swipeFlag]:        Flag / bookmark action reveal
 *
 * ### Eisenhower quadrant backgrounds
 * - [quadrantQ1]:       Do First — urgent + important
 * - [quadrantQ2]:       Schedule — not urgent + important
 * - [quadrantQ3]:       Delegate — urgent + not important
 * - [quadrantQ4]:       Eliminate — not urgent + not important
 *
 * ### Data visualization
 * - [dataVisualizationPalette]: Eight ordered colors for charts, category dots,
 *   template / course color pickers, and progress visualizations. Order is
 *   chosen for maximum contrast between adjacent series.
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
    val tagText: Color,
    // Semantic state colors
    val successColor: Color,
    val warningColor: Color,
    val destructiveColor: Color,
    val infoColor: Color,
    // Swipe action backgrounds
    val swipeComplete: Color,
    val swipeDelete: Color,
    val swipeReschedule: Color,
    val swipeArchive: Color,
    val swipeMove: Color,
    val swipeFlag: Color,
    // Eisenhower quadrant backgrounds
    val quadrantQ1: Color,
    val quadrantQ2: Color,
    val quadrantQ3: Color,
    val quadrantQ4: Color,
    // Data visualization palette (8 entries)
    val dataVisualizationPalette: List<Color>
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
    tagText = Color(0xFF00F5FF),
    successColor = Color(0xFF00FF88),
    warningColor = Color(0xFFFFCC00),
    destructiveColor = Color(0xFFFF3366),
    infoColor = Color(0xFF0099FF),
    swipeComplete = Color(0xFF00B866),
    swipeDelete = Color(0xFFCC0033),
    swipeReschedule = Color(0xFF0077CC),
    swipeArchive = Color(0xFF335577),
    swipeMove = Color(0xFF007799),
    swipeFlag = Color(0xFFCC8800),
    quadrantQ1 = Color(0xFF220011),
    quadrantQ2 = Color(0xFF001122),
    quadrantQ3 = Color(0xFF221100),
    quadrantQ4 = Color(0xFF0A0A14),
    dataVisualizationPalette = listOf(
        Color(0xFF00F5FF),
        Color(0xFFFF00AA),
        Color(0xFF00FF88),
        Color(0xFFFFCC00),
        Color(0xFF9933FF),
        Color(0xFFFF5500),
        Color(0xFF00AAFF),
        Color(0xFF00FFCC)
    )
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
    tagText = Color(0xFF6E3FFF),
    successColor = Color(0xFF00EE88),
    warningColor = Color(0xFFFFCC00),
    destructiveColor = Color(0xFFFF2255),
    infoColor = Color(0xFF44AAFF),
    swipeComplete = Color(0xFF009955),
    swipeDelete = Color(0xFFBB1133),
    swipeReschedule = Color(0xFF2255AA),
    swipeArchive = Color(0xFF3A2255),
    swipeMove = Color(0xFF004466),
    swipeFlag = Color(0xFFAA6600),
    quadrantQ1 = Color(0xFF280018),
    quadrantQ2 = Color(0xFF0F0828),
    quadrantQ3 = Color(0xFF281400),
    quadrantQ4 = Color(0xFF100818),
    dataVisualizationPalette = listOf(
        Color(0xFFFF2D87),
        Color(0xFF6E3FFF),
        Color(0xFF00EE88),
        Color(0xFFFFCC00),
        Color(0xFF44AAFF),
        Color(0xFFFF7700),
        Color(0xFF00DDCC),
        Color(0xFFFF66CC)
    )
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
    tagText = Color(0xFF00FF41),
    successColor = Color(0xFF00EE33),
    warningColor = Color(0xFFBBEE00),
    destructiveColor = Color(0xFFFF4422),
    infoColor = Color(0xFF00AA88),
    swipeComplete = Color(0xFF007722),
    swipeDelete = Color(0xFFAA2200),
    swipeReschedule = Color(0xFF005533),
    swipeArchive = Color(0xFF1A3322),
    swipeMove = Color(0xFF003322),
    swipeFlag = Color(0xFF887700),
    quadrantQ1 = Color(0xFF1A0500),
    quadrantQ2 = Color(0xFF001A08),
    quadrantQ3 = Color(0xFF1A1200),
    quadrantQ4 = Color(0xFF020F04),
    dataVisualizationPalette = listOf(
        Color(0xFF00FF41),
        Color(0xFFAAFF00),
        Color(0xFF00EE33),
        Color(0xFFBBEE00),
        Color(0xFF00FFAA),
        Color(0xFF66FF44),
        Color(0xFF00AA66),
        Color(0xFF88FF22)
    )
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
    tagText = Color(0xFF8888CC),
    successColor = Color(0xFF6EC87A),
    warningColor = Color(0xFFD4A843),
    destructiveColor = Color(0xFFE06060),
    infoColor = Color(0xFF7090C8),
    swipeComplete = Color(0xFF4A9A58),
    swipeDelete = Color(0xFFAA4444),
    swipeReschedule = Color(0xFF446698),
    swipeArchive = Color(0xFF3A3A4A),
    swipeMove = Color(0xFF2A4060),
    swipeFlag = Color(0xFF8A6A30),
    quadrantQ1 = Color(0xFF261616),
    quadrantQ2 = Color(0xFF141A26),
    quadrantQ3 = Color(0xFF22180C),
    quadrantQ4 = Color(0xFF141416),
    dataVisualizationPalette = listOf(
        Color(0xFFC8B8FF),
        Color(0xFF8888CC),
        Color(0xFF6EC87A),
        Color(0xFFD4A843),
        Color(0xFF7090C8),
        Color(0xFFE06060),
        Color(0xFFAA88CC),
        Color(0xFF70A8A0)
    )
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
