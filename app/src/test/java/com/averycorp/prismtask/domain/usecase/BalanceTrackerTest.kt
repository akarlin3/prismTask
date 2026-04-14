package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.LifeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class BalanceTrackerTest {
    private val tracker = BalanceTracker()
    private val utc = TimeZone.getTimeZone("UTC")

    // 2026-04-11 00:00 UTC
    private val now = 1_775_779_200_000L
    private val oneDay = 24L * 60 * 60 * 1000

    private fun task(
        id: Long,
        category: LifeCategory?,
        dueDate: Long = now - oneDay
    ): TaskEntity = TaskEntity(
        id = id,
        title = "task $id",
        dueDate = dueDate,
        createdAt = dueDate,
        updatedAt = dueDate,
        lifeCategory = category?.name
    )

    @Test
    fun `empty task list produces empty balance`() {
        val state = tracker.compute(emptyList(), BalanceConfig(), now, utc)
        assertEquals(0, state.totalTracked)
        assertEquals(LifeCategory.UNCATEGORIZED, state.dominantCategory)
        assertFalse(state.isOverloaded)
    }

    @Test
    fun `uncategorized tasks are excluded from counts`() {
        val tasks = listOf(
            task(1, null),
            task(2, null),
            task(3, LifeCategory.SELF_CARE)
        )
        val state = tracker.compute(tasks, BalanceConfig(), now, utc)
        assertEquals(1, state.totalTracked)
        assertEquals(LifeCategory.SELF_CARE, state.dominantCategory)
    }

    @Test
    fun `ratios normalize to one`() {
        val tasks = listOf(
            task(1, LifeCategory.WORK),
            task(2, LifeCategory.WORK),
            task(3, LifeCategory.WORK),
            task(4, LifeCategory.SELF_CARE)
        )
        val state = tracker.compute(tasks, BalanceConfig(), now, utc)
        assertEquals(0.75f, state.currentRatios[LifeCategory.WORK]!!, 0.001f)
        assertEquals(0.25f, state.currentRatios[LifeCategory.SELF_CARE]!!, 0.001f)
        assertEquals(LifeCategory.WORK, state.dominantCategory)
    }

    @Test
    fun `overload triggers when work exceeds target plus threshold`() {
        // 8 work + 2 self-care → 80% work. Target 40% + 10% = 50%, 80 > 50.
        val tasks = (1..8).map { task(it.toLong(), LifeCategory.WORK) } +
            (9..10).map { task(it.toLong(), LifeCategory.SELF_CARE) }
        val state = tracker.compute(tasks, BalanceConfig(), now, utc)
        assertTrue(state.isOverloaded)
    }

    @Test
    fun `overload does not trigger when at threshold`() {
        // 5 work + 5 personal → 50% work. Target 40% + 10% = 50%, so NOT > 50%.
        val tasks = (1..5).map { task(it.toLong(), LifeCategory.WORK) } +
            (6..10).map { task(it.toLong(), LifeCategory.PERSONAL) }
        val state = tracker.compute(tasks, BalanceConfig(), now, utc)
        assertFalse(state.isOverloaded)
    }

    @Test
    fun `tasks outside the 7 day window are excluded from current ratios`() {
        val tasks = listOf(
            task(1, LifeCategory.WORK, dueDate = now - 30 * oneDay), // too old
            task(2, LifeCategory.SELF_CARE, dueDate = now - 2 * oneDay)
        )
        val state = tracker.compute(tasks, BalanceConfig(), now, utc)
        assertEquals(1, state.totalTracked)
        assertEquals(1f, state.currentRatios[LifeCategory.SELF_CARE]!!, 0.001f)
    }

    @Test
    fun `rolling ratios include older tasks up to 28 days`() {
        val tasks = listOf(
            task(1, LifeCategory.WORK, dueDate = now - 20 * oneDay),
            task(2, LifeCategory.SELF_CARE, dueDate = now - 2 * oneDay)
        )
        val state = tracker.compute(tasks, BalanceConfig(), now, utc)
        // Rolling window covers both.
        assertEquals(0.5f, state.rollingRatios[LifeCategory.WORK]!!, 0.001f)
        assertEquals(0.5f, state.rollingRatios[LifeCategory.SELF_CARE]!!, 0.001f)
    }

    @Test
    fun `dominant category picks the max ratio`() {
        val tasks = listOf(
            task(1, LifeCategory.WORK),
            task(2, LifeCategory.PERSONAL),
            task(3, LifeCategory.PERSONAL)
        )
        val state = tracker.compute(tasks, BalanceConfig(), now, utc)
        assertEquals(LifeCategory.PERSONAL, state.dominantCategory)
    }

    @Test
    fun `config isValid true when sums to 1`() {
        val config = BalanceConfig(
            workTarget = 0.40f,
            personalTarget = 0.25f,
            selfCareTarget = 0.20f,
            healthTarget = 0.15f
        )
        assertTrue(config.isValid())
    }

    @Test
    fun `config isValid false when sum is wrong`() {
        val config = BalanceConfig(
            workTarget = 0.50f,
            personalTarget = 0.50f,
            selfCareTarget = 0.50f,
            healthTarget = 0.50f
        )
        assertFalse(config.isValid())
    }
}
