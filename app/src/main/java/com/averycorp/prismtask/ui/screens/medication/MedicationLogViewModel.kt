package com.averycorp.prismtask.ui.screens.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.SelfCareLogEntity
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.repository.MedStepLog
import com.averycorp.prismtask.data.repository.SelfCareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MedicationLogViewModel @Inject constructor(
    private val repository: SelfCareRepository
) : ViewModel() {

    val logs: StateFlow<List<SelfCareLogEntity>> = repository.getLogsForRoutine("medication")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val steps: StateFlow<List<SelfCareStepEntity>> = repository.getSteps("medication")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun parseMedStepLogs(log: SelfCareLogEntity): List<MedStepLog> =
        repository.parseMedStepLogs(log.completedSteps)

    fun parseTiersByTime(log: SelfCareLogEntity): Map<String, String> =
        repository.parseTiersByTime(log.tiersByTime)
}
