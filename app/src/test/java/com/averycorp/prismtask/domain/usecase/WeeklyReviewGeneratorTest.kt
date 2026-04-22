package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.WeeklyReviewEntity
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.WeeklyReviewRequest
import com.averycorp.prismtask.data.remote.api.WeeklyReviewResponse
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.data.repository.WeeklyReviewRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone

class WeeklyReviewGeneratorTest {
    private lateinit var taskRepository: TaskRepository
    private lateinit var weeklyReviewRepository: WeeklyReviewRepository
    private lateinit var api: PrismTaskApi
    private lateinit var proFeatureGate: ProFeatureGate
    private lateinit var generator: WeeklyReviewGenerator

    // Midweek Wednesday 2026-04-15 00:00 UTC. Aggregator uses
    // TimeZone.getDefault() so we pin UTC via reference math.
    private val reference = 1_776_211_200_000L
    private val weekStart: Long = run {
        val c = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = reference
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            firstDayOfWeek = Calendar.MONDAY
            val diff = ((get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY) + 7) % 7
            add(Calendar.DAY_OF_YEAR, -diff)
        }
        c.timeInMillis
    }

    @Before
    fun setUp() {
        taskRepository = mockk(relaxed = true)
        weeklyReviewRepository = mockk(relaxed = true)
        api = mockk(relaxed = true)
        proFeatureGate = mockk(relaxed = true)
        generator = WeeklyReviewGenerator(
            taskRepository = taskRepository,
            weeklyReviewRepository = weeklyReviewRepository,
            api = api,
            proFeatureGate = proFeatureGate
        )
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_WEEKLY_REVIEW) } returns true
    }

    private fun completedTask(id: Long, atMillis: Long): TaskEntity = TaskEntity(
        id = id,
        title = "task $id",
        isCompleted = true,
        completedAt = atMillis,
        createdAt = atMillis - 1_000L,
        updatedAt = atMillis
    )

    private fun slippedTask(id: Long, dueMillis: Long): TaskEntity = TaskEntity(
        id = id,
        title = "slipped $id",
        isCompleted = false,
        dueDate = dueMillis,
        createdAt = dueMillis - 1_000L,
        updatedAt = dueMillis
    )

    @Test
    fun success_persists_entity_and_returns_generated() = runBlocking {
        val tasks = listOf(
            completedTask(1L, weekStart + 1_000L),
            completedTask(2L, weekStart + 2_000L),
            slippedTask(3L, weekStart + 3_000L)
        )
        coEvery { taskRepository.getAllTasksOnce() } returns tasks
        val response = WeeklyReviewResponse(
            weekStart = "2026-04-13",
            weekEnd = "2026-04-19",
            wins = listOf("Shipped the release"),
            slips = listOf("Skipped leg day"),
            patterns = listOf("Peak focus on Wednesdays"),
            nextWeekFocus = listOf("Keep the Wed momentum"),
            narrative = "Solid week overall."
        )
        coEvery { api.getWeeklyReview(any()) } returns response
        val persisted = WeeklyReviewEntity(
            id = 42L,
            weekStartDate = weekStart,
            metricsJson = "{}",
            aiInsightsJson = Gson().toJson(response)
        )
        coEvery { weeklyReviewRepository.save(weekStart, any(), any()) } returns 42L
        coEvery { weeklyReviewRepository.getForWeek(weekStart) } returns persisted

        val outcome = generator.generateReview(reference)

        assertTrue(outcome is WeeklyReviewGenerationOutcome.Generated)
        val generated = outcome as WeeklyReviewGenerationOutcome.Generated
        assertEquals(42L, generated.review.id)

        // Backend request must carry the completed + slipped task lists
        // aggregator produced. Capture + assert so we don't silently drop
        // data on the way to Haiku.
        val requestSlot = slot<WeeklyReviewRequest>()
        coVerify { api.getWeeklyReview(capture(requestSlot)) }
        assertEquals(2, requestSlot.captured.completedTasks.size)
        assertEquals(1, requestSlot.captured.slippedTasks.size)
    }

    @Test
    fun backend_unavailable_is_reported_and_no_row_written() = runBlocking {
        coEvery { taskRepository.getAllTasksOnce() } returns listOf(
            completedTask(1L, weekStart + 1_000L)
        )
        coEvery { api.getWeeklyReview(any()) } throws IOException("offline")

        val outcome = generator.generateReview(reference)

        assertTrue(outcome is WeeklyReviewGenerationOutcome.BackendUnavailable)
        // Spec: "if API unavailable, skip generation for that week (no
        // record created)" — verify no persistence happens.
        coVerify(exactly = 0) { weeklyReviewRepository.save(any(), any(), any()) }
    }

    @Test
    fun no_activity_week_returns_NoActivity_without_calling_api() = runBlocking {
        // No completed and no slipped tasks for the reference week.
        coEvery { taskRepository.getAllTasksOnce() } returns emptyList()

        val outcome = generator.generateReview(reference)

        assertTrue(outcome is WeeklyReviewGenerationOutcome.NoActivity)
        // No empty-week prompt sent to the backend — it would cost
        // tokens for nothing.
        coVerify(exactly = 0) { api.getWeeklyReview(any()) }
        coVerify(exactly = 0) { weeklyReviewRepository.save(any(), any(), any()) }
    }

    @Test
    fun free_tier_returns_NotEligible_and_skips_api() = runBlocking {
        every { proFeatureGate.hasAccess(ProFeatureGate.AI_WEEKLY_REVIEW) } returns false
        coEvery { taskRepository.getAllTasksOnce() } returns listOf(
            completedTask(1L, weekStart + 1_000L)
        )

        val outcome = generator.generateReview(reference)

        assertTrue(outcome is WeeklyReviewGenerationOutcome.NotEligible)
        coVerify(exactly = 0) { api.getWeeklyReview(any()) }
        coVerify(exactly = 0) { weeklyReviewRepository.save(any(), any(), any()) }
    }

    @Test
    fun very_high_activity_week_serializes_without_truncation() = runBlocking {
        val many = (1L..500L).map { i ->
            completedTask(i, weekStart + i)
        }
        coEvery { taskRepository.getAllTasksOnce() } returns many
        coEvery { api.getWeeklyReview(any()) } returns WeeklyReviewResponse(
            weekStart = "2026-04-13",
            weekEnd = "2026-04-19"
        )
        val persisted = WeeklyReviewEntity(
            id = 1L,
            weekStartDate = weekStart,
            metricsJson = "{}",
            aiInsightsJson = "{}"
        )
        coEvery { weeklyReviewRepository.save(any(), any(), any()) } returns 1L
        coEvery { weeklyReviewRepository.getForWeek(weekStart) } returns persisted

        val outcome = generator.generateReview(reference)

        assertTrue(outcome is WeeklyReviewGenerationOutcome.Generated)
        // Metrics JSON is the aggregator snapshot, not the task list;
        // verify the count matches so the caller can't slip a truncation
        // regression past us.
        val savedMetrics = slot<String>()
        coVerify { weeklyReviewRepository.save(any(), capture(savedMetrics), any()) }
        val metricsObj = JsonParser.parseString(savedMetrics.captured).asJsonObject
        assertEquals(500, metricsObj.get("completed").asInt)
        assertFalse(metricsObj.has("completedTasks")) // tasks not serialized into metrics
    }
}
