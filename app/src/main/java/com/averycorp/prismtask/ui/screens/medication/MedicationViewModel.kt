package com.averycorp.prismtask.ui.screens.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.MedicationRepository
import com.averycorp.prismtask.data.repository.MedicationSlotRepository
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.domain.model.medication.MedicationTier
import com.averycorp.prismtask.domain.model.medication.TierSource
import com.averycorp.prismtask.domain.usecase.MedicationTierComputer
import com.averycorp.prismtask.ui.screens.medication.components.MedicationSlotSelection
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Surface the Main Medication screen needs to render one row per slot per
 * day with auto-computed achieved tier + per-medication toggle affordance.
 */
data class MedicationSlotTodayState(
    val slot: MedicationSlotEntity,
    val medications: List<MedicationEntity>,
    val takenMedicationIds: Set<Long>,
    val achievedTier: AchievedTier,
    val isUserSet: Boolean,
    /**
     * User-claimed wall-clock for when the slot was actually taken, if
     * the user has explicitly set it via long-press. Sourced from the
     * first per-slot tier-state row (all rows for the slot carry the
     * same intended_time). NULL means no user override — UI should
     * treat the row's logged_at as the de-facto taken time.
     */
    val intendedTime: Long? = null,
    /** Earliest tier-state logged_at across this slot's rows, or null. */
    val loggedAt: Long? = null
) {
    /**
     * True when the user backdated this slot — i.e. intended_time was
     * set explicitly to a moment that meaningfully differs from the
     * database write. The 60s tolerance avoids a clock-icon flicker for
     * the trivial gap between "tap to mark" and "row landed".
     */
    val isBacklogged: Boolean
        get() {
            val intended = intendedTime ?: return false
            val logged = loggedAt ?: return false
            return kotlin.math.abs(logged - intended) > 60_000L
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicationViewModel
@Inject
constructor(
    private val medicationRepository: MedicationRepository,
    private val slotRepository: MedicationSlotRepository,
    private val taskBehaviorPreferences: TaskBehaviorPreferences
) : ViewModel() {
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode

    val medications: StateFlow<List<MedicationEntity>> = medicationRepository
        .observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val activeSlots: StateFlow<List<MedicationSlotEntity>> = slotRepository
        .observeActiveSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** ISO local date scoped by the user's day-start hour preference. */
    val todayDate: StateFlow<String> = taskBehaviorPreferences
        .getDayStartHour()
        .flatMapLatest { hour ->
            MutableStateFlow(DayBoundary.currentLocalDateString(hour))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            DayBoundary.currentLocalDateString(0)
        )

    private val todaysDoses: StateFlow<List<MedicationDoseEntity>> = todayDate
        .flatMapLatest { date -> medicationRepository.observeDosesForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val todaysTierStates: StateFlow<List<MedicationTierStateEntity>> = todayDate
        .flatMapLatest { date -> slotRepository.observeTierStatesForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /**
     * Reactive list of `(slot, meds, taken, achieved)` projections — the
     * screen renders one card per element. Junction lookups happen inside
     * `combine`'s flatMapLatest loop rather than at render time so the
     * `StateFlow` stays the source of truth.
     */
    val slotTodayStates: StateFlow<List<MedicationSlotTodayState>> = combine(
        activeSlots,
        medications,
        todaysDoses,
        todaysTierStates
    ) { slots, meds, doses, tierStates ->
        // For each slot, resolve its linked meds via the junction, then
        // compute the auto-tier based on today's doses. A user-set tier
        // in the DB sticks regardless of what auto-compute says.
        slots.map { slot ->
            val linkedMedIds = slotRepository.getMedicationIdsForSlotOnce(slot.id).toSet()
            val linkedMeds = meds.filter { it.id in linkedMedIds }
            val takenIds = doses.asSequence()
                .filter { it.medicationId in linkedMedIds && it.slotKey == slot.id.toString() }
                .map { it.medicationId }
                .toSet()
            val computed = MedicationTierComputer.computeAchievedTier(
                medsForSlot = linkedMeds.associate { it.id to MedicationTier.fromStorage(it.tier) },
                markedTaken = takenIds
            )
            val userRow = tierStates.firstOrNull { it.slotId == slot.id && it.isUserSetSource() }
            val displayTier = userRow?.let { AchievedTier.fromStorage(it.tier) } ?: computed
            // Intended/logged times are recorded per-(med, slot, date) but
            // the user edits them at slot granularity, so all per-slot rows
            // carry the same value. Read from any row for the slot.
            val anySlotRow = tierStates.firstOrNull { it.slotId == slot.id }
            MedicationSlotTodayState(
                slot = slot,
                medications = linkedMeds,
                takenMedicationIds = takenIds,
                achievedTier = displayTier,
                isUserSet = userRow != null,
                intendedTime = anySlotRow?.intendedTime,
                loggedAt = anySlotRow?.loggedAt
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private fun MedicationTierStateEntity.isUserSetSource(): Boolean =
        TierSource.fromStorage(this.tierSource) == TierSource.USER_SET

    fun toggleEditMode() {
        _editMode.value = !_editMode.value
    }

    /**
     * Toggle a single medication's dose for the current slot on today's
     * date. Also refreshes the `medication_tier_states` row via
     * auto-compute so the achieved-tier indicator updates reactively —
     * unless the row is `USER_SET`, in which case the user override wins.
     */
    fun toggleDose(slot: MedicationSlotEntity, medication: MedicationEntity) {
        viewModelScope.launch {
            val already = todaysDoses.value.firstOrNull {
                it.medicationId == medication.id && it.slotKey == slot.id.toString()
            }
            if (already != null) {
                medicationRepository.unlogDose(already)
            } else {
                medicationRepository.logDose(
                    medicationId = medication.id,
                    slotKey = slot.id.toString()
                )
            }
            refreshTierState(slot.id)
        }
    }

    /**
     * Drop the slot's tier to SKIPPED for today. Records the row as
     * `USER_SET` so subsequent dose changes don't auto-upgrade it.
     *
     * Also writes a synthetic-skip dose per affected medication so the
     * interval-mode reminder rescheduler re-anchors on the skip. Synthetic
     * doses are filtered out of the medication log UI but visible to the
     * scheduler via [MedicationDoseDao.getMostRecentDoseAnyOnce]. Skipping
     * a previously-synthetic-skipped slot is idempotent — a fresh row just
     * pushes the anchor forward.
     */
    fun setSkippedForSlot(slot: MedicationSlotEntity) {
        viewModelScope.launch {
            val date = todayDate.value
            val meds = medicationsForSlotOnce(slot.id)
            val now = System.currentTimeMillis()
            meds.forEach { med ->
                slotRepository.upsertTierState(
                    medicationId = med.id,
                    slotId = slot.id,
                    date = date,
                    tier = AchievedTier.SKIPPED,
                    source = TierSource.USER_SET
                )
                medicationRepository.logSyntheticSkipDose(
                    medicationId = med.id,
                    slotKey = slot.id.toString(),
                    intendedAt = now
                )
            }
        }
    }

    /**
     * Set a user-claimed intended_time on every per-(medication, slot, today)
     * tier-state row for the given slot. Materializes any missing rows via
     * an auto-compute pass first so the column is non-null after the call.
     */
    fun setIntendedTimeForSlot(slot: MedicationSlotEntity, intendedTime: Long) {
        viewModelScope.launch {
            val date = todayDate.value
            val meds = medicationsForSlotOnce(slot.id)
            // Ensure every per-med row exists — refreshTierState writes
            // COMPUTED rows for any missing (med, slot, date) triple.
            refreshTierState(slot.id)
            meds.forEach { med ->
                slotRepository.setTierStateIntendedTime(
                    medicationId = med.id,
                    slotId = slot.id,
                    date = date,
                    intendedTime = intendedTime
                )
            }
        }
    }

    /**
     * Clear a user override so the slot's tier goes back to auto-compute.
     */
    fun clearUserOverrideForSlot(slot: MedicationSlotEntity) {
        viewModelScope.launch {
            val date = todayDate.value
            val states = todaysTierStates.value.filter { it.slotId == slot.id }
            states.forEach { slotRepository.deleteTierState(it) }
            refreshTierState(slot.id)
        }
    }

    /**
     * Recompute the tier state for a slot from the current dose list and
     * persist it as `COMPUTED`. Called after every dose toggle; no-op if
     * the existing row is `USER_SET` (the repository respects the
     * override inside upsertTierState).
     */
    private suspend fun refreshTierState(slotId: Long) {
        val date = todayDate.value
        val meds = medicationsForSlotOnce(slotId)
        val doses = todaysDoses.value
        val takenIds = doses.asSequence()
            .filter { it.slotKey == slotId.toString() }
            .map { it.medicationId }
            .toSet()
        val computed = MedicationTierComputer.computeAchievedTier(
            medsForSlot = meds.associate { it.id to MedicationTier.fromStorage(it.tier) },
            markedTaken = takenIds
        )
        meds.forEach { med ->
            slotRepository.upsertTierState(
                medicationId = med.id,
                slotId = slotId,
                date = date,
                tier = computed,
                source = TierSource.COMPUTED
            )
        }
    }

    private suspend fun medicationsForSlotOnce(slotId: Long): List<MedicationEntity> {
        val medIds = slotRepository.getMedicationIdsForSlotOnce(slotId).toSet()
        return medications.value.filter { it.id in medIds }
    }

    // ── Medication CRUD ────────────────────────────────────────────────

    /**
     * Insert a new medication and link it to [slotSelections]. Tier is the
     * enum; the storage write is handled inside MedicationTier.toStorage().
     * Per-slot overrides in [slotSelections] are written via upsertOverride
     * so the underlying UNIQUE index is respected.
     */
    fun addMedication(
        name: String,
        tier: MedicationTier,
        notes: String,
        slotSelections: List<MedicationSlotSelection>,
        reminderMode: String? = null,
        reminderIntervalMinutes: Int? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = medicationRepository.insert(
                MedicationEntity(
                    name = name.trim(),
                    tier = tier.toStorage(),
                    notes = notes.trim(),
                    reminderMode = reminderMode,
                    reminderIntervalMinutes = reminderIntervalMinutes
                )
            )
            slotRepository.replaceLinksForMedication(id, slotSelections.map { it.slotId })
            slotSelections
                .filter { it.hasOverride }
                .forEach { sel ->
                    slotRepository.upsertOverride(
                        com.averycorp.prismtask.data.local.entity.MedicationSlotOverrideEntity(
                            medicationId = id,
                            slotId = sel.slotId,
                            overrideIdealTime = sel.overrideIdealTime,
                            overrideDriftMinutes = sel.overrideDriftMinutes
                        )
                    )
                }
            // Bump the med so its embedded slotCloudIds list re-pushes.
            val inserted = medicationRepository.getByIdOnce(id) ?: return@launch
            medicationRepository.update(inserted)
        }
    }

    fun updateMedication(
        medication: MedicationEntity,
        name: String,
        tier: MedicationTier,
        notes: String,
        slotSelections: List<MedicationSlotSelection>,
        reminderMode: String? = null,
        reminderIntervalMinutes: Int? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            medicationRepository.update(
                medication.copy(
                    name = name.trim(),
                    tier = tier.toStorage(),
                    notes = notes.trim(),
                    reminderMode = reminderMode,
                    reminderIntervalMinutes = reminderIntervalMinutes
                )
            )
            slotRepository.replaceLinksForMedication(medication.id, slotSelections.map { it.slotId })
            // Replace override rows: delete any existing for slots that
            // are no longer overridden, upsert the rest.
            val existingOverrides = slotRepository.getOverridesForMedicationOnce(medication.id)
            val selectedSlotIds = slotSelections.map { it.slotId }.toSet()
            existingOverrides
                .filter { it.slotId !in selectedSlotIds }
                .forEach { slotRepository.deleteOverride(it) }
            slotSelections
                .filter { it.hasOverride }
                .forEach { sel ->
                    slotRepository.upsertOverride(
                        com.averycorp.prismtask.data.local.entity.MedicationSlotOverrideEntity(
                            medicationId = medication.id,
                            slotId = sel.slotId,
                            overrideIdealTime = sel.overrideIdealTime,
                            overrideDriftMinutes = sel.overrideDriftMinutes
                        )
                    )
                }
            slotSelections
                .filter { !it.hasOverride }
                .forEach { sel -> slotRepository.deleteOverrideForPair(medication.id, sel.slotId) }
        }
    }

    fun archiveMedication(medication: MedicationEntity) {
        viewModelScope.launch { medicationRepository.archive(medication.id) }
    }

    suspend fun selectionsForMedication(medicationId: Long): List<MedicationSlotSelection> {
        val slotIds = slotRepository.getSlotIdsForMedicationOnce(medicationId)
        val overrides = slotRepository.getOverridesForMedicationOnce(medicationId)
            .associateBy { it.slotId }
        return slotIds.map { slotId ->
            val o = overrides[slotId]
            MedicationSlotSelection(
                slotId = slotId,
                overrideIdealTime = o?.overrideIdealTime,
                overrideDriftMinutes = o?.overrideDriftMinutes
            )
        }
    }
}
