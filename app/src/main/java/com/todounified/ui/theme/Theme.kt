package com.todounified.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// ── Colors ──
val Surface = Color(0xFF0D0D12)
val SurfaceVariant = Color(0xFF14141C)
val SurfaceElevated = Color(0xFF1A1A24)
val OnSurface = Color(0xFFE2E2E8)
val OnSurfaceDim = Color(0x80E2E2E8)
val OnSurfaceFaint = Color(0x40E2E2E8)
val Border = Color(0x14FFFFFF)
val BorderBright = Color(0x28FFFFFF)

val Indigo = Color(0xFF6366F1)
val Blue = Color(0xFF3B82F6)
val Green = Color(0xFF22C55E)
val Red = Color(0xFFFF3B5C)
val Orange = Color(0xFFFF9F1A)
val Gray = Color(0xFF6B7280)

val IndigoBg = Color(0x1F6366F1)
val BlueBg = Color(0x1F3B82F6)
val GreenBg = Color(0x1F22C55E)
val RedBg = Color(0x1FFF3B5C)
val OrangeBg = Color(0x1FFF9F1A)
val GrayBg = Color(0x1F6B7280)

// ── Color scheme ──
private val DarkColorScheme = darkColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Blue,
    background = Surface,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = OnSurface,
    onSurface = OnSurface,
    outline = Border,
    error = Red,
)

// ── Monospace font (system fallback) ──
val MonoFont = FontFamily.Monospace

@Composable
fun TodoUnifiedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
