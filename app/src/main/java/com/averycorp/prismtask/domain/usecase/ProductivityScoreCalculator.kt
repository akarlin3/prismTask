package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.BestWorstDay
import com.averycorp.prismtask.domain.model.DailyScore
import com.averycorp.prismtask.domain.model.ProductivityScoreResponse
import com.averycorp.prismtask.domain.model.ProductivityTrend
import com.averycorp.prismtask.domain.model.ScoreBreakdown
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily productivity score calculator. Mirrors
 * `backend/app/services/analytics.py::compute_daily_productivity_scores`
 * algorithm — same weights, same default-100 fallback for empty buckets,
 * same trend split-half-average rule (>3 / <-3) — so the score and the
 * best/worst-day callouts stay numerically comparable to the web display.
 *
 * Estimation accuracy is hard-coded to 100 today because Android does not
 * track per-task `actualDuration` in Room. When time-tracking lands, swap
 * the default for a real implementation (per-task variance avg).
 */
@Singleton
class ProductivityScoreCalculator @Inject constructor() {

    fun compute(
        startDate: LocalDate,
        endDate: LocalDate,
        zone: ZoneId,
        tasks: List<TaskEntity>,
        activeHabitsCount: Int,
        habitCompletions: List<HabitCompletionEntity>,
    ): ProductivityScoreResponse {
        require(!startDate.isAfter(endDate)) {
            "startDate ($startDate) must be on or before endDate ($endDate)"
        }

        val tasksByDueDate = tasks
            .filter { it.dueDate != null }
            .groupBy { Instant.ofEpochMilli(it.dueDate!!).atZone(zone).toLocalDate() }
        val tasksByCompletedDate = tasks
            .filter { it.isCompleted && it.completedAt != null }
            .groupBy { Instant.ofEpochMilli(it.completedAt!!).atZone(zone).toLocalDate() }
        val habitCompletionsByDate = habitCompletions
            .groupBy { Instant.ofEpochMilli(it.completedDate).atZone(zone).toLocalDate() }

        val scores = mutableListOf<DailyScore>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            val dueOnDay = tasksByDueDate[cursor].orEmpty()
            val completedOnDay = tasksByCompletedDate[cursor].orEmpty()
            val habitCompletionsOnDay = habitCompletionsByDate[cursor].orEmpty()

            val taskCompletionRate = computeTaskCompletionRate(dueOnDay)
            val onTimeRate = computeOnTimeRate(completedOnDay, cursor, zone)
            val habitRate = computeHabitRate(habitCompletionsOnDay, activeHabitsCount)
            val estimationAccuracy = DEFAULT_ESTIMATION_ACCURACY

            val score = (
                taskCompletionRate * WEIGHT_TASK_COMPLETION +
                    onTimeRate * WEIGHT_ON_TIME +
                    habitRate * WEIGHT_HABIT_COMPLETION +
                    estimationAccuracy * WEIGHT_ESTIMATION_ACCURACY
                ).coerceIn(0.0, 100.0)

            scores.add(
                DailyScore(
                    date = cursor,
                    score = score.round1(),
                    breakdown = ScoreBreakdown(
                        taskCompletion = taskCompletionRate.round1(),
                        onTime = onTimeRate.round1(),
                        habitCompletion = habitRate.round1(),
                        estimationAccuracy = estimationAccuracy.round1(),
                    ),
                ),
            )
            cursor = cursor.plusDays(1)
        }

        val average = if (scores.isEmpty()) {
            0.0
        } else {
            scores.sumOf { it.score } / scores.size
        }

        val best = scores.maxByOrNull { it.score }?.let { BestWorstDay(it.date, it.score) }
        val worst = scores.minByOrNull { it.score }?.let { BestWorstDay(it.date, it.score) }

        return ProductivityScoreResponse(
            scores = scores,
            averageScore = average.round1(),
            trend = determineTrend(scores.map { it.score }),
            bestDay = best,
            worstDay = worst,
        )
    }

    /**
     * Backend parity: counts isCompleted regardless of completion date — a task
     * due Mon and completed Wed still raises Mon's task-completion rate.
     */
    private fun computeTaskCompletionRate(dueOnDay: List<TaskEntity>): Double {
        if (dueOnDay.isEmpty()) return DEFAULT_RATE
        val completedDue = dueOnDay.count { it.isCompleted }
        return (completedDue.toDouble() / dueOnDay.size * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Backend parity: tasks with no due date count as NOT on time (the
     * backend's `due_date IS NOT NULL` filter excludes them from the on-time
     * numerator while still counting them in the denominator via the outer
     * "completed on day" pool).
     */
    private fun computeOnTimeRate(
        completedOnDay: List<TaskEntity>,
        day: LocalDate,
        zone: ZoneId,
    ): Double {
        if (completedOnDay.isEmpty()) return DEFAULT_RATE
        val onTime = completedOnDay.count { task ->
            val due = task.dueDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            due != null && !day.isAfter(due)
        }
        return (onTime.toDouble() / completedOnDay.size * 100.0).coerceIn(0.0, 100.0)
    }

    private fun computeHabitRate(
        completionsOnDay: List<HabitCompletionEntity>,
        activeHabitsCount: Int,
    ): Double {
        if (activeHabitsCount <= 0) return DEFAULT_RATE
        val distinctHabitsCompleted = completionsOnDay.map { it.habitId }.toSet().size
        return (distinctHabitsCompleted.toDouble() / activeHabitsCount * 100.0).coerceIn(0.0, 100.0)
    }

    companion object {
        const val WEIGHT_TASK_COMPLETION = 0.40
        const val WEIGHT_ON_TIME = 0.25
        const val WEIGHT_HABIT_COMPLETION = 0.20
        const val WEIGHT_ESTIMATION_ACCURACY = 0.15
        const val TREND_THRESHOLD = 3.0
        const val DEFAULT_RATE = 100.0
        const val DEFAULT_ESTIMATION_ACCURACY = 100.0

        fun determineTrend(scores: List<Double>): ProductivityTrend {
            if (scores.size < 2) return ProductivityTrend.STABLE
            val mid = scores.size / 2
            val firstHalf = scores.take(mid)
            val secondHalf = scores.drop(mid)
            val firstAvg = firstHalf.average()
            val secondAvg = secondHalf.average()
            val diff = secondAvg - firstAvg
            return when {
                diff > TREND_THRESHOLD -> ProductivityTrend.IMPROVING
                diff < -TREND_THRESHOLD -> ProductivityTrend.DECLINING
                else -> ProductivityTrend.STABLE
            }
        }

        private fun Double.round1(): Double = Math.round(this * 10.0) / 10.0
    }
}
