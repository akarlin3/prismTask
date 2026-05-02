package com.averycorp.prismtask.domain.automation

import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.automation.AutomationCondition.Op
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the v1 condition operators + the sample condition tree
 * from `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md` § A4.
 */
class ConditionEvaluatorTest {
    private val evaluator = ConditionEvaluator()
    private val now = 1_000_000L

    private fun ctxWithTask(
        priority: Int = 0,
        dueDate: Long? = null,
        completedAt: Long? = null,
        tags: List<String> = emptyList(),
        title: String = "x"
    ): EvaluationContext {
        val task = TaskEntity(
            id = 1L,
            title = title,
            priority = priority,
            dueDate = dueDate,
            completedAt = completedAt,
            createdAt = 0,
            updatedAt = 0
        )
        return EvaluationContext(
            event = AutomationEvent.TaskUpdated(taskId = 1L, occurredAt = now),
            task = task,
            taskTags = tags,
            now = now
        )
    }

    @Test fun nullCondition_alwaysTrue() {
        assertTrue(evaluator.evaluate(null, ctxWithTask()))
    }

    @Test fun eqOnString_matches() {
        val ctx = ctxWithTask(title = "buy milk")
        assertTrue(evaluator.evaluate(
            AutomationCondition.Compare(Op.EQ, "task.title", "buy milk"), ctx
        ))
        assertFalse(evaluator.evaluate(
            AutomationCondition.Compare(Op.EQ, "task.title", "wash dishes"), ctx
        ))
    }

    @Test fun gteOnPriority() {
        val ctx = ctxWithTask(priority = 4)
        assertTrue(evaluator.evaluate(
            AutomationCondition.Compare(Op.GTE, "task.priority", 3), ctx
        ))
        assertFalse(evaluator.evaluate(
            AutomationCondition.Compare(Op.GTE, "task.priority", 5), ctx
        ))
    }

    @Test fun ltOnDueDateUsesNowToken() {
        val ctx = ctxWithTask(dueDate = now - 1_000)
        // task.dueDate < @now
        assertTrue(evaluator.evaluate(
            AutomationCondition.Compare(Op.LT, "task.dueDate", mapOf("@now" to null)), ctx
        ))
    }

    @Test fun containsOnTags_caseInsensitive() {
        val ctx = ctxWithTask(tags = listOf("#urgent", "work"))
        assertTrue(evaluator.evaluate(
            AutomationCondition.Compare(Op.CONTAINS, "task.tags", "#URGENT"), ctx
        ))
        assertFalse(evaluator.evaluate(
            AutomationCondition.Compare(Op.CONTAINS, "task.tags", "#chill"), ctx
        ))
    }

    @Test fun existsOnCompletedAt() {
        val withCompletion = ctxWithTask(completedAt = 12345L)
        val withoutCompletion = ctxWithTask(completedAt = null)
        assertTrue(evaluator.evaluate(
            AutomationCondition.Compare(Op.EXISTS, "task.completedAt"), withCompletion
        ))
        assertFalse(evaluator.evaluate(
            AutomationCondition.Compare(Op.EXISTS, "task.completedAt"), withoutCompletion
        ))
    }

    @Test fun samplePromptCondition_matchesUrgentOverdueIncomplete() {
        // (priority>=3 OR tags contains #urgent) AND NOT EXISTS completedAt
        val sample = AutomationCondition.And(listOf(
            AutomationCondition.Or(listOf(
                AutomationCondition.Compare(Op.GTE, "task.priority", 3),
                AutomationCondition.Compare(Op.CONTAINS, "task.tags", "#urgent")
            )),
            AutomationCondition.Not(
                AutomationCondition.Compare(Op.EXISTS, "task.completedAt")
            )
        ))
        // Match: high-priority incomplete
        assertTrue(evaluator.evaluate(sample, ctxWithTask(priority = 4, completedAt = null)))
        // Match: tagged incomplete
        assertTrue(evaluator.evaluate(sample, ctxWithTask(tags = listOf("#urgent"))))
        // No match: high-priority but already completed
        assertFalse(evaluator.evaluate(sample, ctxWithTask(priority = 4, completedAt = 1L)))
        // No match: low priority + no tag
        assertFalse(evaluator.evaluate(sample, ctxWithTask(priority = 1)))
    }

    @Test fun typeMismatch_returnsFalseInsteadOfThrowing() {
        val ctx = ctxWithTask(priority = 3)
        // priority is a number; CONTAINS on a number is a type mismatch.
        // Evaluator should not throw and should return false.
        assertFalse(evaluator.evaluate(
            AutomationCondition.Compare(Op.CONTAINS, "task.priority", "xyz"), ctx
        ))
    }

    @Test fun habitFieldsResolveOnHabitEvent() {
        val habit = HabitEntity(id = 7, name = "meditate", category = "self-care", createdAt = 0, updatedAt = 0)
        val ctx = EvaluationContext(
            event = AutomationEvent.HabitStreakHit(habitId = 7, streak = 14, occurredAt = now),
            habit = habit,
            habitStreak = 14,
            now = now
        )
        val condition = AutomationCondition.And(listOf(
            AutomationCondition.Compare(Op.GTE, "habit.streakCount", 7),
            AutomationCondition.Compare(Op.EQ, "habit.category", "self-care")
        ))
        assertTrue(evaluator.evaluate(condition, ctx))
    }
}
