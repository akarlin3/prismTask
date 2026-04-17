package com.averycorp.prismtask

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.converter.RecurrenceConverter
import com.averycorp.prismtask.data.local.database.DatabaseTransactionRunner
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.averycorp.prismtask.domain.model.RecurrenceType
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RecurrenceIntegrationTest {
    private lateinit var database: PrismTaskDatabase
    private lateinit var repository: TaskRepository

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setup() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                PrismTaskDatabase::class.java
            ).allowMainThreadQueries()
            .build()

        repository = TaskRepository(
            transactionRunner = DatabaseTransactionRunner(database),
            taskDao = database.taskDao(),
            tagDao = database.tagDao(),
            syncTracker = mockk(relaxed = true),
            calendarPushDispatcher = mockk(relaxed = true),
            reminderScheduler = mockk(relaxed = true),
            widgetUpdateManager = mockk(relaxed = true),
            taskCompletionRepository = mockk(relaxed = true)
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun test_completeRecurringTask_createsNextOccurrence() = runTest {
        val dueDate = LocalDate.of(2025, 1, 6).toMillis()
        val rule = RecurrenceRule(type = RecurrenceType.DAILY)
        val ruleJson = RecurrenceConverter.toJson(rule)

        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "Daily task",
                dueDate = dueDate,
                recurrenceRule = ruleJson
            )
        )

        repository.completeTask(taskId)

        val allTasks = database.taskDao().getAllTasks().first()
        assertEquals(2, allTasks.size)

        val original = allTasks.find { it.id == taskId }!!
        assertTrue(original.isCompleted)

        val newTask = allTasks.find { it.id != taskId }!!
        assertFalse(newTask.isCompleted)
        assertEquals(LocalDate.of(2025, 1, 7).toMillis(), newTask.dueDate)

        val newRule = RecurrenceConverter.fromJson(newTask.recurrenceRule!!)!!
        assertEquals(1, newRule.occurrenceCount)
    }

    @Test
    fun test_completeRecurringTask_maxOccurrences() = runTest {
        val dueDate = LocalDate.of(2025, 1, 6).toMillis()
        val rule = RecurrenceRule(
            type = RecurrenceType.DAILY,
            maxOccurrences = 3,
            occurrenceCount = 3
        )
        val ruleJson = RecurrenceConverter.toJson(rule)

        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "Limited task",
                dueDate = dueDate,
                recurrenceRule = ruleJson
            )
        )

        repository.completeTask(taskId)

        val allTasks = database.taskDao().getAllTasks().first()
        assertEquals(1, allTasks.size) // no new task created
        assertTrue(allTasks[0].isCompleted)
    }

    @Test
    fun test_completeNonRecurringTask() = runTest {
        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "One-time task",
                dueDate = LocalDate.of(2025, 1, 6).toMillis()
            )
        )

        repository.completeTask(taskId)

        val allTasks = database.taskDao().getAllTasks().first()
        assertEquals(1, allTasks.size)
        assertTrue(allTasks[0].isCompleted)
    }

    @Test
    fun test_weeklyRecurrence_multiDay() = runTest {
        val dueDate = LocalDate.of(2025, 1, 6).toMillis() // Monday
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            // Mon, Wed, Fri
            daysOfWeek = listOf(1, 3, 5)
        )
        val ruleJson = RecurrenceConverter.toJson(rule)

        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "MWF task",
                dueDate = dueDate,
                recurrenceRule = ruleJson
            )
        )

        repository.completeTask(taskId)

        val allTasks = database.taskDao().getAllTasks().first()
        assertEquals(2, allTasks.size)

        val newTask = allTasks.find { !it.isCompleted }!!
        assertEquals(LocalDate.of(2025, 1, 8).toMillis(), newTask.dueDate) // Wednesday
    }
}
