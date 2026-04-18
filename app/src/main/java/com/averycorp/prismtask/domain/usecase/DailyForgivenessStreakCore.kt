package com.averycorp.prismtask.domain.usecase

import java.time.LocalDate

/**
 * Pure, domain-agnostic core of the daily forgiveness-first streak walk.
 *
 * Takes a plain `Set<LocalDate>` of "activity" days — days on which the
 * underlying entity (habit, project, whatever) did enough to count as met —
 * and runs the same walk that powered [StreakCalculator.calculateResilientDailyStreak].
 *
 * Extracted in v1.4.0 so the Projects feature can compute its own streak
 * (activity = task completions on the project + milestone completions)
 * without importing habit entities. [StreakCalculator] delegates its daily
 * path here so habits and projects share one implementation.
 *
 * NOT extracted yet (by design): weekly / fortnightly / monthly /
 * bimonthly / quarterly variants. Those still live in [StreakCalculator]
 * because no project-side feature needs them in v1. The day a feature does,
 * the same extraction pattern can migrate them one at a time.
 */
object DailyForgivenessStreakCore {

    private const val SAFETY_CAP = 10_000

    /**
     * @param activityDates Dates the entity met its daily target. This set is
     *   assumed to already have applied any per-entity rules like "habit
     *   target frequency ≥ N" or "task completion counts"; the core just
     *   asks "is this day met?".
     * @param today The reference "now" date — the walk starts here.
     * @param config Forgiveness window + allowed misses. When disabled the
     *   result collapses to a strict streak in both slots and no forgiven
     *   dates are returned.
     */
    fun calculate(
        activityDates: Set<LocalDate>,
        today: LocalDate = LocalDate.now(),
        config: ForgivenessConfig = ForgivenessConfig.DEFAULT
    ): StreakResult {
        val strict = strictWalk(activityDates, today)
        if (!config.enabled) {
            return StreakResult(
                strictStreak = strict,
                resilientStreak = strict,
                missesInWindow = 0,
                gracePeriodRemaining = 0,
                forgivenDates = emptyList()
            )
        }

        val allowed = config.allowedMisses.coerceAtLeast(0)
        val window = config.gracePeriodDays.coerceAtLeast(1)

        // Mid-day rule: don't penalize the user for not logging yet today.
        // If today isn't met, drop the cursor to yesterday before starting.
        val start = if (today in activityDates) today else today.minusDays(1)

        // Hard reset: if the starting cursor itself is a miss (today AND
        // yesterday both missed), the current run has already broken.
        // Return resilient=0 regardless of how long the historical run was.
        if (start !in activityDates) {
            return StreakResult(
                strictStreak = strict,
                resilientStreak = 0,
                missesInWindow = 0,
                gracePeriodRemaining = allowed,
                forgivenDates = emptyList()
            )
        }

        // Don't walk past the earliest known activity — counting pre-history
        // days as "misses" would penalize the user for before the entity
        // existed.
        val earliest = activityDates.minOrNull()

        var cursor = start
        var metDays = 0
        val missDates = mutableListOf<LocalDate>()

        while (true) {
            if (cursor in activityDates) {
                metDays++
            } else {
                // A miss is tolerable iff the rolling window (anchored at
                // the cursor, extending forward) already holds fewer than
                // [allowed] misses.
                val windowEnd = cursor.plusDays((window - 1).toLong())
                val priorMissesInWindow = missDates.count { !it.isBefore(cursor) && !it.isAfter(windowEnd) }
                if (priorMissesInWindow >= allowed) break
                missDates.add(cursor)
            }
            if (metDays + missDates.size > SAFETY_CAP) break
            cursor = cursor.minusDays(1)
            if (earliest != null && cursor.isBefore(earliest)) break
        }

        val resilient = metDays + missDates.size
        val remaining = (allowed - missDates.size).coerceAtLeast(0)
        return StreakResult(
            strictStreak = strict,
            resilientStreak = resilient,
            missesInWindow = missDates.size,
            gracePeriodRemaining = remaining,
            forgivenDates = missDates.toList()
        )
    }

    /**
     * Classic "consecutive met days, break on first miss" walk. Matches
     * [StreakCalculator.calculateCurrentStreak]'s daily path with the
     * default `maxMissedDays = 1`.
     */
    private fun strictWalk(activityDates: Set<LocalDate>, today: LocalDate): Int {
        if (activityDates.isEmpty()) return 0
        var cursor = if (today in activityDates) today else today.minusDays(1)
        var streak = 0
        while (cursor in activityDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
