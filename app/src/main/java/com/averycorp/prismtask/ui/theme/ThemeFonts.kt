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

/**
 * Builds a display-only [FontFamily] from a single-weight Google Font. Many
 * display faces (Monoton, VT323, Audiowide) ship only as Regular, so we avoid
 * the multi-weight path that would silently fall back to a system font for the
 * missing weights.
 */
private fun singleWeightGoogleFontOrFallback(
    name: String,
    fallback: FontFamily
): FontFamily = try {
    val font = GoogleFont(name)
    FontFamily(
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Normal)
    )
} catch (_: Throwable) {
    fallback
}

// Google Fonts used by the four PrismThemes. Resolved lazily so we don't pay
// the construction cost (or crash in previews that lack the Play Services
// provider) until a screen actually asks for the family.

// Matrix body: monospaced terminal face.
private val ShareTechMono: FontFamily by lazy {
    googleFontFamilyOrFallback("Share Tech Mono", FontFamily.Monospace)
}

// Matrix display: pixelated CRT terminal face (single weight).
private val Vt323: FontFamily by lazy {
    singleWeightGoogleFontOrFallback("VT323", FontFamily.Monospace)
}

// Cyberpunk body: angular techno sans-serif with sci-fi flair.
private val ChakraPetch: FontFamily by lazy {
    googleFontFamilyOrFallback("Chakra Petch", FontFamily.SansSerif)
}

// Cyberpunk display: wide futurist face with strong geometric forms.
private val Audiowide: FontFamily by lazy {
    singleWeightGoogleFontOrFallback("Audiowide", FontFamily.SansSerif)
}

// Synthwave body: condensed sans-serif that reads cleanly next to heavy display type.
private val Rajdhani: FontFamily by lazy {
    googleFontFamilyOrFallback("Rajdhani", FontFamily.SansSerif)
}

// Synthwave display: iconic 80s neon-stripe letters.
private val Monoton: FontFamily by lazy {
    singleWeightGoogleFontOrFallback("Monoton", FontFamily.SansSerif)
}

// Void body: geometric sans-serif with quiet technical character.
private val SpaceGrotesk: FontFamily by lazy {
    googleFontFamilyOrFallback("Space Grotesk", FontFamily.SansSerif)
}

// Void display: serif counterpoint for minimal, editorial hero text.
private val Fraunces: FontFamily by lazy {
    googleFontFamilyOrFallback("Fraunces", FontFamily.Serif)
}

/**
 * Body / UI font family for the given [theme]. Each PrismTheme uses a
 * distinct body face so the running UI feels unmistakably tied to its
 * aesthetic:
 *
 * - CYBERPUNK: Chakra Petch (angular techno sans-serif)
 * - MATRIX:    Share Tech Mono (terminal monospace)
 * - SYNTHWAVE: Rajdhani (condensed 80s-inflected sans)
 * - VOID:      Space Grotesk (quiet geometric sans)
 */
fun prismThemeFonts(theme: PrismTheme): FontFamily = when (theme) {
    PrismTheme.CYBERPUNK -> ChakraPetch
    PrismTheme.MATRIX -> ShareTechMono
    PrismTheme.SYNTHWAVE -> Rajdhani
    PrismTheme.VOID -> SpaceGrotesk
}

/**
 * Display / headline font family for the given [theme]. Pairs with
 * [prismThemeFonts] but leans decorative and unique per theme:
 *
 * - CYBERPUNK: Audiowide (wide futurist hero face)
 * - MATRIX:    VT323 (pixelated CRT terminal)
 * - SYNTHWAVE: Monoton (neon-stripe 80s display)
 * - VOID:      Fraunces (editorial serif counterpoint)
 */
fun prismDisplayFont(theme: PrismTheme): FontFamily = when (theme) {
    PrismTheme.CYBERPUNK -> Audiowide
    PrismTheme.MATRIX -> Vt323
    PrismTheme.SYNTHWAVE -> Monoton
    PrismTheme.VOID -> Fraunces
}
