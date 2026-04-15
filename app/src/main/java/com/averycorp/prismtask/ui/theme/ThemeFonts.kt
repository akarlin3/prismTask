package com.averycorp.prismtask.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.averycorp.prismtask.R

/**
 * Downloadable-font provider pointing at Google Play Services' font catalog.
 * The referenced cert array lives in `res/values/font_certs.xml`.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * Builds a [FontFamily] backed by the named Google Font across the common
 * weight range we use in-app (Normal, Medium, SemiBold, Bold). If constructing
 * the Google-Fonts-backed family throws for any reason (e.g. Play Services
 * unavailable at build/inspection time), [fallback] is returned instead so
 * the UI still renders with a reasonable system font.
 */
private fun googleFontFamilyOrFallback(
    name: String,
    fallback: FontFamily
): FontFamily = try {
    val font = GoogleFont(name)
    FontFamily(
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Bold)
    )
} catch (_: Throwable) {
    fallback
}

// Google Fonts used by the four PrismThemes. Resolved lazily so we don't pay
// the construction cost (or crash in previews that lack the Play Services
// provider) until a screen actually asks for the family.
private val ShareTechMono: FontFamily by lazy {
    googleFontFamilyOrFallback("Share Tech Mono", FontFamily.Monospace)
}

private val Rajdhani: FontFamily by lazy {
    googleFontFamilyOrFallback("Rajdhani", FontFamily.SansSerif)
}

private val DmSans: FontFamily by lazy {
    googleFontFamilyOrFallback("DM Sans", FontFamily.SansSerif)
}

private val Orbitron: FontFamily by lazy {
    googleFontFamilyOrFallback("Orbitron", FontFamily.SansSerif)
}

/**
 * Body / UI font family for the given [theme].
 *
 * - CYBERPUNK, MATRIX: Share Tech Mono (monospaced, terminal feel)
 * - SYNTHWAVE:         Rajdhani (condensed sans-serif display)
 * - VOID:              DM Sans (clean geometric sans-serif)
 */
fun prismThemeFonts(theme: PrismTheme): FontFamily = when (theme) {
    PrismTheme.CYBERPUNK -> ShareTechMono
    PrismTheme.MATRIX -> ShareTechMono
    PrismTheme.SYNTHWAVE -> Rajdhani
    PrismTheme.VOID -> DmSans
}

/**
 * Display / headline font family for the given [theme]. Pairs with
 * [prismThemeFonts] but leans more decorative for hero text.
 *
 * - CYBERPUNK, MATRIX, SYNTHWAVE: Orbitron (geometric futurist display)
 * - VOID:                         DM Sans (reuses body face for a minimal look)
 */
fun prismDisplayFont(theme: PrismTheme): FontFamily = when (theme) {
    PrismTheme.CYBERPUNK -> Orbitron
    PrismTheme.MATRIX -> Orbitron
    PrismTheme.SYNTHWAVE -> Orbitron
    PrismTheme.VOID -> DmSans
}
