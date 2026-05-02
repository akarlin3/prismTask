package com.averycorp.prismtask.ui.screens.automation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.repository.AutomationRuleRepository
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationCondition
import com.averycorp.prismtask.domain.automation.AutomationCondition.Op
import com.averycorp.prismtask.domain.automation.AutomationJsonAdapter
import com.averycorp.prismtask.domain.automation.AutomationTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [AutomationRuleEditScreen]. Holds the working draft of a
 * rule as plain composables-friendly state ([RuleDraft]) and converts to
 * [AutomationTrigger] / [AutomationCondition] / [AutomationAction] only
 * at save time.
 *
 * v1.1 scope: single-leaf Compare condition. Storage supports the full
 * AND/OR/NOT tree (a power user importing a JSON-authored rule sees it
 * preserved on disk), but the visual builder is block-not-graph for
 * mobile (per § A7 of the architecture doc).
 */
@HiltViewModel
class AutomationRuleEditViewModel @Inject constructor(
    private val ruleRepository: AutomationRuleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ruleId: Long? = savedStateHandle.get<String?>("ruleId")?.toLongOrNull()

    private val _draft = MutableStateFlow(RuleDraft())
    val draft: StateFlow<RuleDraft> = _draft.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        if (ruleId != null) {
            viewModelScope.launch { loadFromExisting(ruleId) }
        }
    }

    private suspend fun loadFromExisting(id: Long) {
        val row = ruleRepository.getByIdOnce(id) ?: return
        val trigger = AutomationJsonAdapter.decodeTrigger(row.triggerJson)
        val condition = AutomationJsonAdapter.decodeCondition(row.conditionJson)
        val actions = AutomationJsonAdapter.decodeActions(row.actionJson)
        _draft.value = RuleDraft(
            id = row.id,
            name = row.name,
            description = row.description.orEmpty(),
            enabled = row.enabled,
            isBuiltIn = row.isBuiltIn,
            triggerKind = triggerKindOf(trigger),
            entityEventKind = (trigger as? AutomationTrigger.EntityEvent)?.eventKind ?: ENTITY_EVENT_KINDS.first(),
            timeHour = (trigger as? AutomationTrigger.TimeOfDay)?.hour ?: 7,
            timeMinute = (trigger as? AutomationTrigger.TimeOfDay)?.minute ?: 0,
            composedParentRuleId = (trigger as? AutomationTrigger.Composed)?.parentRuleId?.toString().orEmpty(),
            conditionEnabled = condition is AutomationCondition.Compare,
            conditionField = (condition as? AutomationCondition.Compare)?.field ?: CONDITION_FIELDS.first(),
            conditionOp = (condition as? AutomationCondition.Compare)?.opType ?: Op.GTE,
            conditionValue = (condition as? AutomationCondition.Compare)?.value?.toString().orEmpty(),
            actions = actions.mapNotNull { it.toDraft() }.ifEmpty { listOf(ActionDraft.Notify()) }
        )
    }

    fun setName(value: String) = _draft.update { it.copy(name = value) }
    fun setDescription(value: String) = _draft.update { it.copy(description = value) }
    fun setEnabled(value: Boolean) = _draft.update { it.copy(enabled = value) }

    fun setTriggerKind(kind: TriggerKind) = _draft.update { it.copy(triggerKind = kind) }
    fun setEntityEventKind(value: String) = _draft.update { it.copy(entityEventKind = value) }
    fun setTimeHour(hour: Int) = _draft.update { it.copy(timeHour = hour.coerceIn(0, 23)) }
    fun setTimeMinute(minute: Int) = _draft.update { it.copy(timeMinute = minute.coerceIn(0, 59)) }
    fun setComposedParentRuleId(value: String) = _draft.update { it.copy(composedParentRuleId = value) }

    fun setConditionEnabled(value: Boolean) = _draft.update { it.copy(conditionEnabled = value) }
    fun setConditionField(value: String) = _draft.update { it.copy(conditionField = value) }
    fun setConditionOp(value: Op) = _draft.update { it.copy(conditionOp = value) }
    fun setConditionValue(value: String) = _draft.update { it.copy(conditionValue = value) }

    fun addAction() = _draft.update { it.copy(actions = it.actions + ActionDraft.Notify()) }
    fun removeAction(index: Int) = _draft.update { d ->
        d.copy(actions = d.actions.toMutableList().also { if (it.size > 1) it.removeAt(index) })
    }
    fun replaceAction(index: Int, action: ActionDraft) = _draft.update { d ->
        d.copy(actions = d.actions.toMutableList().also { it[index] = action })
    }

    fun save() {
        val current = _draft.value
        val trigger = current.toTrigger() ?: return
        val condition = current.toCondition()
        val actions = current.actions.map { it.toAction() }
        viewModelScope.launch {
            if (ruleId != null) {
                val existing = ruleRepository.getByIdOnce(ruleId) ?: return@launch
                ruleRepository.update(
                    existing.copy(
                        name = current.name.ifBlank { "Untitled Rule" },
                        description = current.description.ifBlank { null },
                        enabled = current.enabled,
                        triggerJson = AutomationJsonAdapter.encodeTrigger(trigger),
                        conditionJson = AutomationJsonAdapter.encodeCondition(condition),
                        actionJson = AutomationJsonAdapter.encodeActions(actions)
                    )
                )
            } else {
                ruleRepository.create(
                    name = current.name.ifBlank { "Untitled Rule" },
                    description = current.description.ifBlank { null },
                    trigger = trigger,
                    condition = condition,
                    actions = actions,
                    enabled = current.enabled,
                    isBuiltIn = false
                )
            }
            _saved.value = true
        }
    }

    private fun triggerKindOf(t: AutomationTrigger?): TriggerKind = when (t) {
        is AutomationTrigger.EntityEvent -> TriggerKind.ENTITY_EVENT
        is AutomationTrigger.TimeOfDay -> TriggerKind.TIME_OF_DAY
        AutomationTrigger.Manual -> TriggerKind.MANUAL
        is AutomationTrigger.Composed -> TriggerKind.COMPOSED
        null -> TriggerKind.ENTITY_EVENT
    }

    private fun AutomationAction.toDraft(): ActionDraft? = when (this) {
        is AutomationAction.Notify -> ActionDraft.Notify(title, body)
        is AutomationAction.LogMessage -> ActionDraft.Log(message)
        is AutomationAction.MutateTask -> ActionDraft.MutateTaskPriority(
            priority = (updates["priority"] as? Number)?.toInt() ?: 3
        )
        is AutomationAction.ScheduleTimer -> ActionDraft.Timer(durationMinutes, mode)
        // Other action shapes are storage-supported but not rendered in
        // the v1.1 visual builder. Drop into the draft list as a Log
        // placeholder so users at least see something for them.
        else -> ActionDraft.Log("(${type})")
    }

    companion object {
        val ENTITY_EVENT_KINDS = listOf(
            "TaskCreated",
            "TaskUpdated",
            "TaskCompleted",
            "TaskDeleted",
            "HabitCompleted",
            "HabitStreakHit",
            "MedicationLogged"
        )
        val CONDITION_FIELDS = listOf(
            "task.priority",
            "task.dueDate",
            "task.completedAt",
            "task.tags",
            "task.lifeCategory",
            "task.isFlagged",
            "habit.streakCount",
            "habit.category",
            "event.occurredAt"
        )
    }
}

