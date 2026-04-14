package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.LifeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeeklyReviewAggregatorTest {
    private val agg = WeeklyReviewAggregator()
    private val utc = TimeZone.getTimeZone("UTC")

    // Monday 2026-04-06 00:00 UTC = 1_775_779_200_000 + (5 days back from 2026-04-11 Saturday)
    // Use a fixed reference inside the week we care about.
    private val reference = 1_776_211_200_000L // 2026-04-15 Wednesday 00:00 UTC

    private fun task(
        id: Long,
        isCompleted: Boolean = false,
        completedAt: Long? = null,
        dueDate: Long? = null,
        lifeCategory: LifeCategory? = null,
        createdAt: Long = reference - 1000,
        updatedAt: Long = reference - 1000,
        archivedAt: Long? = null
    ): TaskEntity = TaskEntity(
        id = id,
        title = "task $id",
        isCompleted = isCompleted,
        completedAt = completedAt,
        dueDate = dueDate,
        lifeCategory = lifeCategory?.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt
    )

    @Test
    fun `empty task list produces zero stats`() {
        val stats = agg.aggregate(emptyList(), reference, utc)
        assertEquals(0, stats.completed)
        assertEquals(0, stats.slipped)
        assertEquals(0, stats.total)
        assertEquals(0f, stats.completionRate, 0.001f)
    }

    @Test
    fun `tasks completed within the week are counted`() {
        val tasks = listOf(
            task(1, isCompleted = true, completedAt = reference - 2 * DAY, lifeCategory = LifeCategory.WORK),
            task(2, isCompleted = true, completedAt = reference - 1 * DAY, lifeCategory = LifeCategory.SELF_CARE)
        )
        val stats = agg.aggregate(tasks, reference, utc)
        assertEquals(2, stats.completed)
        assertEquals(1, stats.byCategory[LifeCategory.WORK])
        assertEquals(1, stats.byCategory[LifeCategory.SELF_CARE])
    }

    @Test
    fun `tasks completed in a previous week are excluded`() {
        val tasks = listOf(
            task(1, isCompleted = true, completedAt = reference - 10 * DAY, lifeCategory = LifeCategory.WORK)
        )
        val stats = agg.aggregate(tasks, reference, utc)
        assertEquals(0, stats.completed)
    }

    @Test
    fun `open tasks due this week count as slipped`() {
        val tasks = listOf(
            task(1, dueDate = reference - 1 * DAY)
        )
        val stats = agg.aggregate(tasks, reference, utc)
        assertEquals(1, stats.slipped)
        assertEquals(1, stats.carryForward.size)
    }

    @Test
    fun `archived tasks are not counted as slipped`() {
        val tasks = listOf(
            task(1, dueDate = reference - 1 * DAY, archivedAt = reference)
        )
        val stats = agg.aggregate(tasks, reference, utc)
        assertEquals(0, stats.slipped)
    }

    @Test
    fun `rescheduled tasks are detected by updated after create`() {
        val tasks = listOf(
            task(
                1,
                dueDate = reference + 10 * DAY, // pushed past this week
                createdAt = reference - 20 * DAY,
                updatedAt = reference - 1 * DAY
            )
        )
        val stats = agg.aggregate(tasks, reference, utc)
        assertEquals(1, stats.rescheduled)
    }

    @Test
    fun `completion rate is completed over total`() {
        val tasks = listOf(
            task(1, isCompleted = true, completedAt = reference),
            task(2, isCompleted = true, completedAt = reference),
            task(3, dueDate = reference),
            task(4, dueDate = reference)
        )
        val stats = agg.aggregate(tasks, reference, utc)
        assertEquals(2, stats.completed)
        assertEquals(2, stats.slipped)
        assertEquals(0.5f, stats.completionRate, 0.001f)
    }

    @Test
    fun `week start monday by default`() {
        val stats = agg.aggregate(emptyList(), reference, utc, Calendar.MONDAY)
        val cal = Calendar.getInstance(utc).apply { timeInMillis = stats.weekStart }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `week end is exactly seven days after start`() {
        val stats = agg.aggregate(emptyList(), reference, utc)
        assertEquals(7 * DAY, stats.weekEnd - stats.weekStart)
    }

    @Test
    fun `uncategorized completed tasks still counted in total`() {
        val tasks = listOf(
            task(1, isCompleted = true, completedAt = reference)
        )
        val stats = agg.aggregate(tasks, reference, utc)
        assertEquals(1, stats.completed)
        // But not in byCategory since there's no category set.
        assertTrue(stats.byCategory.values.all { it == 0 })
    }

    companion object {
        private const val DAY: Long = 24L * 60 * 60 * 1000
    }
}
