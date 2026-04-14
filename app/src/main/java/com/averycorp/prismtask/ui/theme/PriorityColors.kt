package com.averycorp.prismtask.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PriorityColors(
    val none: Color = Color(0xFF9E9E9E),
    val low: Color = Color(0xFF4A90D9),
    val medium: Color = Color(0xFFF59E0B),
    val high: Color = Color(0xFFE86F3C),
    val urgent: Color = Color(0xFFD4534A)
) {
    fun forLevel(priority: Int): Color = when (priority) {
        1 -> low
        2 -> medium
        3 -> high
        4 -> urgent
        else -> none
    }
}

val LocalPriorityColors = staticCompositionLocalOf { PriorityColors() }