enum class TriggerKind(val label: String) {
    ENTITY_EVENT("When an event happens"),
    TIME_OF_DAY("Daily at a time"),
    MANUAL("Run only when I tap"),
    COMPOSED("After another rule fires")
}

data class RuleDraft(
    val id: Long? = null,
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val triggerKind: TriggerKind = TriggerKind.ENTITY_EVENT,
    val entityEventKind: String = "TaskCreated",
    val timeHour: Int = 7,
    val timeMinute: Int = 0,
    val composedParentRuleId: String = "",
    val conditionEnabled: Boolean = false,
    val conditionField: String = "task.priority",
    val conditionOp: Op = Op.GTE,
    val conditionValue: String = "",
    val actions: List<ActionDraft> = listOf(ActionDraft.Notify())
) {
    fun toTrigger(): AutomationTrigger? = when (triggerKind) {
        TriggerKind.ENTITY_EVENT -> AutomationTrigger.EntityEvent(entityEventKind)
        TriggerKind.TIME_OF_DAY -> AutomationTrigger.TimeOfDay(timeHour, timeMinute)
        TriggerKind.MANUAL -> AutomationTrigger.Manual
        TriggerKind.COMPOSED -> composedParentRuleId.toLongOrNull()?.let { AutomationTrigger.Composed(it) }
    }

    fun toCondition(): AutomationCondition? {
        if (!conditionEnabled) return null
        // Numeric coercion: when the value looks like a number, parse it
        // as Long; otherwise keep as String. Matters for the evaluator's
        // numeric-comparison fast path.
        val coerced: Any = conditionValue.toLongOrNull() ?: conditionValue
        return AutomationCondition.Compare(conditionOp, conditionField, coerced)
    }
}

sealed class ActionDraft {
    abstract fun toAction(): AutomationAction
    data class Notify(val title: String = "Notification", val body: String = "") : ActionDraft() {
        override fun toAction(): AutomationAction = AutomationAction.Notify(title, body)
    }
    data class Log(val message: String = "") : ActionDraft() {
        override fun toAction(): AutomationAction = AutomationAction.LogMessage(message)
    }
    data class MutateTaskPriority(val priority: Int = 3) : ActionDraft() {
        override fun toAction(): AutomationAction =
            AutomationAction.MutateTask(updates = mapOf("priority" to priority))
    }
    data class Timer(val durationMinutes: Int = 25, val mode: String = "WORK") : ActionDraft() {
        override fun toAction(): AutomationAction =
            AutomationAction.ScheduleTimer(durationMinutes = durationMinutes, mode = mode)
    }
}
