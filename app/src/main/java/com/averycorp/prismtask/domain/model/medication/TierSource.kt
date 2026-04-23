package com.averycorp.prismtask.domain.model.medication

/**
 * Records whether a `medication_tier_states` row was derived from medication
 * completion state (`COMPUTED`) or set by the user via an explicit slot
 * tap (`USER_SET`). User-set rows sticky-override the computed value until
 * the user clears them — see `MedicationTierComputer` for the precedence
 * rules.
 */
enum class TierSource {
    COMPUTED,
    USER_SET;

    fun toStorage(): String = name.lowercase()

    companion object {
        fun fromStorage(value: String?): TierSource {
            if (value.isNullOrBlank()) return COMPUTED
            return try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                COMPUTED
            }
        }
    }
}
