package com.averycorp.prismtask.ui.screens.settings.sections.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.MedicationReminderModePrefs
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.notifications.MedicationClockRescheduler
import com.averycorp.prismtask.notifications.MedicationIntervalRescheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [MedicationReminderModeSection]. Reads + writes the global
 * default through [UserPreferencesDataStore] and triggers an immediate
 * pass through both reminder reschedulers — INTERVAL and CLOCK alarms
 * are owned by separate components, and a global flip in either
 * direction needs to cancel the side that's no longer active and
 * re-arm the side that is.
 */
@HiltViewModel
class MedicationReminderModeSettingsViewModel
@Inject
constructor(
    private val userPreferences: UserPreferencesDataStore,
    private val intervalRescheduler: MedicationIntervalRescheduler,
    private val clockRescheduler: MedicationClockRescheduler
) : ViewModel() {

    val prefs: StateFlow<MedicationReminderModePrefs> =
        userPreferences.medicationReminderModeFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MedicationReminderModePrefs()
        )

    suspend fun save(prefs: MedicationReminderModePrefs) {
        userPreferences.setMedicationReminderMode(prefs)
        viewModelScope.launch { intervalRescheduler.rescheduleAll() }
        viewModelScope.launch { clockRescheduler.rescheduleAll() }
    }
}
