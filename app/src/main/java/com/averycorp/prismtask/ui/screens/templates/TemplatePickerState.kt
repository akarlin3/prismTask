package com.averycorp.prismtask.ui.screens.templates

import com.averycorp.prismtask.domain.model.SelfCareRoutines

/**
 * Selection state for the shared template picker (onboarding + Settings "Browse
 * templates"). Empty values everywhere = "seed nothing" which is the default
 * for a new user: templates are opt-in, not opt-out.
 *
 * Self-care / bedtime / housework are represented by a tier choice plus an
 * optional per-step override. When [morningCustomStepIds] is `null` the
 * effective step set is derived from the tier (cumulative via
 * [SelfCareRoutines.tierIncludes]); when non-null the user has customized the
 * selection and the tier is informational only.
 */
data class TemplateSelections(
    val musicIds: Set<String> = emptySet(),
    val flexIds: Set<String> = emptySet(),
    val morningTier: String? = null,
    val morningCustomStepIds: Set<String>? = null,
    val bedtimeTier: String? = null,
    val bedtimeCustomStepIds: Set<String>? = null,
    val houseworkTier: String? = null,
    val houseworkCustomStepIds: Set<String>? = null
) {
    fun tierFor(routineType: String): String? = when (routineType) {
        "morning" -> morningTier
        "bedtime" -> bedtimeTier
        "housework" -> houseworkTier
        else -> null
    }

    fun customStepIdsFor(routineType: String): Set<String>? = when (routineType) {
        "morning" -> morningCustomStepIds
        "bedtime" -> bedtimeCustomStepIds
        "housework" -> houseworkCustomStepIds
        else -> null
    }

    /**
     * Which step ids are currently slated for seeding for [routineType]. When
     * the user has customized, returns the explicit set; otherwise expands
     * the tier via cumulative semantics. Empty set = nothing will be seeded.
     */
    fun effectiveStepIds(routineType: String): Set<String> {
        val custom = customStepIdsFor(routineType)
        if (custom != null) return custom
        val tier = tierFor(routineType) ?: return emptySet()
        val tierOrder = SelfCareRoutines.getTierOrder(routineType)
        if (tier !in tierOrder) return emptySet()
        return SelfCareRoutines
            .getSteps(routineType)
            .filter { SelfCareRoutines.tierIncludes(tierOrder, tier, it.tier) }
            .map { it.id }
            .toSet()
    }

    fun withMusicToggled(id: String): TemplateSelections =
        copy(musicIds = if (id in musicIds) musicIds - id else musicIds + id)

    fun withFlexToggled(id: String): TemplateSelections =
        copy(flexIds = if (id in flexIds) flexIds - id else flexIds + id)

    /**
     * Sets the tier for [routineType]. Passing `null` clears the tier. Either
     * way the custom-step override is reset to `null` so the tier change is
     * visible; users who want per-step selection re-enable customize mode.
     */
    fun withTier(routineType: String, tier: String?): TemplateSelections = when (routineType) {
        "morning" -> copy(morningTier = tier, morningCustomStepIds = null)
        "bedtime" -> copy(bedtimeTier = tier, bedtimeCustomStepIds = null)
        "housework" -> copy(houseworkTier = tier, houseworkCustomStepIds = null)
        else -> this
    }

    /**
     * Toggles [stepId] for [routineType]. If the user hasn't customized yet,
     * we materialize the tier-derived set first so the toggle applies to a
     * concrete starting point.
     */
    fun withStepToggled(routineType: String, stepId: String): TemplateSelections {
        val current = customStepIdsFor(routineType) ?: effectiveStepIds(routineType)
        val next = if (stepId in current) current - stepId else current + stepId
        return when (routineType) {
            "morning" -> copy(morningCustomStepIds = next)
            "bedtime" -> copy(bedtimeCustomStepIds = next)
            "housework" -> copy(houseworkCustomStepIds = next)
            else -> this
        }
    }
}
