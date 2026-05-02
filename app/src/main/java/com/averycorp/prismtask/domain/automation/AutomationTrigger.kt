package com.averycorp.prismtask.domain.automation

/**
 * What causes a rule to fire. One of four kinds (per § A6 of
 * `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md`):
 *
 *  - [EntityEvent]   — fires when an entity-CRUD event occurs (e.g. a task is created).
 *  - [TimeOfDay]     — fires at a wall-clock time once per day.
 *  - [Manual]        — fires only via "Run Now" from the rule list.
 *  - [Composed]      — fires when another rule fires.
 *
 * Discriminated by [type] for JSON round-trip via [AutomationJsonAdapter].
 */
sealed class AutomationTrigger(val type: String) {

    /**
     * Subscribes to a specific [AutomationEvent] kind. [eventKind] is the
     * `simpleName` of the event sealed type (e.g. "TaskCreated",
     * "HabitCompleted"); see [AutomationEvent] for the canonical list.
     */
    data class EntityEvent(
        val eventKind: String
    ) : AutomationTrigger(TYPE)
    {
        companion object { const val TYPE = "ENTITY_EVENT" }
    }

    /**
     * Wall-clock trigger. [hour] / [minute] are 24h local time. The engine's
     * [AutomationTimeTickWorker] enqueues a [AutomationEvent.TimeTick] at
     * 5-minute granularity; matching rules fire when their target time
     * falls inside the elapsed window.
     */
    data class TimeOfDay(
        val hour: Int,
        val minute: Int
    ) : AutomationTrigger(TYPE)
    {
        companion object { const val TYPE = "TIME_OF_DAY" }
    }

    /** No automatic firing — must be invoked via "Run Now" from the UI. */
    object Manual : AutomationTrigger(TYPE) { const val TYPE = "MANUAL" }

    /**
     * Composed trigger — fires when [parentRuleId] fires. Cycle detection
     * lives in [AutomationEngine]; a chain of length 5+ aborts.
     */
    data class Composed(
        val parentRuleId: Long
    ) : AutomationTrigger(TYPE)
    {
        companion object { const val TYPE = "COMPOSED" }
    }
}
