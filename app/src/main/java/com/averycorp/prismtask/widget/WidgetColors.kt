package com.averycorp.prismtask.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * Centralized color definitions for PrismTask Glance widgets.
 *
 * Glance 1.1 removed the two-argument [ColorProvider] factory.
 * Each value now wraps a single colour that is legible on both
 * light and dark widget surfaces.
 */
object WidgetColors {
    // Priority indicator colors
    val priorityUrgent = ColorProvider(Color(0xFFD32F2F))
    val priorityHigh = ColorProvider(Color(0xFFF57C00))
    val priorityMedium = ColorProvider(Color(0xFFFBC02D))
    val priorityLow = ColorProvider(Color(0xFF388E3C))
    val priorityNone = ColorProvider(Color(0xFF9E9E9E))

    // Productivity score bands
    val scoreGreen = ColorProvider(Color(0xFF2E7D32))
    val scoreOrange = ColorProvider(Color(0xFFED6C02))
    val scoreRed = ColorProvider(Color(0xFFC62828))

    // Score badge backgrounds (semi-transparent)
    val scoreGreenBg = ColorProvider(Color(0x262E7D32))
    val scoreOrangeBg = ColorProvider(Color(0x26ED6C02))
    val scoreRedBg = ColorProvider(Color(0x26C62828))

    // Habit completion
    val habitComplete = ColorProvider(Color(0xFF2E7D32))
    val habitCompleteBg = ColorProvider(Color(0xFFC8E6C9))
    val habitIncomplete = ColorProvider(Color(0xFFBDBDBD))

    // Streak fire accent
    val streakFire = ColorProvider(Color(0xFFFF6F00))
    val streakGold = ColorProvider(Color(0xFFFFB300))

    // Overdue
    val overdue = ColorProvider(Color(0xFFD32F2F))
    val overdueBg = ColorProvider(Color(0x33D32F2F))

    // Timer accents
    val timerWork = ColorProvider(Color(0xFFF57C00))
    val timerBreak = ColorProvider(Color(0xFF00897B))
    val timerStop = ColorProvider(Color(0xFFC62828))

    // Calendar event default
    val calendarEvent = ColorProvider(Color(0xFF1976D2))

    // Button text on colored backgrounds
    val onColored = ColorProvider(Color.White)
}
