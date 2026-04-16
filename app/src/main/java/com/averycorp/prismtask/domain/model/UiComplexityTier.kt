package com.averycorp.prismtask.domain.model

/**
 * Three-tier UI complexity system. Controls which settings, controls,
 * and options are visible throughout the app.
 *
 * Ordinal order matters: [isAtLeast] compares ordinals for tier gating.
 */
enum class UiComplexityTier(
    val displayName: String,
    val description: String
) {
    BASIC(
        displayName = "Basic",
        description = "Just the essentials. Clean and simple."
    ),
    STANDARD(
        displayName = "Standard",
        description = "Full features with smart defaults."
    ),
    POWER(
        displayName = "Power User",
        description = "Everything exposed. Full control."
    );

    companion object {
        fun fromName(name: String?): UiComplexityTier =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/**
 * Returns true if this tier's ordinal is greater than or equal to [minimum]'s ordinal.
 * Used for tier-gating: `if (tier.isAtLeast(STANDARD)) { showFeature() }`.
 */
fun UiComplexityTier.isAtLeast(minimum: UiComplexityTier): Boolean =
    this.ordinal >= minimum.ordinal
