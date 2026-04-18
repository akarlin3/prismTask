package com.averycorp.prismtask.ui.screens.selfcare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.SelfCareLogEntity
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelfCareViewModel
@Inject
constructor(
    private val repository: SelfCareRepository,
    savedStateHandle: SavedStateHandle,
    private val gson: Gson
) : ViewModel() {
    private val _routineType = MutableStateFlow(
        savedStateHandle.get<String>("routineType") ?: "morning"
    )
    val routineType: StateFlow<String> = _routineType

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayLog: StateFlow<SelfCareLogEntity?> = _routineType
        .flatMapLatest { type ->
            repository.getTodayLog(type)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val steps: StateFlow<List<SelfCareStepEntity>> = _routineType
        .flatMapLatest { type ->
            repository.getSteps(type)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun switchRoutine(type: String) {
        _routineType.value = type
    }

    fun toggleEditMode() {
        _editMode.value = !_editMode.value
    }

    fun setTier(tier: String) {
        viewModelScope.launch {
            repository.setTier(_routineType.value, tier)
        }
    }

    fun toggleStep(stepId: String) {
        viewModelScope.launch {
            repository.toggleStep(_routineType.value, stepId)
        }
    }

    fun resetToday() {
        viewModelScope.launch {
            repository.resetToday(_routineType.value)
        }
    }

    fun addStep(label: String, duration: String, tier: String, note: String, phase: String) {
        viewModelScope.launch {
            repository.addStep(_routineType.value, label, duration, tier, note, phase)
        }
    }

    fun updateStep(step: SelfCareStepEntity) {
        viewModelScope.launch {
            repository.updateStep(step)
        }
    }

    fun deleteStep(step: SelfCareStepEntity) {
        viewModelScope.launch {
            repository.deleteStep(step)
        }
    }

    fun moveStep(step: SelfCareStepEntity, direction: Int) {
        viewModelScope.launch {
            repository.moveStep(step, direction)
        }
    }

    fun sortByPhaseOrder(phaseOrder: List<String>) {
        viewModelScope.launch {
            repository.sortStepsByPhaseOrder(_routineType.value, phaseOrder)
        }
    }

    fun getDefaultPhases(): List<String> = when (_routineType.value) {
        "morning" -> listOf("Skincare", "Hygiene", "Grooming")
        "housework" -> listOf("Kitchen", "Living Areas", "Bathroom", "Laundry")
        else -> listOf("Wash", "Skincare", "Hygiene", "Sleep")
    }

    fun getCurrentPhases(steps: List<SelfCareStepEntity>): List<String> {
        val defaultPhases = getDefaultPhases()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (step in steps) {
            if (seen.add(step.phase)) result.add(step.phase)
        }
        // Add any default phases not present in steps
        for (phase in defaultPhases) {
            if (seen.add(phase)) result.add(phase)
        }
        return result
    }

    fun computeTierTimes(steps: List<SelfCareStepEntity>): Map<String, String> =
        repository.computeTierTimes(steps, _routineType.value)

    fun getVisibleSteps(allSteps: List<SelfCareStepEntity>, tier: String): List<SelfCareStepEntity> =
        repository.getVisibleStepsFromEntities(allSteps, tier, _routineType.value)

    fun getPhaseGroupedSteps(allSteps: List<SelfCareStepEntity>): List<Pair<String, List<SelfCareStepEntity>>> =
        repository.getPhaseGroupedSteps(allSteps, _routineType.value)

    fun getCompletedSteps(log: SelfCareLogEntity?): Set<String> {
        if (log == null) return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(log.completedSteps, type).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun getSelectedTier(log: SelfCareLogEntity?): String = log?.selectedTier ?: SelfCareRoutines.getTierOrder(_routineType.value).let {
        if (it.size >= 2) it[it.size - 2] else it.first()
    }
}
