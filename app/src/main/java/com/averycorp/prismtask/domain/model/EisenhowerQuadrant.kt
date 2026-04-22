package com.averycorp.prismtask.domain.model

/**
 * Eisenhower urgency/importance quadrant.
 *
 * Persisted on [com.averycorp.prismtask.data.local.entity.TaskEntity.eisenhowerQuadrant]
 * as the legacy `"Q1"`..`"Q4"` string codes — the column predates this enum
 * (migration 47→48) and existing rows + Firestore docs use those codes.
 * [UNCLASSIFIED] maps to a null column value so legacy "not yet categorized"
 * rows continue to read cleanly.
 */
enum class EisenhowerQuadrant(val code: String?) {
    URGENT_IMPORTANT("Q1"),
    NOT_URGENT_IMPORTANT("Q2"),
    URGENT_NOT_IMPORTANT("Q3"),
    NOT_URGENT_NOT_IMPORTANT("Q4"),
    UNCLASSIFIED(null);

    companion object {
        fun fromCode(code: String?): EisenhowerQuadrant = when (code) {
            "Q1" -> URGENT_IMPORTANT
            "Q2" -> NOT_URGENT_IMPORTANT
            "Q3" -> URGENT_NOT_IMPORTANT
            "Q4" -> NOT_URGENT_NOT_IMPORTANT
            else -> UNCLASSIFIED
        }
    }
}
