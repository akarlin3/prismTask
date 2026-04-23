package com.averycorp.prismtask.domain.model.medication

/**
 * Tier category assigned to each medication. Tiers are hierarchical and
 * inclusive: every medication belongs to exactly one tier, and reaching a
 * higher tier on a slot implicitly means every med at a lower tier for that
 * slot is also taken (see [AchievedTier] + `MedicationTierComputer`).
 *
 * Stored on `medications.tier` as a lowercase string token to match the
 * legacy values seeded by migration 53 → 54 (`"essential"` default) and the
 * `SelfCareRoutines.medicationTiers` color/label lookup. Domain code converts
 * via [fromStorage] / [toStorage]; Room keeps the column as a plain `TEXT`.
 */
enum class MedicationTier {
    ESSENTIAL,
    PRESCRIPTION,
    COMPLETE;

    /** Lowercase token written to the `medications.tier` column. */
    fun toStorage(): String = name.lowercase()

    companion object {
        /**
         * Tier ladder from lowest to highest. Shared with `AchievedTier`
         * when computing the highest tier achieved for a slot on a day.
         */
        val LADDER: List<MedicationTier> = listOf(ESSENTIAL, PRESCRIPTION, COMPLETE)

        /**
         * Parse a stored string back into a tier, defaulting to [ESSENTIAL]
         * for unknown / legacy tokens (matches the column's SQL default).
         */
        fun fromStorage(value: String?): MedicationTier {
            if (value.isNullOrBlank()) return ESSENTIAL
            return try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                ESSENTIAL
            }
        }
    }
}
