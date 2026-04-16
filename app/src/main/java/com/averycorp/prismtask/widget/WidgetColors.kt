package com.averycorp.prismtask.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * Centralized color definitions for PrismTask Glance widgets.
 *
 * Every [ColorProvider] uses the two-argument form so colors adapt
 * correctly in both light and dark modes, including on devices
 * without Material You (API < 31) where GlanceTheme falls back to
 * static light/dark palettes.
 */
object WidgetColors {
    // Priority indicator colors (day, night)
    val priorityUrgent = ColorProvider(Color(0xFFD32F2F), Color(0xFFEF9A9A))
    val priorityHigh = ColorProvider(Color(0xFFF57C00), Color(0xFFFFCC80))
    val priorityMedium = ColorProvider(Color(0xFFFBC02D), Color(0xFFFFF176))
    val priorityLow = ColorProvider(Color(0xFF388E3C), Color(0xFFA5D6A7))
    val priorityNone = ColorProvider(Color(0xFF9E9E9E), Color(0xFFBDBDBD))

    // Productivity score bands
    val scoreGreen = ColorProvider(Color(0xFF2E7D32), Color(0xFFA5D6A7))
    val scoreOrange = ColorProvider(Color(0xFFED6C02), Color(0xFFFFCC80))
    val scoreRed = ColorProvider(Color(0xFFC62828), Color(0xFFEF9A9A))

    // Score badge backgrounds (semi-transparent)
    val scoreGreenBg = ColorProvider(Color(0x262E7D32), Color(0x33A5D6A7))
    val scoreOrangeBg = ColorProvider(Color(0x26ED6C02), Color(0x33FFCC80))
    val scoreRedBg = ColorProvider(Color(0x26C62828), Color(0x33EF9A9A))

    // Habit completion
    val habitComplete = ColorProvider(Color(0xFF2E7D32), Color(0xFFA5D6A7))
    val habitCompleteBg = ColorProvider(Color(0xFFC8E6C9), Color(0xFF1B5E20))
    val habitIncomplete = ColorProvider(Color(0xFFBDBDBD), Color(0xFF757575))

    // Streak fire accent
    val streakFire = ColorProvider(Color(0xFFFF6F00), Color(0xFFFFAB40))
    val streakGold = ColorProvider(Color(0xFFFFB300), Color(0xFFFFD54F))

    // Overdue
    val overdue = ColorProvider(Color(0xFFD32F2F), Color(0xFFEF9A9A))
    val overdueBg = ColorProvider(Color(0x33D32F2F), Color(0x33EF9A9A))

    // Timer accents
    val timerWork = ColorProvider(Color(0xFFF57C00), Color(0xFFFFCC80))
    val timerBreak = ColorProvider(Color(0xFF00897B), Color(0xFF80CBC4))
    val timerStop = ColorProvider(Color(0xFFC62828), Color(0xFFEF9A9A))

    // Calendar event default
    val calendarEvent = ColorProvider(Color(0xFF1976D2), Color(0xFF90CAF9))

    // Button text on colored backgrounds
    val onColored = ColorProvider(Color.White, Color(0xFF1C1B1F))
}
