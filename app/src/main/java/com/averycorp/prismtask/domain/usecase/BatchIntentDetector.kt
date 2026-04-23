package com.averycorp.prismtask.domain.usecase

/**
 * Heuristic detector for batch-style commands typed into QuickAddBar.
 *
 * The single-task NLP path is the default — false positives here would
 * trap normal users in the heavier batch flow. The detector errs on the
 * side of NOT matching: a batch command must show two independent
 * signals before we route it to the batch endpoint.
 *
 * Signals:
 *   - **Quantifier**: any of `all`, `everything`, `every`, `each`
 *   - **Tag filter**: `tagged X` or `#X`
 *   - **Time range**: any of `today`, `tonight`, `tomorrow`,
 *     `this week`, `next week`, `the weekend`, a weekday name
 *   - **Bulk verb + entity plural**: `cancel/clear/move/reschedule/delete
 *     /complete/skip` followed by an entity plural
 *     (`tasks`, `habits`, `medications`)
 *
 * Single-task creation usually has zero or one signal; a batch command
 * has the bulk verb + a quantifier or time range. We require **at least
 * two distinct signal categories** — that empirically clears the
 * obvious false positives ("buy groceries today" matches one signal
 * only and stays on the single-task path).
 */
class BatchIntentDetector {
    private val quantifierTokens = setOf(
        "all",
        "everything",
        "every",
        "each"
    )

    /** Bulk verbs strong enough to imply a batch when paired with an
     *  entity plural. The verbs are also valid in single-task commands
     *  ("Cancel groceries"), so we never match on the verb alone. */
    private val bulkVerbs = setOf(
        "cancel",
        "clear",
        "move",
        "reschedule",
        "delete",
        "complete",
        "skip",
        "archive"
    )

    /** Plurals that signal "operating on a collection". */
    private val entityPlurals = setOf(
        "tasks",
        "habits",
        "medications",
        "meds",
        "projects",
        "items"
    )

    /** Time range words that imply a horizon, not a specific instance. */
    private val timeRangeTokens = setOf(
        "today",
        "tonight",
        "tomorrow",
        "monday",
        "tuesday",
        "wednesday",
        "thursday",
        "friday",
        "saturday",
        "sunday",
        "weekend"
    )

    /** Multiword time-range phrases. */
    private val timeRangePhrases = listOf(
        "this week",
        "next week",
        "the weekend",
        "the rest of the day",
        "the rest of the week",
        "this morning",
        "this afternoon",
        "this evening"
    )

    /** Tag-filter phrasing: `tagged X` or `#X`. */
    private val tagFilterRegex = Regex("(?:\\btagged\\s+\\S+|#\\S+)", RegexOption.IGNORE_CASE)

    fun detect(rawText: String): Result {
        val text = rawText.trim()
        if (text.isEmpty()) return Result.NotABatch
        val lower = text.lowercase()

        val signals = mutableSetOf<Signal>()

        // Quantifier.
        val tokens = lower.split(Regex("\\s+"))
        if (tokens.any { it in quantifierTokens }) signals += Signal.QUANTIFIER

        // Time range — single tokens or multi-word phrases.
        if (tokens.any { it in timeRangeTokens }) signals += Signal.TIME_RANGE
        if (timeRangePhrases.any { lower.contains(it) }) signals += Signal.TIME_RANGE

        // Tag filter.
        if (tagFilterRegex.containsMatchIn(text)) signals += Signal.TAG_FILTER

        // Bulk verb + entity plural in the same command.
        val hasBulkVerb = tokens.any { it in bulkVerbs }
        val hasEntityPlural = tokens.any { it in entityPlurals }
        if (hasBulkVerb && hasEntityPlural) signals += Signal.BULK_VERB_AND_PLURAL

        // Two distinct signal categories required — see class kdoc.
        return if (signals.size >= 2) {
            Result.Batch(commandText = text, signals = signals.toSet())
        } else {
            Result.NotABatch
        }
    }

    sealed class Result {
        /** Stays on the single-task NLP path. */
        data object NotABatch : Result()

        /** Route to the batch-parse API + preview screen. */
        data class Batch(val commandText: String, val signals: Set<Signal>) : Result()
    }

    enum class Signal {
        QUANTIFIER,
        TIME_RANGE,
        TAG_FILTER,
        BULK_VERB_AND_PLURAL
    }
}
