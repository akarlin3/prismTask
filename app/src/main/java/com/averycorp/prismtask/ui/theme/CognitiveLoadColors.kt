package com.averycorp.prismtask.ui.theme

import androidx.compose.ui.graphics.Color
import com.averycorp.prismtask.domain.model.CognitiveLoad

/**
 * Palette for the Cognitive Load (start-friction) dimension. Distinct
 * hues from [LifeCategoryColor] and [TaskModeColor] so the three
 * balance bars don't visually conflict on the Today screen.
 *
 * EASY → green (low friction, "go for it"); MEDIUM → amber (moderate
 * friction); HARD → magenta (high friction, "deserves a deliberate
 * start"). Magenta avoids re-using the LifeCategory.WORK / TaskMode.WORK
 * blue — Cognitive Load is a separate axis and the color picker should
 * reinforce that.
 *
 * See `docs/COGNITIVE_LOAD.md` for the philosophy.
 */
object CognitiveLoadColor {
    val EASY = Color(0xFF4CAF6E) // green
    val MEDIUM = Color(0xFFD89A2E) // amber (slightly distinct from TaskMode.PLAY)
    val HARD = Color(0xFFB54199) // magenta
    val UNCATEGORIZED = Color(0xFF9E9E9E) // neutral gray

    fun forLoad(load: CognitiveLoad): Color = when (load) {
        CognitiveLoad.EASY -> EASY
        CognitiveLoad.MEDIUM -> MEDIUM
        CognitiveLoad.HARD -> HARD
        CognitiveLoad.UNCATEGORIZED -> UNCATEGORIZED
    }
}
