package com.averycorp.prismtask.ui.theme

import androidx.compose.ui.graphics.Color
import com.averycorp.prismtask.domain.model.LifeCategory

/**
 * Palette for the Work-Life Balance Engine (v1.4.0 V1).
 *
 * These colors are intentionally a touch more saturated than the Material
 * surface palette so the stacked balance bar on the Today screen reads at a
 * glance. They share hues with the priority colors so the overall UI stays
 * coherent.
 */
object LifeCategoryColor {
    val WORK = Color(0xFF2E7BE5)      // blue
    val PERSONAL = Color(0xFF37A669)  // green
    val SELF_CARE = Color(0xFF8A4FCF) // purple
    val HEALTH = Color(0xFFE05353)    // red
    val UNCATEGORIZED = Color(0xFF9E9E9E) // neutral gray

    fun forCategory(category: LifeCategory): Color = when (category) {
        LifeCategory.WORK -> WORK
        LifeCategory.PERSONAL -> PERSONAL
        LifeCategory.SELF_CARE -> SELF_CARE
        LifeCategory.HEALTH -> HEALTH
        LifeCategory.UNCATEGORIZED -> UNCATEGORIZED
    }
}
