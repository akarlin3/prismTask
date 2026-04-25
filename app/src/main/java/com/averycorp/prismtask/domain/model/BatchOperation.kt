package com.averycorp.prismtask.domain.model

/**
 * Entity surface a batch mutation can target. The batch path crosses entity
 * types deliberately — a single user command ("cancel everything Friday")
 * may touch tasks, habits, and projects in the same transaction. Each
 * `BatchUndoLogEntry` row pins itself to one entity via this enum.
 */
enum class BatchEntityType {
    TASK,
    HABIT,
    PROJECT,
    MEDICATION
}

/**
 * Per-row mutation kind. Used for diff-preview color coding (reschedule =
 * amber, cancel/delete = red, complete = green, tag change = blue,
 * state change = teal), for the undo path's per-mutation reverse logic,
 * and for analytics.
 *
 * Not every (entity, mutation) pair is valid — e.g. only tasks support
 * `PROJECT_MOVE`, only medications support `STATE_CHANGE`. Validation lives
 * in the batch resolver, not here.
 *
 * `STATE_CHANGE` is currently medication-only — it carries a `tier` field
 * in `proposed_new_values` and writes a `MedicationTierStateEntity` row
 * with `tier_source = "user_set"`. The verb is named generically so
 * future entity-level state overrides can reuse it.
 */
enum class BatchMutationType {
    RESCHEDULE,
    DELETE,
    COMPLETE,
    SKIP,
    PRIORITY_CHANGE,
    TAG_CHANGE,
    PROJECT_MOVE,
    ARCHIVE,
    STATE_CHANGE
}
