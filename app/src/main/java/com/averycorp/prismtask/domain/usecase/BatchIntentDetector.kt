package com.averycorp.prismtask.domain.usecase

/**
 * Heuristic detector for batch-style commands typed into QuickAddBar.
 *
 * The single-task NLP path is the default — false positives here would
 * trap normal users in the heavier batch flow. The detector errs on the
 * side of NOT matching: a batch command must show **two distinct signal
 * categories** out of the four below before we route it to the batch
 * endpoint.
 *
 * The gate is "any 2 of 4 categories" — bulk-verb-with-plural is NOT
 * required, just one of four eligible categories. (The earlier kdoc on
 * this class claimed "bulk verb + a quantifier or time range," but the
 * implementation has always been broader; that mismatch is fixed here
 * by weakening the kdoc rather than tightening the gate, since the
 * existing `everyHabit_plusTimeRange_detects` test deliberately locks
 * in the broader behavior.)
 *
 * Signals:
 *   - **Quantifier**: any of `all`, `everything`, `every`, `each`
 *   - **Tag filter**: `tagged X` or `#X`, where `#X` must be at the
 *     start of the input or preceded by whitespace, and `X` must not
 *     be purely numeric (`#1`, `#42` are reference markers, not tags).
 *     Mixed tokens like `#2024-q1` still match.
 *   - **Time range**: any of `today`, `tonight`, `tomorrow`,
 *     `this week`, `next week`, `the weekend`, a weekday name
 *   - **Bulk verb + entity plural**: `cancel/clear/move/reschedule/
 *     delete/complete/skip/archive/mark` followed by an entity plural
 *     (`tasks`, `habits`, `medications`, …)
 *
 * **Carve-outs** (return NotABatch even when ≥2 signals fire):
 *   - **Negation prefix** — input beginning with `don't`, `do not`,
 *     `please don't`, or `never` is the user telling us NOT to do
 *     something; the batch preview is the wrong destination. Greedy on
 *     prefix only — `don't worry, complete all tasks today` would also
 *     suppress (known limitation; defer smarter parse to follow-up).
 *   - **Recurrence pattern** — when the only signals are QUANTIFIER +
 *     TIME_RANGE and the input matches `(every|each) <day-noun|weekday>`
 *     (e.g. `every monday at 8am`, `each friday morning`), the input
 *     is recurring single-task creation, not a batch command.
 *   - **Description-vs-command** — when the only signals are
 *     QUANTIFIER + TIME_RANGE and no bulk verb appears in the input
 *     (e.g. `all my tasks for today`), the user is describing rather
 *     than commanding. Defers to the single-task path.
 *
 * Tokenization splits on whitespace plus common sentence-ending
 * punctuation (`,.;!?`) so trailing periods/commas don't defeat
 * dictionary lookups (`delete tasks today.` no longer drops TIME_RANGE).
 * Quotes and hyphens are NOT split, so quoted input
 * (`"complete all tasks today"`) currently stays NOT-BATCH —
 * documented as a known limitation.
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
        "archive",
        "mark"
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

    /**
     * Tag-filter phrasing: `tagged X` or `#X`. The `#X` branch requires
     * `#` to be at start-of-input or preceded by whitespace (kills
     * mid-token `C#programming` false matches), and the lookahead
     * `(?!\d+(?:\s|$))` excludes pure-numeric tags (`#1`, `#42`) which
     * are reference markers, not tags. Mixed tokens like `#2024-q1`
     * still match because `\d+` is followed by `-`, not whitespace/EOL.
     */
    private val tagFilterRegex = Regex(
        """(?:(?:^|\s)#(?!\d+(?:\s|$))\S+|\btagged\s+\S+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Recurrence pattern (`every monday`, `each friday morning`). Used
     * by the recurrence carve-out below to keep recurring single-task
     * creation off the batch path; the description-vs-command carve-out
     * is its companion for non-recurrence {QUANT, TIME_RANGE} inputs.
     * Includes calendar nouns (`day`, `week`, `month`, `year`, `hour`,
     * `other`) and parts-of-day (`morning`, `evening`, `night`)
     * commonly used after `every`/`each`.
     *
     * IMPORTANT: do NOT add singular entity nouns (`habit`, `task`,
     * `medication`, `med`, `project`, `item`) to the noun group below.
     * They're the singulars of `entityPlurals`; adding them would
     * silently break `everyHabit_plusTimeRange_detects` — `mark every
     * habit complete for today` routes to BATCH today via the
     * {QUANT, TIME_RANGE} signal mix, and that test deliberately locks
     * the broader gate semantics. If a future edit needs an entity noun
     * to behave as a recurrence trigger, change the carve-out instead —
     * don't expand this regex.
     */
    private val recurrencePatternRegex = Regex(
        """\b(every|each)\s+""" +
            """(monday|tuesday|wednesday|thursday|friday|saturday|sunday|""" +
            """day|week|month|year|morning|evening|night|hour|other)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Negation prefix that suppresses batch routing entirely. */
    private val negationPrefixRegex = Regex(
        """^\s*(?:please\s+)?(?:don'?t|do\s+not|never)\b""",
        RegexOption.IGNORE_CASE
    )

    fun detect(rawText: String): Result {
        val text = rawText.trim()
        if (text.isEmpty()) return Result.NotABatch

        // Negation pre-check — explicit "don't / do not / never" prefixes
        // mean the user is telling us NOT to do something. Defer to the
        // single-task path; batch preview is the wrong destination.
        if (negationPrefixRegex.containsMatchIn(text)) return Result.NotABatch

        val lower = text.lowercase()

        val signals = mutableSetOf<Signal>()

        // Tokenize on whitespace + common sentence punctuation so trailing
        // periods/commas don't defeat dictionary lookups.
        val tokens = lower.split(Regex("[\\s,.;!?]+"))

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

        // Recurrence carve-out — `every monday`-style inputs trip
        // QUANT+TIME_RANGE but are recurring single-task creation, not
        // batch operations. Narrowly scoped: only when those are the
        // ONLY signals (a third signal like BULK_VERB_AND_PLURAL means
        // the user really is operating on a collection).
        if (signals == setOf(Signal.QUANTIFIER, Signal.TIME_RANGE) &&
            recurrencePatternRegex.containsMatchIn(text)
        ) {
            return Result.NotABatch
        }

        // Description-vs-command carve-out — when signals are exactly
        // QUANT+TIME_RANGE and no bulk verb appears anywhere in the
        // input (e.g. `all my tasks for today`), the user is describing
        // their tasks rather than commanding a batch operation. Inputs
        // with a bulk-verb token (`mark every habit complete for today`)
        // keep their existing routing because `mark`/`complete` are in
        // `bulkVerbs`, so the `tokens.none { ... }` check fails and
        // this skips. Order vs. recurrence carve-out is safe: both fire
        // only on the same {QUANT, TIME_RANGE} mix and either correctly
        // returns NotABatch.
        if (signals == setOf(Signal.QUANTIFIER, Signal.TIME_RANGE) &&
            tokens.none { it in bulkVerbs }
        ) {
            return Result.NotABatch
        }

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
