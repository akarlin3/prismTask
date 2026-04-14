package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.MedicationRefillEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RefillCalculatorTest {
    private val now = 1_000_000L
    private val oneDay = 24L * 60 * 60 * 1000

    private fun med(
        pillCount: Int = 30,
        pillsPerDose: Int = 1,
        dosesPerDay: Int = 1,
        lastRefill: Long? = now,
        reminderDaysBefore: Int = 3
    ) = MedicationRefillEntity(
        medicationName = "Test",
        pillCount = pillCount,
        pillsPerDose = pillsPerDose,
        dosesPerDay = dosesPerDay,
        lastRefillDate = lastRefill,
        reminderDaysBefore = reminderDaysBefore
    )

    @Test
    fun `30 pills one per day gives 30 days remaining`() {
        val forecast = RefillCalculator.forecast(med(pillCount = 30), now)
        assertEquals(30, forecast.daysRemaining)
    }

    @Test
    fun `60 pills 2 per day gives 30 days remaining`() {
        val forecast = RefillCalculator.forecast(
            med(pillCount = 60, pillsPerDose = 1, dosesPerDay = 2),
            now
        )
        assertEquals(30, forecast.daysRemaining)
    }

    @Test
    fun `60 pills 2 pills per dose 1 dose per day gives 30 days`() {
        val forecast = RefillCalculator.forecast(
            med(pillCount = 60, pillsPerDose = 2, dosesPerDay = 1),
            now
        )
        assertEquals(30, forecast.daysRemaining)
    }

    @Test
    fun `refill date anchors to last refill plus days remaining`() {
        val forecast = RefillCalculator.forecast(med(pillCount = 10), now)
        assertEquals(now + 10 * oneDay, forecast.refillDateMillis)
    }

    @Test
    fun `reminder fires three days before refill by default`() {
        val forecast = RefillCalculator.forecast(med(pillCount = 10), now)
        assertEquals(forecast.refillDateMillis - 3 * oneDay, forecast.reminderDateMillis)
    }

    @Test
    fun `healthy urgency when more than seven days remaining`() {
        val forecast = RefillCalculator.forecast(med(pillCount = 10), now)
        assertEquals(RefillUrgency.HEALTHY, forecast.urgency)
    }

    @Test
    fun `upcoming urgency between three and seven days`() {
        val forecast = RefillCalculator.forecast(med(pillCount = 5), now)
        assertEquals(RefillUrgency.UPCOMING, forecast.urgency)
    }

    @Test
    fun `urgent urgency under three days`() {
        val forecast = RefillCalculator.forecast(med(pillCount = 2), now)
        assertEquals(RefillUrgency.URGENT, forecast.urgency)
    }

    @Test
    fun `out of stock when pill count is zero`() {
        val forecast = RefillCalculator.forecast(med(pillCount = 0), now)
        assertEquals(RefillUrgency.OUT_OF_STOCK, forecast.urgency)
        assertEquals(0, forecast.daysRemaining)
    }

    @Test
    fun `apply daily dose decrements pill count`() {
        val updated = RefillCalculator.applyDailyDose(med(pillCount = 30), now)
        assertEquals(29, updated.pillCount)
    }

    @Test
    fun `apply daily dose with multi-dose consumes full daily amount`() {
        val updated = RefillCalculator.applyDailyDose(
            med(pillCount = 30, pillsPerDose = 2, dosesPerDay = 2),
            now
        )
        // 2 * 2 = 4 pills per day.
        assertEquals(26, updated.pillCount)
    }

    @Test
    fun `apply daily dose floors at zero`() {
        val updated = RefillCalculator.applyDailyDose(med(pillCount = 0), now)
        assertEquals(0, updated.pillCount)
    }

    @Test
    fun `apply refill resets count and stamps date`() {
        val before = med(pillCount = 5, lastRefill = now - 10 * oneDay)
        val after = RefillCalculator.applyRefill(before, newSupply = 60, now = now)
        assertEquals(60, after.pillCount)
        assertEquals(now, after.lastRefillDate)
    }

    @Test
    fun `adherence rate returns taken over expected`() {
        val rate = RefillCalculator.adherenceRate(med(dosesPerDay = 1), dosesTaken = 6, rangeDays = 7)
        // 6 / 7 ≈ 0.857
        assertEquals(0.857f, rate, 0.01f)
    }

    @Test
    fun `adherence rate clamps to one`() {
        val rate = RefillCalculator.adherenceRate(med(dosesPerDay = 1), dosesTaken = 20, rangeDays = 7)
        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `adherence rate zero when range is zero`() {
        val rate = RefillCalculator.adherenceRate(med(), dosesTaken = 0, rangeDays = 0)
        assertEquals(0f, rate, 0.001f)
    }
}
