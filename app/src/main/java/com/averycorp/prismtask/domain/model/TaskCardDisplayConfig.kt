package com.averycorp.prismtask.domain.model

/**
 * User-configurable fields shown on a task card. Serialized as JSON under a
 * single UserPreferencesDataStore key so future fields can be added without
 * a schema change.
 *
 * Added in v1.3.0 (P12).
 */
data class TaskCardDisplayConfig(
    val showDueDate: Boolean = true,
    val showTime: Boolean = true,
    val showPriorityDot: Boolean = true,
    val showProjectBadge: Boolean = true,
    val showTagChips: Boolean = true,
    val maxTagChips: Int = 3,
    val showSubtaskCount: Boolean = true,
    val showRecurrenceIcon: Boolean = true,
    val showUrgencyIndicator: Boolean = false,
    val showNotesPreview: Boolean = false,
    val showCreationDate: Boolean = false,
    val showDurationBadge: Boolean = false,
    val showFlagIndicator: Boolean = true,
    val showTimeTracked: Boolean = false,
    val minimalStyle: Boolean = false
) {
    /**
     * Returns a config with all non-essential fields disabled, matching the
     * "Minimal" card style. Essentials kept: title, due date, priority dot.
     */
    fun toMinimal(): TaskCardDisplayConfig = copy(
        showDueDate = true,
        showTime = false,
        showPriorityDot = true,
        showProjectBadge = false,
        showTagChips = false,
        showSubtaskCount = false,
        showRecurrenceIcon = false,
        showUrgencyIndicator = false,
        showNotesPreview = false,
        showCreationDate = false,
        showDurationBadge = false,
        showFlagIndicator = true,
        showTimeTracked = false,
        minimalStyle = true
    )

    /**
     * Caps [maxTagChips] to 1..5 to match the slider range in the Settings UI.
     */
    fun withClampedTagLimit(): TaskCardDisplayConfig =
        if (maxTagChips in 1..5) this else copy(maxTagChips = maxTagChips.coerceIn(1, 5))
}
