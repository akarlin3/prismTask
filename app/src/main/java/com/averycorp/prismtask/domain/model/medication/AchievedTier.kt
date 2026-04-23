package com.averycorp.prismtask.domain.model.medication

/**
 * The highest [MedicationTier] satisfied for a given `(medication, slot, day)`
 * triple, or [SKIPPED] when the user has explicitly marked the slot as not
 * applicable for that day. Stored on `medication_tier_states.tier`.
 *
 * `SKIPPED` is distinct from "no tier-state row yet" — absence of a row
 * means the day's status is still computed (or the user hasn't interacted
 * with this slot today); a `SKIPPED` row is a deliberate user override.
 */
enum class AchievedTier {
    SKIPPED,
    ESSENTIAL,
    PRESCRIPTION,
    COMPLETE;

    fun toStorage(): String = name.lowercase()

    companion object {
        fun fromStorage(value: String?): AchievedTier {
            if (value.isNullOrBlank()) return SKIPPED
            return try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                SKIPPED
            }
        }

        /** Convert a concrete [MedicationTier] into the matching achieved value. */
        fun from(tier: MedicationTier): AchievedTier = when (tier) {
            MedicationTier.ESSENTIAL -> ESSENTIAL
            MedicationTier.PRESCRIPTION -> PRESCRIPTION
            MedicationTier.COMPLETE -> COMPLETE
        }
    }
}
