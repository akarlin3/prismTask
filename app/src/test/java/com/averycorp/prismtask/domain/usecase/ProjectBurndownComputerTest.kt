package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProjectBurndownComputerTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val computer = ProjectBurndownComputer()

    @Test
    fun `empty task list returns null`() {
        val result = computer.compute(
            tasks = emptyList(),
            zone = zone,
            today = LocalDate.of(2026, 4, 28),
            projectStart = LocalDate.of(2026, 4, 21),
            projectEnd = LocalDate.of(2026, 5, 5)
        )
        assertNull(result)
    }

    @Test
    fun `actual line ends on today and ideal line spans the whole window`() {
        val start = LocalDate.of(2026, 4, 21)
        val end = LocalDate.of(2026, 4, 28)
        val today = LocalDate.of(2026, 4, 24)
        val tasks = listOf(
            taskEntity(id = 1, isCompleted = false),
            taskEntity(id = 2, isCompleted = false),
            taskEntity(id = 3, isCompleted = true, completedOn = LocalDate.of(2026, 4, 22)),
            taskEntity(id = 4, isCompleted = true, completedOn = LocalDate.of(2026, 4, 23))
        )

        val result = computer.compute(
            tasks = tasks,
            zone = zone,
            today = today,
            projectStart = start,
            projectEnd = end
        )

        assertNotNull(result)
        val burndown = result!!
        assertEquals(8, burndown.points.size) // start..end inclusive

        // Day 0 (start): nothing completed yet → 4 remaining (actual)
        assertEquals(4, burndown.points[0].actualRemaining)
        // Ideal at start = totalTasks
        assertEquals(4.0, burndown.points[0].idealRemaining, 0.001)
        // Ideal at end = 0
        assertEquals(0.0, burndown.points.last().idealRemaining, 0.001)

        // Days after `today` should have null actuals
        val afterToday = burndown.points.filter { it.date.isAfter(today) }
        afterToday.forEach { assertNull(it.actualRemaining) }

        // Days on or before `today` should have non-null actuals
        burndown.points.filter { !it.date.isAfter(today) }.forEach {
            assertNotNull("Actual remaining on ${it.date}", it.actualRemaining)
        }

        assertEquals(4, burndown.totalTasks)
        assertEquals(2, burndown.remainingTasks)
    }

    @Test
    fun `velocity counts completions in the last seven days only`() {
        val today = LocalDate.of(2026, 4, 28)
        val tasks = listOf(
            taskEntity(id = 1, isCompleted = true, completedOn = LocalDate.of(2026, 4, 22)),
            taskEntity(id = 2, isCompleted = true, completedOn = LocalDate.of(2026, 4, 25)),
            taskEntity(id = 3, isCompleted = true, completedOn = LocalDate.of(2026, 4, 28)),
            // Outside the seven-day window — should NOT count
            taskEntity(id = 4, isCompleted = true, completedOn = LocalDate.of(2026, 4, 10))
        )
        val result = computer.compute(
            tasks = tasks,
            zone = zone,
            today = today,
            projectStart = LocalDate.of(2026, 4, 1),
            projectEnd = LocalDate.of(2026, 5, 1)
        )

        assertNotNull(result)
        // 3 completed in the seven-day window / 7 = 0.4285…
        assertEquals(3.0 / 7.0, result!!.velocityPerDay, 0.001)
    }

    @Test
    fun `projectedCompletion is null when there is remaining work but no velocity`() {
        val today = LocalDate.of(2026, 4, 28)
        val tasks = listOf(
            taskEntity(id = 1, isCompleted = false),
            taskEntity(id = 2, isCompleted = false)
        )
        val result = computer.compute(
            tasks = tasks,
            zone = zone,
            today = today,
            projectStart = LocalDate.of(2026, 4, 21),
            projectEnd = LocalDate.of(2026, 5, 1)
        )

        assertNotNull(result)
        assertEquals(0.0, result!!.velocityPerDay, 0.001)
        assertNull("No velocity → no projection", result.projectedCompletion)
    }

    @Test
    fun `projectedCompletion lands on today when nothing is remaining`() {
        val today = LocalDate.of(2026, 4, 28)
        val tasks = listOf(
            taskEntity(id = 1, isCompleted = true, completedOn = LocalDate.of(2026, 4, 25))
        )
        val result = computer.compute(
            tasks = tasks,
            zone = zone,
            today = today,
            projectStart = LocalDate.of(2026, 4, 21),
            projectEnd = LocalDate.of(2026, 5, 1)
        )

        assertNotNull(result)
        assertEquals(today, result!!.projectedCompletion)
    }

    @Test
    fun `degenerate window (end on or before start) returns null`() {
        val today = LocalDate.of(2026, 4, 28)
        val tasks = listOf(taskEntity(id = 1, isCompleted = false))
        val result = computer.compute(
            tasks = tasks,
            zone = zone,
            today = today,
            projectStart = LocalDate.of(2026, 4, 28),
            projectEnd = LocalDate.of(2026, 4, 28)
        )
        assertNull(result)
    }

    @Test
    fun `null project dates fall back to task createdAt and a 14-day window`() {
        val today = LocalDate.of(2026, 4, 28)
        val createdAtMs = LocalDate.of(2026, 4, 21)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            taskEntity(id = 1, createdAt = createdAtMs, isCompleted = false),
            taskEntity(id = 2, createdAt = createdAtMs, isCompleted = false)
        )

        val result = computer.compute(
            tasks = tasks,
            zone = zone,
            today = today,
            projectStart = null,
            projectEnd = null
        )
        assertNotNull(result)
        // 14-day window inclusive => 15 points
        assertEquals(15, result!!.points.size)
        assertTrue(result.points.first().date == LocalDate.of(2026, 4, 21))
        assertTrue(result.points.last().date == LocalDate.of(2026, 5, 5))
    }

    private fun taskEntity(
        id: Long,
        isCompleted: Boolean = false,
        completedOn: LocalDate? = null,
        createdAt: Long = LocalDate.of(2026, 4, 21).atStartOfDay(zone).toInstant().toEpochMilli()
    ): TaskEntity {
        val completedAt = completedOn?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.plus(12 * 3600 * 1000L)
        return TaskEntity(
            id = id,
            title = "task-$id",
            isCompleted = isCompleted,
            completedAt = completedAt,
            createdAt = createdAt
        )
    }
}
