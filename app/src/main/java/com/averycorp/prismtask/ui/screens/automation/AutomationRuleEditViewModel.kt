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

    private val _composedCandidates = MutableStateFlow<List<ParentRuleOption>>(emptyList())
    val composedCandidates: StateFlow<List<ParentRuleOption>> = _composedCandidates.asStateFlow()

    init {
        viewModelScope.launch { loadComposedCandidates() }
        if (ruleId != null) {
            viewModelScope.launch { loadFromExisting(ruleId) }
        }
    }

    /**
     * Builds the parent-rule picker list for the [TriggerKind.COMPOSED]
     * editor. Excludes:
     *  - the rule currently being edited (no self-reference)
     *  - rules that already point at *this* rule via Composed (would
     *    immediately form a 2-cycle on save)
     *
     * Deeper cycle detection (length 3+) lives in [AutomationEngine] —
     * the user can still author a 3-cycle through the UI, the engine
     * will abort the chain at fire time. Per § A6 of the architecture
     * doc, that is the chosen safety boundary.
     */
    private suspend fun loadComposedCandidates() {
        val all = ruleRepository.getAllOnce()
        val descendants = ruleId?.let { collectComposedDescendants(it, all) } ?: emptySet()
        _composedCandidates.value = all
            .filter { it.id != ruleId && it.id !in descendants }
            .map { ParentRuleOption(it.id, it.name) }
    }

    private fun collectComposedDescendants(
        startId: Long,
        all: List<com.averycorp.prismtask.data.local.entity.AutomationRuleEntity>
    ): Set<Long> {
        val children = mutableSetOf<Long>()
        val stack = ArrayDeque<Long>()
        stack.add(startId)
        val byParent = all.groupBy { row ->
            val trigger = AutomationJsonAdapter.decodeTrigger(row.triggerJson)
            (trigger as? AutomationTrigger.Composed)?.parentRuleId
        }
        while (stack.isNotEmpty()) {
            val parent = stack.removeFirst()
            byParent[parent].orEmpty().forEach { child ->
                if (children.add(child.id)) stack.add(child.id)
            }
        }
        return children
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
            timeHour = (trigger as? AutomationTrigger.TimeOfDay)?.hour
                ?: (trigger as? AutomationTrigger.DayOfWeekTime)?.hour ?: 7,
            timeMinute = (trigger as? AutomationTrigger.TimeOfDay)?.minute
                ?: (trigger as? AutomationTrigger.DayOfWeekTime)?.minute ?: 0,
            daysOfWeek = (trigger as? AutomationTrigger.DayOfWeekTime)?.daysOfWeek
                ?: emptySet(),
            composedParentRuleId = (trigger as? AutomationTrigger.Composed)?.parentRuleId?.toString().orEmpty(),
            condition = ConditionDraft.fromCondition(condition),
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
    fun toggleDayOfWeek(day: String) = _draft.update { d ->
        val next = d.daysOfWeek.toMutableSet().apply { if (!add(day)) remove(day) }
        d.copy(daysOfWeek = next)
    }
    fun setComposedParentRuleId(value: String) = _draft.update { it.copy(composedParentRuleId = value) }

    fun setConditionEnabled(value: Boolean) = _draft.update {
        it.copy(condition = if (value) it.condition ?: ConditionDraft.Leaf() else null)
    }

    fun replaceConditionAt(path: List<Int>, transform: (ConditionDraft) -> ConditionDraft?) =
        _draft.update { it.copy(condition = it.condition?.replaceAt(path, transform)) }

    fun setConditionRoot(node: ConditionDraft?) = _draft.update { it.copy(condition = node) }

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
        is AutomationTrigger.DayOfWeekTime -> TriggerKind.DAY_OF_WEEK_TIME
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
        else -> ActionDraft.Log("($type)")
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
        val DAYS_OF_WEEK = listOf(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY"
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

data class ParentRuleOption(val id: Long, val name: String)

enum class TriggerKind(val label: String) {
    ENTITY_EVENT("When an event happens"),
    TIME_OF_DAY("Daily at a time"),
    DAY_OF_WEEK_TIME("Weekly on chosen days"),
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
    val daysOfWeek: Set<String> = emptySet(),
    val composedParentRuleId: String = "",
    val condition: ConditionDraft? = null,
    val actions: List<ActionDraft> = listOf(ActionDraft.Notify())
) {
    fun toTrigger(): AutomationTrigger? = when (triggerKind) {
        TriggerKind.ENTITY_EVENT -> AutomationTrigger.EntityEvent(entityEventKind)
        TriggerKind.TIME_OF_DAY -> AutomationTrigger.TimeOfDay(timeHour, timeMinute)
        TriggerKind.DAY_OF_WEEK_TIME -> daysOfWeek.takeIf { it.isNotEmpty() }
            ?.let { AutomationTrigger.DayOfWeekTime(it, timeHour, timeMinute) }
        TriggerKind.MANUAL -> AutomationTrigger.Manual
        TriggerKind.COMPOSED -> composedParentRuleId.toLongOrNull()?.let { AutomationTrigger.Composed(it) }
    }

    fun toCondition(): AutomationCondition? = condition?.toCondition()
}

/**
 * Working representation of an [AutomationCondition] for the editor.
 * The disk shape ([AutomationCondition]) is the same sealed-tree
 * structure; the draft mirrors it so the recursive Composable can
 * render and mutate without touching JSON.
 *
 * Path-based mutations: each node in a composite is addressable by a
 * `List<Int>` index path from the root. Empty list = root. Used by the
 * UI's add/remove/wrap callbacks.
 */
sealed class ConditionDraft {
    abstract fun toCondition(): AutomationCondition?

    data class Leaf(
        val field: String = "task.priority",
        val op: AutomationCondition.Op = AutomationCondition.Op.GTE,
        val value: String = ""
    ) : ConditionDraft() {
        override fun toCondition(): AutomationCondition {
            val coerced: Any = value.toLongOrNull() ?: value
            return AutomationCondition.Compare(op, field, coerced)
        }
    }

    data class And(val children: List<ConditionDraft> = listOf(Leaf())) : ConditionDraft() {
        override fun toCondition(): AutomationCondition? = children
            .mapNotNull { it.toCondition() }
            .takeIf { it.isNotEmpty() }
            ?.let { AutomationCondition.And(it) }
    }

    data class Or(val children: List<ConditionDraft> = listOf(Leaf())) : ConditionDraft() {
        override fun toCondition(): AutomationCondition? = children
            .mapNotNull { it.toCondition() }
            .takeIf { it.isNotEmpty() }
            ?.let { AutomationCondition.Or(it) }
    }

    data class Not(val child: ConditionDraft = Leaf()) : ConditionDraft() {
        override fun toCondition(): AutomationCondition? =
            child.toCondition()?.let { AutomationCondition.Not(it) }
    }

    /**
     * Replace the node at `path` with the result of `transform(node)`.
     * Returns the new tree. If `transform` returns null and the parent
     * is composite, the node is removed.
     */
    fun replaceAt(
        path: List<Int>,
        transform: (ConditionDraft) -> ConditionDraft?
    ): ConditionDraft? = if (path.isEmpty()) {
        transform(this)
    } else {
        val head = path.first()
        val tail = path.drop(1)
        when (this) {
            is Leaf -> this
            is And -> {
                val updated = children.toMutableList().apply {
                    val replaced = this[head].replaceAt(tail, transform)
                    if (replaced == null) removeAt(head) else this[head] = replaced
                }
                if (updated.isEmpty()) null else copy(children = updated)
            }
            is Or -> {
                val updated = children.toMutableList().apply {
                    val replaced = this[head].replaceAt(tail, transform)
                    if (replaced == null) removeAt(head) else this[head] = replaced
                }
                if (updated.isEmpty()) null else copy(children = updated)
            }
            is Not -> {
                val replaced = child.replaceAt(tail, transform)
                replaced?.let { copy(child = it) }
            }
        }
    }

    companion object {
        fun fromCondition(c: AutomationCondition?): ConditionDraft? = when (c) {
            null -> null
            is AutomationCondition.Compare -> Leaf(c.field, c.opType, c.value?.toString().orEmpty())
            is AutomationCondition.And -> And(c.children.mapNotNull { fromCondition(it) })
            is AutomationCondition.Or -> Or(c.children.mapNotNull { fromCondition(it) })
            is AutomationCondition.Not -> fromCondition(c.child)?.let { Not(it) }
        }
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
