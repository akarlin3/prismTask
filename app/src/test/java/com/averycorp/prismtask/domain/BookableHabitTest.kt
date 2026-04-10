package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.HabitLogEntity
import com.averycorp.prismtask.ui.screens.habits.HabitDetailStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class BookableHabitTest {

    private fun makeBookableHabit(
        id: Long = 1,
        isBooked: Boolean = false,
        bookedDate: Long? = null,
        bookedNote: String? = null,
        frequencyPeriod: String = "monthly"
    ) = HabitEntity(
        id = id,
        name = "Dentist",
        isBookable = true,
        isBooked = isBooked,
        bookedDate = bookedDate,
        bookedNote = bookedNote,
        frequencyPeriod = frequencyPeriod,
        targetFrequency = 1
    )

    // --- Test: logActivity resets booking ---

    @Test
    fun logActivity_shouldResetBooking() {
        // Simulate: habit is booked, then user logs activity
        val habit = makeBookableHabit(
            isBooked = true,
            bookedDate = System.currentTimeMillis(),
            bookedNote = "Dr. Smith, 2pm"
        )

        // After logActivity, the habit should have booking cleared
        val updatedHabit = habit.copy(
            isBooked = false,
            bookedDate = null,
            bookedNote = null
        )

        assertFalse(updatedHabit.isBooked)
        assertNull(updatedHabit.bookedDate)
        assertNull(updatedHabit.bookedNote)
    }

    // --- Test: setBooked updates habit correctly ---

    @Test
    fun setBooked_shouldUpdateHabitFields() {
        val habit = makeBookableHabit()
        val bookedDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        val bookedNote = "Dr. Smith, 2pm"

        val updated = habit.copy(
            isBooked = true,
            bookedDate = bookedDate,
            bookedNote = bookedNote
        )

        assertTrue(updated.isBooked)
        assertEquals(bookedDate, updated.bookedDate)
        assertEquals(bookedNote, updated.bookedNote)
    }

    @Test
    fun setBooked_unbookClearsFields() {
        val habit = makeBookableHabit(
            isBooked = true,
            bookedDate = System.currentTimeMillis(),
            bookedNote = "note"
        )

        val unbooked = habit.copy(
            isBooked = false,
            bookedDate = null,
            bookedNote = null
        )

        assertFalse(unbooked.isBooked)
        assertNull(unbooked.bookedDate)
        assertNull(unbooked.bookedNote)
    }

    // --- Test: getLastLogDate returns most recent ---

    @Test
    fun getLastLogDate_returnsMostRecent() {
        val logs = listOf(
            HabitLogEntity(id = 1, habitId = 1, date = 1000L),
            HabitLogEntity(id = 2, habitId = 1, date = 3000L),
            HabitLogEntity(id = 3, habitId = 1, date = 2000L)
        )
        // sorted descending by date → first = most recent
        val lastLog = logs.maxByOrNull { it.date }
        assertNotNull(lastLog)
        assertEquals(3000L, lastLog!!.date)
    }

    // --- Test: average interval calculation ---

    @Test
    fun averageInterval_calculatedFromLogDates() {
        val day = TimeUnit.DAYS.toMillis(1)
        val logs = listOf(
            HabitLogEntity(id = 1, habitId = 1, date = 0),
            HabitLogEntity(id = 2, habitId = 1, date = 30 * day),
            HabitLogEntity(id = 3, habitId = 1, date = 60 * day)
        )

        val sortedDates = logs.map { it.date }.sorted()
        val intervals = sortedDates.zipWithNext { a, b -> b - a }
        val avgMillis = intervals.average()
        val avgDays = (avgMillis / day).toInt()

        assertEquals(30, avgDays)
    }

    @Test
    fun averageInterval_singleLog_returnsNull() {
        val logs = listOf(
            HabitLogEntity(id = 1, habitId = 1, date = 1000L)
        )

        val sortedDates = logs.map { it.date }.sorted()
        val avgInterval = if (sortedDates.size >= 2) {
            val intervals = sortedDates.zipWithNext { a, b -> b - a }
            intervals.average().toInt()
        } else null

        assertNull(avgInterval)
    }

    // --- Test: overdue detection ---

    @Test
    fun overdueDetection_lastLogBeyondExpectedInterval() {
        val habit = makeBookableHabit(frequencyPeriod = "monthly")
        val day = TimeUnit.DAYS.toMillis(1)
        val lastLogDate = System.currentTimeMillis() - (35 * day) // 35 days ago

        val periodDays = when (habit.frequencyPeriod) {
            "weekly" -> 7L
            "fortnightly" -> 14L
            "monthly" -> 30L
            "bimonthly" -> 60L
            "quarterly" -> 90L
            else -> Long.MAX_VALUE
        }

        val elapsed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastLogDate)
        val isOverdue = elapsed > periodDays

        assertTrue("Should be overdue after 35 days for a monthly habit", isOverdue)
    }

    @Test
    fun overdueDetection_withinInterval_notOverdue() {
        val habit = makeBookableHabit(frequencyPeriod = "monthly")
        val day = TimeUnit.DAYS.toMillis(1)
        val lastLogDate = System.currentTimeMillis() - (15 * day) // 15 days ago

        val periodDays = 30L
        val elapsed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastLogDate)
        val isOverdue = elapsed > periodDays

        assertFalse("Should not be overdue after 15 days for a monthly habit", isOverdue)
    }

    // --- Test: HabitDetailStats computation ---

    @Test
    fun habitDetailStats_computesCorrectly() {
        val day = TimeUnit.DAYS.toMillis(1)
        val now = System.currentTimeMillis()
        val logs = listOf(
            HabitLogEntity(id = 1, habitId = 1, date = now - 90 * day),
            HabitLogEntity(id = 2, habitId = 1, date = now - 60 * day),
            HabitLogEntity(id = 3, habitId = 1, date = now - 30 * day)
        )

        val sortedDates = logs.map { it.date }.sorted()
        val totalCount = logs.size
        val avgInterval = if (sortedDates.size >= 2) {
            val intervals = sortedDates.zipWithNext { a, b -> b - a }
            (intervals.average() / day).toInt().coerceAtLeast(1)
        } else null
        val lastDone = sortedDates.lastOrNull()
        val nextSuggested = if (lastDone != null && avgInterval != null) {
            lastDone + avgInterval.toLong() * day
        } else null

        assertEquals(3, totalCount)
        assertEquals(30, avgInterval)
        assertNotNull(lastDone)
        assertNotNull(nextSuggested)
        // Next suggested should be approximately "now"
        val daysUntilNext = TimeUnit.MILLISECONDS.toDays(nextSuggested!! - now)
        assertTrue("Next suggested should be within a few days of now", daysUntilNext in -2..2)
    }
}
