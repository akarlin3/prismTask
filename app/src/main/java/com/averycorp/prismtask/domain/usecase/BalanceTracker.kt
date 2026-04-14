package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.LifeCategory
import java.util.Calendar
import java.util.TimeZone

/**
 * Snapshot of the user's current Work-Life Balance state.
 *
 * @property currentRatios How the last 7 days of tasks are distributed across categories.
 *                         Values are normalized 0.0..1.0 and sum to 1.0 unless [totalTracked] == 0.
 * @property rollingRatios Same computation over the past 28 days (4-week average).
 * @property targetRatios The user's configured target for each category.
 * @property isOverloaded True when WORK ratio exceeds `workTarget + overloadThreshold`.
 * @property dominantCategory The category with the highest current ratio
 *                            (or [LifeCategory.UNCATEGORIZED] when there is no data).
 * @property totalTracked The number of tracked tasks contributing to [currentRatios].
 */
data class BalanceState(
    val currentRatios: Map<LifeCategory, Float>,
    val rollingRatios: Map<LifeCategory, Float>,
    val targetRatios: Map<LifeCategory, Float>,
    val isOverloaded: Boolean,
    val dominantCategory: LifeCategory,
    val totalTracked: Int
) {
    companion object {
        val EMPTY = BalanceState(
            currentRatios = LifeCategory.TRACKED.associateWith { 0f },
            rollingRatios = LifeCategory.TRACKED.associateWith { 0f },
            targetRatios = LifeCategory.TRACKED.associateWith { 0.25f },
            isOverloaded = false,
            dominantCategory = LifeCategory.UNCATEGORIZED,
            totalTracked = 0
        )
    }
}

/**
 * Configuration for [BalanceTracker]. Percentages are stored as 0..100 ints in
 * DataStore but exposed as 0f..1f floats here. [overloadThreshold] is an
 * additive buffer in the same 0f..1f units (0.10 = 10 percentage points).
 */
data class BalanceConfig(
    val workTarget: Float = 0.40f,
    val personalTarget: Float = 0.25f,
    val selfCareTarget: Float = 0.20f,
    val healthTarget: Float = 0.15f,
    val overloadThreshold: Float = 0.10f
) {
    fun asMap(): Map<LifeCategory, Float> = mapOf(
        LifeCategory.WORK to workTarget,
        LifeCategory.PERSONAL to personalTarget,
        LifeCategory.SELF_CARE to selfCareTarget,
        LifeCategory.HEALTH to healthTarget
    )

    /** Whether the targets form a valid distribution (sums to ~1.0, allowing rounding). */
    fun isValid(): Boolean {
        val sum = workTarget + personalTarget + selfCareTarget + healthTarget
        return kotlin.math.abs(sum - 1f) < 0.01f
    }
}

/**
 * Pure-function balance computations used by the Today screen balance bar,
 * weekly report, and burnout scorer.
 *
 * The tracker intentionally takes a list of [TaskEntity] and a "now" long so it
 * is trivial to unit-test deterministically. Repositories wire it up with
 * `Flow<List<TaskEntity>>` combine operators.
 */
class BalanceTracker {
    /** Compute a [BalanceState] from a pool of tasks. */
    fun compute(
        allTasks: List<TaskEntity>,
        config: BalanceConfig,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): BalanceState {
        val weekCutoff = cutoff(now, days = 7, timeZone = timeZone)
        val monthCutoff = cutoff(now, days = 28, timeZone = timeZone)

        val current = computeRatios(allTasks, weekCutoff)
        val rolling = computeRatios(allTasks, monthCutoff)
        val total = countTracked(allTasks, weekCutoff)

        val workRatio = current[LifeCategory.WORK] ?: 0f
        val overloaded = total > 0 && workRatio > (config.workTarget + config.overloadThreshold)

        val dominant = if (total == 0) {
            LifeCategory.UNCATEGORIZED
        } else {
            current.maxByOrNull { it.value }?.key ?: LifeCategory.UNCATEGORIZED
        }

        return BalanceState(
            currentRatios = current,
            rollingRatios = rolling,
            targetRatios = config.asMap(),
            isOverloaded = overloaded,
            dominantCategory = dominant,
            totalTracked = total
        )
    }

    private fun computeRatios(
        tasks: List<TaskEntity>,
        cutoff: Long
    ): Map<LifeCategory, Float> {
        val counts = LifeCategory.TRACKED.associateWith { 0 }.toMutableMap()
        var total = 0
        for (t in tasks) {
            val ts = timestampFor(t)
            if (ts < cutoff) continue
            val cat = LifeCategory.fromStorage(t.lifeCategory)
            if (cat == LifeCategory.UNCATEGORIZED) continue
            counts[cat] = (counts[cat] ?: 0) + 1
            total++
        }
        if (total == 0) return LifeCategory.TRACKED.associateWith { 0f }
        return counts.mapValues { (_, count) -> count.toFloat() / total.toFloat() }
    }

    private fun countTracked(tasks: List<TaskEntity>, cutoff: Long): Int {
        var total = 0
        for (t in tasks) {
            val ts = timestampFor(t)
            if (ts < cutoff) continue
            val cat = LifeCategory.fromStorage(t.lifeCategory)
            if (cat == LifeCategory.UNCATEGORIZED) continue
            total++
        }
        return total
    }

    /**
     * Choose the most relevant timestamp for a task when deciding whether
     * it falls into the balance window:
     *  - Completed tasks use `completedAt`.
     *  - Otherwise, `dueDate` if set, else `createdAt`.
     */
    private fun timestampFor(task: TaskEntity): Long = task.completedAt ?: task.dueDate ?: task.createdAt

    private fun cutoff(now: Long, days: Int, timeZone: TimeZone): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        return cal.timeInMillis
    }
}
