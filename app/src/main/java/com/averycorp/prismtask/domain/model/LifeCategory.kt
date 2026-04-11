package com.averycorp.prismtask.domain.model

/**
 * Work-Life Balance categorization for tasks.
 *
 * Every task may optionally be tagged with a [LifeCategory] so that the
 * Work-Life Balance Engine (see domain/usecase/BalanceTracker.kt) can
 * track how the user's effort is split across different life areas.
 *
 * Tasks that have not been classified default to [UNCATEGORIZED] and are
 * excluded from ratio computations.
 */
enum class LifeCategory {
    WORK,
    PERSONAL,
    SELF_CARE,
    HEALTH,
    UNCATEGORIZED;

    companion object {
        /** Categories that participate in balance ratio computation. */
        val TRACKED: List<LifeCategory> = listOf(WORK, PERSONAL, SELF_CARE, HEALTH)

        /**
         * Parse a stored Room string back into an enum value, tolerating
         * unknown/legacy values by returning [UNCATEGORIZED].
         */
        fun fromStorage(value: String?): LifeCategory {
            if (value.isNullOrBlank()) return UNCATEGORIZED
            return try {
                valueOf(value)
            } catch (_: IllegalArgumentException) {
                UNCATEGORIZED
            }
        }

        /** Display label for UI. */
        fun label(category: LifeCategory): String = when (category) {
            WORK -> "Work"
            PERSONAL -> "Personal"
            SELF_CARE -> "Self-Care"
            HEALTH -> "Health"
            UNCATEGORIZED -> "Uncategorized"
        }
    }
}
