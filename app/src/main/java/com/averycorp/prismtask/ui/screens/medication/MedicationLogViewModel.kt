package com.averycorp.prismtask.ui.screens.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Medication Log screen — day-by-day history of every
 * dose the user has recorded.
 *
 * Post v1.4 medication-top-level refactor (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §6.1), reads exclusively from
 * [MedicationRepository]. Each [MedicationLogDay] is the dose history
 * for one local date, with doses pre-grouped by slot-key for the UI's
 * morning / afternoon / evening / night sections.
 */
@HiltViewModel
class MedicationLogViewModel
@Inject
constructor(
    private val repository: MedicationRepository
) : ViewModel() {
    val days: StateFlow<List<MedicationLogDay>> = combine(
        repository.observeAll(),
        repository.observeAllDoses()
    ) { meds, doses ->
        val medsById = meds.associateBy { it.id }
        doses
            .groupBy { it.takenDateLocal }
            .map { (date, dosesForDate) ->
                MedicationLogDay(
                    date = date,
                    doses = dosesForDate.sortedBy { it.takenAt },
                    medicationsById = medsById
                )
            }
            .sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/**
 * One day's worth of dose history. Embeds the `medicationsById` lookup
 * so UI code can resolve names without needing its own StateFlow.
 */
data class MedicationLogDay(
    /** ISO yyyy-MM-dd in the device's local timezone. */
    val date: String,
    val doses: List<MedicationDoseEntity>,
    val medicationsById: Map<Long, MedicationEntity>
) {
    val loggedCount: Int get() = doses.size

    /**
     * Groups [doses] by slot-key. Preserves the input ordering within
     * each group so the earliest dose shows first. Slot keys are
     * opaque strings — callers decide how to label the sections.
     */
    val dosesBySlot: Map<String, List<MedicationDoseEntity>>
        get() = doses.groupBy { it.slotKey }

    fun medicationName(dose: MedicationDoseEntity): String {
        val med = medicationsById[dose.medicationId]
        return med?.displayLabel ?: med?.name ?: "Unknown"
    }
}
