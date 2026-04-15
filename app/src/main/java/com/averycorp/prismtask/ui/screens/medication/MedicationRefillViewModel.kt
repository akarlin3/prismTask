package com.averycorp.prismtask.ui.screens.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.MedicationRefillEntity
import com.averycorp.prismtask.data.repository.MedicationRefillRepository
import com.averycorp.prismtask.domain.usecase.RefillCalculator
import com.averycorp.prismtask.domain.usecase.RefillForecast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Medication Refill tracking screen (v1.4.0 V10).
 *
 * Exposes each persisted [MedicationRefillEntity] row alongside its live
 * [RefillForecast] so the UI can render urgency badges without having to
 * run the calculator itself.
 */
@HiltViewModel
class MedicationRefillViewModel
    @Inject
    constructor(
        private val repository: MedicationRefillRepository
    ) : ViewModel() {
        val medications: StateFlow<List<MedicationWithForecast>> =
            repository
                .observeAll()
                .map { list ->
                    val now = System.currentTimeMillis()
                    list.map { row -> MedicationWithForecast(row, RefillCalculator.forecast(row, now)) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fun addMedication(
            name: String,
            pillCount: Int,
            pillsPerDose: Int,
            dosesPerDay: Int,
            pharmacyName: String? = null,
            pharmacyPhone: String? = null
        ) {
            viewModelScope.launch {
                repository.upsert(
                    MedicationRefillEntity(
                        medicationName = name.trim(),
                        pillCount = pillCount,
                        pillsPerDose = pillsPerDose,
                        dosesPerDay = dosesPerDay,
                        pharmacyName = pharmacyName?.takeIf { it.isNotBlank() },
                        pharmacyPhone = pharmacyPhone?.takeIf { it.isNotBlank() },
                        lastRefillDate = System.currentTimeMillis()
                    )
                )
            }
        }

        fun recordDailyDose(refill: MedicationRefillEntity) {
            viewModelScope.launch { repository.applyDailyDose(refill) }
        }

        fun recordRefill(refill: MedicationRefillEntity, newSupply: Int) {
            viewModelScope.launch { repository.applyRefill(refill, newSupply) }
        }

        fun delete(id: Long) {
            viewModelScope.launch { repository.delete(id) }
        }
    }

data class MedicationWithForecast(
    val row: MedicationRefillEntity,
    val forecast: RefillForecast
)
