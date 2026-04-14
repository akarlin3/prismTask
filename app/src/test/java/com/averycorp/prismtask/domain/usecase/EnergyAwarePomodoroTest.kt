package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.MoodEnergyLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyAwarePomodoroTest {
    private val planner = EnergyAwarePomodoro()

    @Test
    fun `null energy falls back to classic defaults`() {
        val config = planner.plan(null, DefaultPomodoroConfig(25, 5, 15))
        assertEquals(25, config.workMinutes)
        assertEquals(5, config.breakMinutes)
        assertEquals(15, config.longBreakMinutes)
    }

    @Test
    fun `null energy falls back to custom defaults`() {
        val config = planner.plan(null, DefaultPomodoroConfig(50, 10, 30))
        assertEquals(50, config.workMinutes)
        assertEquals(10, config.breakMinutes)
    }

    @Test
    fun `energy one produces short session long break`() {
        val config = planner.plan(1)
        assertEquals(15, config.workMinutes)
        assertEquals(10, config.breakMinutes)
    }

    @Test
    fun `energy two uses the low-energy bucket`() {
        val config = planner.plan(2)
        assertEquals(15, config.workMinutes)
    }

    @Test
    fun `energy three uses classic pomodoro`() {
        val config = planner.plan(3)
        assertEquals(25, config.workMinutes)
        assertEquals(5, config.breakMinutes)
    }

    @Test
    fun `energy four produces a thirty-five minute session`() {
        val config = planner.plan(4)
        assertEquals(35, config.workMinutes)
    }

    @Test
    fun `energy five produces a forty-five minute sprint`() {
        val config = planner.plan(5)
        assertEquals(45, config.workMinutes)
        assertEquals(3, config.breakMinutes)
    }

    @Test
    fun `out of range energy is clamped`() {
        val zero = planner.plan(0)
        val ten = planner.plan(10)
        // 0 clamps to 1 → low-energy. 10 clamps to 5 → peak.
        assertEquals(15, zero.workMinutes)
        assertEquals(45, ten.workMinutes)
    }

    @Test
    fun `planFromLogs picks the most recent log`() {
        val logs = listOf(
            MoodEnergyLogEntity(date = 1, mood = 3, energy = 1, createdAt = 100),
            MoodEnergyLogEntity(date = 1, mood = 4, energy = 5, createdAt = 200)
        )
        val config = planner.planFromLogs(logs)
        // Latest createdAt = 200 → energy 5 → peak sprint.
        assertEquals(45, config.workMinutes)
    }

    @Test
    fun `planFromLogs with empty list uses defaults`() {
        val config = planner.planFromLogs(emptyList(), DefaultPomodoroConfig(30, 6, 18))
        assertEquals(30, config.workMinutes)
        assertEquals(6, config.breakMinutes)
    }

    @Test
    fun `rationale is non-empty for every bucket`() {
        (1..5).forEach { energy ->
            assertTrue(planner.plan(energy).rationale.isNotBlank())
        }
    }
}
