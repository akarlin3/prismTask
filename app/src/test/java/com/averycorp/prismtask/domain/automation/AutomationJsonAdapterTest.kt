package com.averycorp.prismtask.domain.automation

import com.averycorp.prismtask.domain.automation.AutomationCondition.Op
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationJsonAdapterTest {

    @Test fun trigger_entityEvent_roundTrip() {
        val original = AutomationTrigger.EntityEvent("TaskCompleted")
        val json = AutomationJsonAdapter.encodeTrigger(original)
        assertTrue(json.contains("\"type\":\"ENTITY_EVENT\""))
        val decoded = AutomationJsonAdapter.decodeTrigger(json) as AutomationTrigger.EntityEvent
        assertEquals(original, decoded)
    }

    @Test fun trigger_timeOfDay_roundTrip() {
        val original = AutomationTrigger.TimeOfDay(7, 30)
        val decoded = AutomationJsonAdapter.decodeTrigger(
            AutomationJsonAdapter.encodeTrigger(original)
        )
        assertEquals(original, decoded)
    }

    @Test fun trigger_manual_roundTrip() {
        val decoded = AutomationJsonAdapter.decodeTrigger(
            AutomationJsonAdapter.encodeTrigger(AutomationTrigger.Manual)
        )
        assertTrue(decoded is AutomationTrigger.Manual)
    }

    @Test fun trigger_composed_roundTrip() {
        val original = AutomationTrigger.Composed(parentRuleId = 42L)
        val decoded = AutomationJsonAdapter.decodeTrigger(
            AutomationJsonAdapter.encodeTrigger(original)
        )
        assertEquals(original, decoded)
    }

    @Test fun trigger_malformed_returnsNullInsteadOfThrowing() {
        val decoded = AutomationJsonAdapter.decodeTrigger("{nope}")
        assertNull(decoded)
    }

    @Test fun condition_complexTree_roundTrip() {
        val original = AutomationCondition.And(listOf(
            AutomationCondition.Or(listOf(
                AutomationCondition.Compare(Op.GTE, "task.priority", 3),
                AutomationCondition.Compare(Op.CONTAINS, "task.tags", "#urgent")
            )),
            AutomationCondition.Not(
                AutomationCondition.Compare(Op.EXISTS, "task.completedAt")
            )
        ))
        val json = AutomationJsonAdapter.encodeCondition(original)
        val decoded = AutomationJsonAdapter.decodeCondition(json)
        assertEquals(original, decoded)
    }

    @Test fun condition_null_encodesToNull() {
        assertNull(AutomationJsonAdapter.encodeCondition(null))
        assertNull(AutomationJsonAdapter.decodeCondition(null))
    }

    @Test fun action_notify_roundTrip() {
        val original = listOf(
            AutomationAction.Notify(title = "T", body = "B", priority = 1),
            AutomationAction.LogMessage("hello")
        )
        val json = AutomationJsonAdapter.encodeActions(original)
        val decoded = AutomationJsonAdapter.decodeActions(json)
        assertEquals(original, decoded)
    }

    @Test fun action_aiSummarize_roundTrip() {
        val original = listOf(
            AutomationAction.AiSummarize(scope = "recent_completions", maxItems = 50)
        )
        val decoded = AutomationJsonAdapter.decodeActions(
            AutomationJsonAdapter.encodeActions(original)
        )
        assertEquals(original, decoded)
    }

    @Test fun action_mutateTask_roundTrip() {
        val original = listOf(
            AutomationAction.MutateTask(updates = mapOf("priority" to 4, "isFlagged" to true))
        )
        val decoded = AutomationJsonAdapter.decodeActions(
            AutomationJsonAdapter.encodeActions(original)
        ) as List<AutomationAction.MutateTask>
        assertEquals(4L, (decoded[0].updates["priority"] as Number).toLong())
        assertEquals(true, decoded[0].updates["isFlagged"])
    }
}
