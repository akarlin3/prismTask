package com.averycorp.prismtask.domain.model

/**
 * External anchor variant attached to a project or phase. Storage is a
 * single `anchor_json` TEXT column on `external_anchors`; round-trip
 * goes through [com.averycorp.prismtask.domain.anchor.ExternalAnchorJsonAdapter]
 * (mirrors `AutomationJsonAdapter`'s polymorphic Gson pattern).
 *
 * The variants cover three distinct anchor semantics:
 *
 *  - [CalendarDeadline] — a hard date pin (e.g. "App Store review by
 *    Aug 1"). [epochMs] is UTC epoch millis.
 *  - [NumericThreshold] — a measurable target ("MAU > 10k"). [metric]
 *    is a free-form key callers interpret; [op] is one of
 *    [NumericOp]; [value] is a Double.
 *  - [BooleanGate] — an external boolean toggle ("Phase F kickoff =
 *    true"). [gateKey] is a free-form key, [expectedState] is the
 *    state that satisfies the anchor.
 *
 * Each variant carries [type] purely as a discriminator string for the
 * Gson adapter; it is not meant to be set by callers (sealed class
 * bias).
 */
sealed class ExternalAnchor {
    abstract val type: String

    data class CalendarDeadline(val epochMs: Long) : ExternalAnchor() {
        override val type: String = TYPE
        companion object {
            const val TYPE = "calendar.deadline"
        }
    }

    data class NumericThreshold(
        val metric: String,
        val op: NumericOp,
        val value: Double
    ) : ExternalAnchor() {
        override val type: String = TYPE
        companion object {
            const val TYPE = "numeric.threshold"
        }
    }

    data class BooleanGate(
        val gateKey: String,
        val expectedState: Boolean
    ) : ExternalAnchor() {
        override val type: String = TYPE
        companion object {
            const val TYPE = "boolean.gate"
        }
    }
}

enum class NumericOp { GTE, GT, LTE, LT, EQ, NEQ }

enum class RiskLevel { LOW, MEDIUM, HIGH }
