package com.averycorp.prismtask.ui.theme

/**
 * User-selectable app theme variants introduced in v1.4.x.
 *
 * Each entry defines a cohesive palette and font pairing that flows through
 * [prismThemeColors] and [prismThemeFonts] / [prismDisplayFont]. The existing
 * Material [PrismTaskTheme] wrapper will layer these tokens on top of the
 * Material color scheme so screens can opt into the new visual language
 * incrementally.
 */
enum class PrismTheme {
    CYBERPUNK,
    SYNTHWAVE,
    MATRIX,
    VOID
}
