package com.averycorp.prismtask.domain.automation.handlers

import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.data.repository.MedicationRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.automation.ActionResult
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationEvent
import com.averycorp.prismtask.domain.automation.EvaluationContext
import com.averycorp.prismtask.domain.automation.ExecutionContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-handler unit tests for the three Mutate*ActionHandler classes
 * tightened in the D-series silent-failure PR. Covers:
 *  * happy path (write lands on repository)
 *  * unknown field returns Error
 *  * type-mismatched value returns Error
 *  * no-op (next == task) does not write but reports honestly
 *
 * See `docs/audits/D_AUTOMATION_ACTION_SILENT_FAILURE_AUDIT.md`.
 */
class SimpleActionHandlersTest {

    private val sampleRule = AutomationRuleEntity(
        id = 42L,
        name = "Test rule",
        triggerJson = "{}",
        actionJson = "{}",
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun ctx(
        event: AutomationEvent,
        task: TaskEntity? = null,
        habit: HabitEntity? = null,
        medication: MedicationEntity? = null
    ): ExecutionContext = ExecutionContext(
        rule = sampleRule,
        event = event,
        evaluation = EvaluationContext(
            event = event,
            task = task,
            habit = habit,
            medication = medication
        ),
        depth = 0,
        lineage = emptySet(),
        parentLogId = null
    )

    private fun task(priority: Int = 0, title: String = "T") = TaskEntity(
        id = 7L,
        title = title,
        priority = priority
    )

    // ---------------------------------------------------------------------
    // mutate.task — the handler at the heart of the AVD Stage 7 reproduction.
    // ---------------------------------------------------------------------

    @Test
    fun `mutate_task happy path writes priority and reports updated`() = runTest {
        val taskRepository: TaskRepository = mockk(relaxed = true)
        val tagDao: TagDao = mockk(relaxed = true)
        val handler = MutateTaskActionHandler(taskRepository, tagDao)

        val seeded = task(priority = 1)
        val action = AutomationAction.MutateTask(updates = mapOf("priority" to 4))

        val result = handler.execute(
            action,
            ctx(AutomationEvent.TaskCreated(taskId = 7L, occurredAt = 0L), task = seeded)
        )

        assertTrue("expected Ok, got $result", result is ActionResult.Ok)
        val captured = slot<TaskEntity>()
        coVerify { taskRepository.updateTask(capture(captured)) }
        assertEquals(4, captured.captured.priority)
        assertEquals(seeded.id, captured.captured.id)
    }

    @Test
    fun `mutate_task unknown field returns Error and skips write`() = runTest {
        val taskRepository: TaskRepository = mockk(relaxed = true)
        val tagDao: TagDao = mockk(relaxed = true)
        val handler = MutateTaskActionHandler(taskRepository, tagDao)
        val action = AutomationAction.MutateTask(
            updates = mapOf("priority" to 4, "bogusField" to "x")
        )

        val result = handler.execute(
            action,
            ctx(AutomationEvent.TaskCreated(taskId = 7L, occurredAt = 0L), task = task())
        )

        assertTrue("expected Error, got $result", result is ActionResult.Error)
        result as ActionResult.Error
        assertTrue(result.reason.contains("unknown field"))
        assertTrue(result.reason.contains("bogusField"))
        coVerify(exactly = 0) { taskRepository.updateTask(any()) }
    }

    @Test
    fun `mutate_task wrong-type priority returns Error and skips write`() = runTest {
        val taskRepository: TaskRepository = mockk(relaxed = true)
        val tagDao: TagDao = mockk(relaxed = true)
        val handler = MutateTaskActionHandler(taskRepository, tagDao)
        val action = AutomationAction.MutateTask(updates = mapOf("priority" to "high"))

        val result = handler.execute(
            action,
            ctx(AutomationEvent.TaskCreated(taskId = 7L, occurredAt = 0L), task = task())
        )

        assertTrue("expected Error, got $result", result is ActionResult.Error)
        result as ActionResult.Error
        assertTrue(result.reason.contains("priority"))
        assertTrue(result.reason.contains("Number"))
        coVerify(exactly = 0) { taskRepository.updateTask(any()) }
    }

    @Test
    fun `mutate_task no-op does not write but reports no-op`() = runTest {
        val taskRepository: TaskRepository = mockk(relaxed = true)
        val tagDao: TagDao = mockk(relaxed = true)
        val handler = MutateTaskActionHandler(taskRepository, tagDao)
        val seeded = task(priority = 4)
        val action = AutomationAction.MutateTask(updates = mapOf("priority" to 4))

        val result = handler.execute(
            action,
            ctx(AutomationEvent.TaskCreated(taskId = 7L, occurredAt = 0L), task = seeded)
        )

        assertTrue(result is ActionResult.Ok)
        result as ActionResult.Ok
        assertTrue(
            "message should signal no-op, was: ${result.message}",
            result.message?.contains("no-op") == true
        )
        coVerify(exactly = 0) { taskRepository.updateTask(any()) }
    }

    @Test
    fun `mutate_task missing task on event returns Skipped`() = runTest {
        val taskRepository: TaskRepository = mockk(relaxed = true)
        val tagDao: TagDao = mockk(relaxed = true)
        val handler = MutateTaskActionHandler(taskRepository, tagDao)
        val action = AutomationAction.MutateTask(updates = mapOf("priority" to 4))

        val result = handler.execute(
            action,
            ctx(AutomationEvent.TimeTick(hour = 9, minute = 0, occurredAt = 0L))
        )

        assertTrue(result is ActionResult.Skipped)
    }

    // ---------------------------------------------------------------------
    // mutate.habit
    // ---------------------------------------------------------------------

    @Test
    fun `mutate_habit isArchived true archives the habit`() = runTest {
        val habitRepository: HabitRepository = mockk(relaxed = true)
        val handler = MutateHabitActionHandler(habitRepository)
        val habit = HabitEntity(id = 11L, name = "Stretch")
        val action = AutomationAction.MutateHabit(updates = mapOf("isArchived" to true))

        val result = handler.execute(
            action,
            ctx(AutomationEvent.HabitCompleted(habitId = 11L, date = "2026-05-06", occurredAt = 0L), habit = habit)
        )

        assertTrue(result is ActionResult.Ok)
        coVerify { habitRepository.archiveHabit(11L) }
    }

    @Test
    fun `mutate_habit unknown field returns Error`() = runTest {
        val habitRepository: HabitRepository = mockk(relaxed = true)
        val handler = MutateHabitActionHandler(habitRepository)
        val habit = HabitEntity(id = 11L, name = "Stretch")
        val action = AutomationAction.MutateHabit(updates = mapOf("name" to "Run"))

        val result = handler.execute(
            action,
            ctx(AutomationEvent.HabitCompleted(habitId = 11L, date = "2026-05-06", occurredAt = 0L), habit = habit)
        )

        assertTrue("expected Error, got $result", result is ActionResult.Error)
        result as ActionResult.Error
        assertTrue(result.reason.contains("unknown field"))
        assertTrue(result.reason.contains("name"))
    }

    @Test
    fun `mutate_habit wrong-type isArchived returns Error`() = runTest {
        val habitRepository: HabitRepository = mockk(relaxed = true)
        val handler = MutateHabitActionHandler(habitRepository)
        val habit = HabitEntity(id = 11L, name = "Stretch")
        val action = AutomationAction.MutateHabit(updates = mapOf("isArchived" to "yes"))

        val result = handler.execute(
            action,
            ctx(AutomationEvent.HabitCompleted(habitId = 11L, date = "2026-05-06", occurredAt = 0L), habit = habit)
        )

        assertTrue(result is ActionResult.Error)
    }

    // ---------------------------------------------------------------------
    // mutate.medication
    // ---------------------------------------------------------------------

    @Test
    fun `mutate_medication name rename writes through repository`() = runTest {
        val medicationRepository: MedicationRepository = mockk(relaxed = true)
        val handler = MutateMedicationActionHandler(medicationRepository)
        val med = MedicationEntity(id = 31L, name = "Old", dosesPerDay = 1)
        val action = AutomationAction.MutateMedication(updates = mapOf("name" to "New"))

        val result = handler.execute(
            action,
            ctx(
                AutomationEvent.MedicationLogged(medicationId = 31L, slotKey = "morning", occurredAt = 0L),
                medication = med
            )
        )

        assertTrue("expected Ok, got $result", result is ActionResult.Ok)
        val captured = slot<MedicationEntity>()
        coVerify { medicationRepository.update(capture(captured)) }
        assertEquals("New", captured.captured.name)
    }

    @Test
    fun `mutate_medication unknown field returns Error`() = runTest {
        val medicationRepository: MedicationRepository = mockk(relaxed = true)
        val handler = MutateMedicationActionHandler(medicationRepository)
        val med = MedicationEntity(id = 31L, name = "Old", dosesPerDay = 1)
        val action = AutomationAction.MutateMedication(
            updates = mapOf("doseTaken" to true)
        )

        val result = handler.execute(
            action,
            ctx(
                AutomationEvent.MedicationLogged(medicationId = 31L, slotKey = "morning", occurredAt = 0L),
                medication = med
            )
        )

        assertTrue("expected Error, got $result", result is ActionResult.Error)
        result as ActionResult.Error
        assertTrue(result.reason.contains("unknown field"))
        assertTrue(result.reason.contains("doseTaken"))
        assertTrue(result.reason.contains("apply.batch"))
    }
}
