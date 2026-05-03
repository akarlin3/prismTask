package com.averycorp.prismtask.domain.model

/**
 * Start-friction / cognitive-load-to-start dimension — orthogonal to
 * [LifeCategory], [TaskMode], and the Eisenhower quadrant.
 *
 * Cognitive Load answers: *how much friction stands between the user
 * and starting?* This is independent of what the task is about
 * ([LifeCategory]), what reward type it produces ([TaskMode]), and
 * whether it's urgent / important (Eisenhower).
 *
 * A task carries a [CognitiveLoad] alongside the other dimensions.
 * Examples:
 *  - "Reply 'thanks' to mom's text" → PERSONAL / WORK / Q4 / **EASY**
 *  - "Draft difficult email to recommender" → PERSONAL / WORK / Q2 / **HARD**
 *  - "Pay overdue electric bill" → PERSONAL / WORK / Q1 / **HARD** (5 min,
 *    high friction even though urgent and short)
 *
 * The framing as a "bridge" between Eisenhower and Work/Play/Relax is
 * a narrative hook for the philosophy doc — the *formal* definition is
 * start-friction. See `docs/COGNITIVE_LOAD.md`.
 *
 * Tasks that have not been classified default to [UNCATEGORIZED] and are
 * excluded from balance ratio computations.
 */
enum class CognitiveLoad {
    EASY,
    MEDIUM,
    HARD,
    UNCATEGORIZED;

    companion object {
        /** Loads that participate in balance ratio computation. */
        val TRACKED: List<CognitiveLoad> = listOf(EASY, MEDIUM, HARD)

        /**
         * Parse a stored Room string back into an enum value, tolerating
         * unknown/legacy values by returning [UNCATEGORIZED].
         */
        fun fromStorage(value: String?): CognitiveLoad {
            if (value.isNullOrBlank()) return UNCATEGORIZED
            return try {
                valueOf(value)
            } catch (_: IllegalArgumentException) {
                UNCATEGORIZED
            }
        }

        /** Display label for UI. */
        fun label(load: CognitiveLoad): String = when (load) {
            EASY -> "Easy"
            MEDIUM -> "Medium"
            HARD -> "Hard"
            UNCATEGORIZED -> "Uncategorized"
        }
    }
}
