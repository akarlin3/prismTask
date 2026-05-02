package com.averycorp.prismtask.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.repository.AutomationRuleRepository
import com.averycorp.prismtask.domain.automation.AutomationEngine
import com.averycorp.prismtask.domain.automation.AutomationJsonAdapter
import com.averycorp.prismtask.domain.automation.AutomationTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationRuleListViewModel @Inject constructor(
    private val ruleRepository: AutomationRuleRepository,
    private val engine: AutomationEngine
) : ViewModel() {

    val rules: StateFlow<List<AutomationRuleRow>> = ruleRepository
        .observeAll()
        .map { list -> list.map { it.toRow() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { ruleRepository.setEnabled(id, enabled) }
    }

    fun runNow(id: Long) { engine.runNow(id) }

    fun delete(id: Long) {
        viewModelScope.launch { ruleRepository.delete(id) }
    }

    private fun AutomationRuleEntity.toRow(): AutomationRuleRow {
        val trigger = AutomationJsonAdapter.decodeTrigger(triggerJson)
        return AutomationRuleRow(
            id = id,
            name = name,
            description = description,
            enabled = enabled,
            isBuiltIn = isBuiltIn,
            triggerLabel = triggerLabelOf(trigger),
            triggerIsManual = trigger is AutomationTrigger.Manual,
            lastFiredAt = lastFiredAt,
            usesAi = actionJson.contains("\"type\":\"ai.")
        )
    }

    private fun triggerLabelOf(trigger: AutomationTrigger?): String = when (trigger) {
        null -> "Unparseable trigger"
        is AutomationTrigger.EntityEvent -> "When ${trigger.eventKind}"
        is AutomationTrigger.TimeOfDay -> "Daily at %02d:%02d".format(trigger.hour, trigger.minute)
        AutomationTrigger.Manual -> "Manual run only"
        is AutomationTrigger.Composed -> "After rule #${trigger.parentRuleId}"
    }
}

data class AutomationRuleRow(
    val id: Long,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val triggerLabel: String,
    val triggerIsManual: Boolean,
    val lastFiredAt: Long?,
    val usesAi: Boolean
)
