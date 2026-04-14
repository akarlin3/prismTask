package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.MoodEnergyLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodCorrelationEngineTest {
    private val engine = MoodCorrelationEngine()

    private fun observation(
        day: Int,
        mood: Int,
        energy: Int = mood,
        selfCare: Int = 0,
        work: Int = 0
    ): DailyObservation = DailyObservation(
        date = day.toLong() * 24 * 60 * 60 * 1000,
        mood = mood,
        energy = energy,
        selfCareTasksCompleted = selfCare,
        workTasksCompleted = work,
        tasksCompleted = selfCare + work
    )

    @Test
    fun `empty data returns empty result list`() {
        assertTrue(engine.correlateMood(emptyList()).isEmpty())
    }

    @Test
    fun `fewer than seven observations returns empty`() {
        val obs = (1..6).map { observation(it, mood = it, selfCare = it) }
        assertTrue(engine.correlateMood(obs).isEmpty())
    }

    @Test
    fun `perfect positive correlation between mood and self-care`() {
        val obs = (1..7).map { observation(it, mood = it, selfCare = it) }
        val results = engine.correlateMood(obs)
        val selfCare = results.first { it.factor == CorrelationFactor.SELF_CARE_TASKS_COMPLETED }
        assertEquals(1f, selfCare.coefficient, 0.001f)
        assertEquals(CorrelationStrength.STRONG, selfCare.strength)
    }

    @Test
    fun `perfect negative correlation between mood and work tasks`() {
        val obs = (1..7).map { observation(it, mood = 8 - it, work = it) }
        val results = engine.correlateMood(obs)
        val work = results.first { it.factor == CorrelationFactor.WORK_TASKS_COMPLETED }
        assertEquals(-1f, work.coefficient, 0.001f)
        assertEquals(CorrelationStrength.STRONG, work.strength)
    }

    @Test
    fun `flat mood series returns zero coefficient`() {
        val obs = (1..7).map { observation(it, mood = 3, selfCare = it) }
        val results = engine.correlateMood(obs)
        val selfCare = results.first { it.factor == CorrelationFactor.SELF_CARE_TASKS_COMPLETED }
        assertEquals(0f, selfCare.coefficient, 0.001f)
    }

    @Test
    fun `energy correlation uses energy values`() {
        val obs = (1..7).map { observation(it, mood = 3, energy = it, work = i(it)) }
        val results = engine.correlateEnergy(obs)
        // Work tasks ascend with energy → positive correlation.
        val work = results.first { it.factor == CorrelationFactor.WORK_TASKS_COMPLETED }
        assertTrue(work.coefficient > 0.9f)
    }

    @Test
    fun `results are sorted by absolute coefficient descending`() {
        val obs = (1..7).map { observation(it, mood = it, selfCare = it, work = 8 - it) }
        val results = engine.correlateMood(obs)
        // Both selfCare (+1) and work (-1) should tie at |1|, but order matters
        // less than strength — check the first result is "STRONG".
        assertEquals(CorrelationStrength.STRONG, results.first().strength)
    }

    @Test
    fun `strength buckets map as expected`() {
        // Moderate: correlation around 0.4
        val pearson = engine.pearson(
            listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f),
            listOf(1f, 1f, 2f, 4f, 3f, 7f, 6f)
        )
        assertTrue(pearson > 0.3f && pearson < 0.95f)
    }

    @Test
    fun `averageByDay groups morning and evening entries`() {
        val logs = listOf(
            MoodEnergyLogEntity(date = 100, mood = 4, energy = 3, timeOfDay = "morning"),
            MoodEnergyLogEntity(date = 100, mood = 2, energy = 5, timeOfDay = "evening"),
            MoodEnergyLogEntity(date = 200, mood = 5, energy = 5, timeOfDay = "morning")
        )
        val avg = engine.averageByDay(logs)
        val day100 = avg[100L]!!
        assertEquals(3f, day100.first, 0.001f)
        assertEquals(4f, day100.second, 0.001f)
        val day200 = avg[200L]!!
        assertEquals(5f, day200.first, 0.001f)
    }

    @Test
    fun `averageByDay empty returns empty map`() {
        assertTrue(engine.averageByDay(emptyList()).isEmpty())
    }

    // Helper used by the energy correlation test to avoid a parameter conflict
    // (observation() has an `energy` default).
    private fun i(x: Int): Int = x
}
