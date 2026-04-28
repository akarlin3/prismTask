package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskTimingEntity
import com.averycorp.prismtask.domain.model.DailyTimeBucket
import com.averycorp.prismtask.domain.model.ProductivityRange
import com.averycorp.prismtask.domain.model.TimeTrackingResponse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Buckets a flat list of `TaskTimingEntity` rows into per-day totals over a
 * sliding window ending at `endDate` (inclusive). Mirrors the per-day shape
 * the productivity score chart uses so the chart UI can re-use the same
 * Compose Canvas drawing path.
 *
 * Empty days inside the window get a 0-minute bucket so the chart renders a
 * continuous time axis (no gaps when no time was logged on a given day).
 */
@Singleton
class TimeTrackingAggregator @Inject constructor() {

    /**
     * @param endDate Last day in the window (inclusive). Defaults to "today"
     *   in [zone] at the call site.
     * @param zone Time zone used to bucket [TaskTimingEntity.createdAt] (UTC
     *   millis) into local-day buckets.
     * @param range Window length in days (7/30/90).
     * @param timings All `TaskTimingEntity` rows whose `createdAt` falls
     *   anywhere in `[startDate, endDate]`. Rows outside the window are
     *   filtered defensively.
     */
    fun compute(
        endDate: LocalDate,
        zone: ZoneId,
        range: ProductivityRange,
        timings: List<TaskTimingEntity>
    ): TimeTrackingResponse {
        val startDate = endDate.minusDays((range.days - 1).toLong())

        val perDayMinutes = mutableMapOf<LocalDate, Int>()
        timings.forEach { timing ->
            val day = Instant.ofEpochMilli(timing.createdAt).atZone(zone).toLocalDate()
            if (day < startDate || day > endDate) return@forEach
            perDayMinutes[day] = (perDayMinutes[day] ?: 0) + timing.durationMinutes
        }

        val buckets = (0L until range.days.toLong()).map { offset ->
            val date = startDate.plusDays(offset)
            DailyTimeBucket(
                date = date,
                totalMinutes = perDayMinutes[date] ?: 0
            )
        }

        val totalMinutes = buckets.sumOf { it.totalMinutes }
        val activeDayCount = buckets.count { it.totalMinutes > 0 }
        val averageMinutesPerActiveDay = if (activeDayCount > 0) {
            totalMinutes / activeDayCount
        } else {
            0
        }

        return TimeTrackingResponse(
            buckets = buckets,
            totalMinutes = totalMinutes,
            averageMinutesPerActiveDay = averageMinutesPerActiveDay,
            activeDayCount = activeDayCount,
            range = range
        )
    }
}
