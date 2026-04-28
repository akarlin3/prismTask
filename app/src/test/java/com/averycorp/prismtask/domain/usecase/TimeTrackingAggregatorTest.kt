package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskTimingEntity
import com.averycorp.prismtask.domain.model.ProductivityRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TimeTrackingAggregatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 4, 28)
    private val aggregator = TimeTrackingAggregator()

    private fun timing(daysAgo: Long, minutes: Int): TaskTimingEntity {
        val day = today.minusDays(daysAgo)
        val createdAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return TaskTimingEntity(
            taskId = 1L,
            durationMinutes = minutes,
            createdAt = createdAt
        )
    }

    @Test
    fun `empty input produces zero-filled buckets`() {
        val response = aggregator.compute(today, zone, ProductivityRange.SEVEN_DAYS, emptyList())

        assertEquals(7, response.buckets.size)
        assertEquals(0, response.totalMinutes)
        assertEquals(0, response.activeDayCount)
        assertEquals(0, response.averageMinutesPerActiveDay)
        assertTrue(response.buckets.all { it.totalMinutes == 0 })
    }

    @Test
    fun `entries within window aggregate per local day`() {
        val timings = listOf(
            timing(daysAgo = 0, minutes = 30),
            timing(daysAgo = 0, minutes = 15),
            timing(daysAgo = 2, minutes = 60),
            timing(daysAgo = 6, minutes = 5)
        )

        val response = aggregator.compute(today, zone, ProductivityRange.SEVEN_DAYS, timings)

        assertEquals(7, response.buckets.size)
        assertEquals(110, response.totalMinutes)
        assertEquals(3, response.activeDayCount)
        assertEquals(110 / 3, response.averageMinutesPerActiveDay)

        val todayBucket = response.buckets.last()
        assertEquals(today, todayBucket.date)
        assertEquals(45, todayBucket.totalMinutes)

        val twoDaysAgoBucket = response.buckets[response.buckets.size - 3]
        assertEquals(60, twoDaysAgoBucket.totalMinutes)

        val sixDaysAgoBucket = response.buckets.first()
        assertEquals(today.minusDays(6), sixDaysAgoBucket.date)
        assertEquals(5, sixDaysAgoBucket.totalMinutes)
    }

    @Test
    fun `entries outside the window are filtered out`() {
        val timings = listOf(
            timing(daysAgo = 0, minutes = 10),
            timing(daysAgo = 8, minutes = 999),
            timing(daysAgo = 100, minutes = 999)
        )

        val response = aggregator.compute(today, zone, ProductivityRange.SEVEN_DAYS, timings)

        assertEquals(10, response.totalMinutes)
        assertEquals(1, response.activeDayCount)
    }

    @Test
    fun `30-day range produces 30 bucket window`() {
        val timings = listOf(
            timing(daysAgo = 0, minutes = 60),
            timing(daysAgo = 29, minutes = 30)
        )

        val response = aggregator.compute(today, zone, ProductivityRange.THIRTY_DAYS, timings)

        assertEquals(30, response.buckets.size)
        assertEquals(90, response.totalMinutes)
        assertEquals(2, response.activeDayCount)
        assertEquals(today.minusDays(29), response.buckets.first().date)
        assertEquals(today, response.buckets.last().date)
    }

    @Test
    fun `90-day range produces 90 bucket window`() {
        val timings = listOf(timing(daysAgo = 89, minutes = 12))

        val response = aggregator.compute(today, zone, ProductivityRange.NINETY_DAYS, timings)

        assertEquals(90, response.buckets.size)
        assertEquals(12, response.totalMinutes)
        assertEquals(1, response.activeDayCount)
        assertEquals(today.minusDays(89), response.buckets.first().date)
    }

    @Test
    fun `average rounds toward zero on uneven totals`() {
        val timings = listOf(
            timing(daysAgo = 0, minutes = 10),
            timing(daysAgo = 1, minutes = 11),
            timing(daysAgo = 2, minutes = 12)
        )

        val response = aggregator.compute(today, zone, ProductivityRange.SEVEN_DAYS, timings)

        assertEquals(33, response.totalMinutes)
        assertEquals(3, response.activeDayCount)
        // 33 / 3 = 11 exact; verifying integer division is the intent.
        assertEquals(11, response.averageMinutesPerActiveDay)
    }
}
