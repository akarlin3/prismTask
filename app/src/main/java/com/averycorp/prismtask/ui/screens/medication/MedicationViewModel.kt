package com.averycorp.prismtask.ui.screens.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.SelfCareLogEntity
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.repository.MedStepLog
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.notifications.MedicationReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicationViewModel
    @Inject
    constructor(
        private val repository: SelfCareRepository,
        private val medicationPreferences: MedicationPreferences,
        private val reminderScheduler: MedicationReminderScheduler
    ) : ViewModel() {
        private val _editMode = MutableStateFlow(false)
        val editMode: StateFlow<Boolean> = _editMode

        val todayLog: StateFlow<SelfCareLogEntity?> = repository
            .getTodayLog("medication")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        val steps: StateFlow<List<SelfCareStepEntity>> = repository
            .getSteps("medication")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val reminderIntervalMinutes: StateFlow<Int> = medicationPreferences
            .getReminderIntervalMinutes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MedicationPreferences.DEFAULT_INTERVAL)

        val scheduleMode: StateFlow<MedicationScheduleMode> = medicationPreferences
            .getScheduleMode()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MedicationScheduleMode.INTERVAL)

        val specificTimes: StateFlow<Set<String>> = medicationPreferences
            .getSpecificTimes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

        fun setScheduleMode(mode: MedicationScheduleMode) {
            viewModelScope.launch {
                medicationPreferences.setScheduleMode(mode)
                reminderScheduler.rescheduleAll()
            }
        }

        fun addSpecificTime(time: String) {
            viewModelScope.launch {
                medicationPreferences.addSpecificTime(time)
                if (medicationPreferences.getScheduleModeOnce() == MedicationScheduleMode.SPECIFIC_TIMES) {
                    reminderScheduler.scheduleSpecificTimes()
                }
            }
        }

        fun removeSpecificTime(time: String) {
            viewModelScope.launch {
                medicationPreferences.removeSpecificTime(time)
                if (medicationPreferences.getScheduleModeOnce() == MedicationScheduleMode.SPECIFIC_TIMES) {
                    reminderScheduler.scheduleSpecificTimes()
                }
            }
        }

        fun setReminderInterval(minutes: Int) {
            viewModelScope.launch {
                medicationPreferences.setReminderIntervalMinutes(minutes)
            }
        }

        fun toggleEditMode() {
            _editMode.value = !_editMode.value
        }

        fun setTier(tier: String) {
            viewModelScope.launch {
                repository.setTier("medication", tier)
            }
        }

        fun setTierForTime(timeOfDay: String, tier: String?) {
            viewModelScope.launch {
                repository.setTierForTime(timeOfDay, tier)
            }
        }

        fun getTiersByTime(log: SelfCareLogEntity?): Map<String, String> {
            if (log == null) return emptyMap()
            return repository.parseTiersByTime(log.tiersByTime)
        }

        fun logTier(tier: String, note: String) {
            viewModelScope.launch {
                repository.logTier(tier, note)
            }
        }

        fun unlogTier(tier: String) {
            viewModelScope.launch {
                repository.unlogTier(tier)
            }
        }

        fun toggleStep(stepId: String, timeOfDay: String = "", note: String? = null) {
            viewModelScope.launch {
                repository.toggleStep("medication", stepId, note, timeOfDay)
            }
        }

        fun resetToday() {
            viewModelScope.launch {
                repository.resetToday("medication")
            }
        }

        fun addStep(label: String, duration: String, tier: String, note: String, timeOfDay: String) {
            viewModelScope.launch {
                repository.addStep("medication", label, duration, tier, note, "Medications", timeOfDay = timeOfDay)
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

        fun getVisibleSteps(allSteps: List<SelfCareStepEntity>, tier: String): List<SelfCareStepEntity> =
            repository.getVisibleStepsFromEntities(allSteps, tier, "medication")

        fun computeTierTimes(steps: List<SelfCareStepEntity>): Map<String, String> =
            repository.computeTierTimes(steps, "medication")

        fun getCompletedSteps(log: SelfCareLogEntity?): Set<String> {
            if (log == null) return emptySet()
            return getMedStepLogs(log).map { it.id }.toSet()
        }

        fun getMedStepLogs(log: SelfCareLogEntity?): List<MedStepLog> {
            if (log == null) return emptyList()
            return repository.parseMedStepLogs(log.completedSteps)
        }

        /** True if [stepId] has been logged for the given time-of-day block. */
        fun isStepDoneAt(logs: List<MedStepLog>, stepId: String, timeOfDay: String): Boolean =
            repository.isMedLoggedAt(logs, stepId, timeOfDay)

        /**
         * Returns the log entry for a step in a specific time-of-day block, or
         * null if not logged. Prefers an exact-block match over legacy blanks.
         */
        fun getLogForStepAt(logs: List<MedStepLog>, stepId: String, timeOfDay: String): MedStepLog? {
            val exact = logs.firstOrNull { it.id == stepId && it.timeOfDay == timeOfDay }
            if (exact != null) return exact
            return logs.firstOrNull { it.id == stepId && it.timeOfDay.isBlank() }
        }

        fun getSelectedTier(log: SelfCareLogEntity?): String = log?.selectedTier ?: "prescription"

        fun isTierFullyLogged(
            allSteps: List<SelfCareStepEntity>,
            completedSteps: Set<String>,
            tier: String
        ): Boolean {
            val visibleSteps = getVisibleSteps(allSteps, tier)
            return visibleSteps.isNotEmpty() && visibleSteps.all { it.stepId in completedSteps }
        }

        fun getStepsForTier(allSteps: List<SelfCareStepEntity>, tier: String): List<SelfCareStepEntity> =
            getVisibleSteps(allSteps, tier)

        fun getStepsInExactTier(allSteps: List<SelfCareStepEntity>, tier: String): List<SelfCareStepEntity> =
            allSteps.filter { it.tier == tier }
    }
