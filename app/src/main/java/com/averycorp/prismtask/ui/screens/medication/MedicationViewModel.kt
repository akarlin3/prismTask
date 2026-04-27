package com.averycorp.prismtask.ui.screens.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.core.time.LocalDateFlow
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import com.averycorp.prismtask.data.repository.MedicationRepository
import com.averycorp.prismtask.data.repository.MedicationSlotRepository
import com.averycorp.prismtask.domain.model.BatchEntityType
import com.averycorp.prismtask.domain.model.BatchMutationType
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.domain.model.medication.BulkMarkScope
import com.averycorp.prismtask.domain.model.medication.MedicationTier
import com.averycorp.prismtask.domain.model.medication.TierSource
import com.averycorp.prismtask.domain.usecase.MedicationTierComputer
import com.averycorp.prismtask.ui.screens.medication.components.MedicationSlotSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val batchOperationsRepository: BatchOperationsRepository,
    private val localDateFlow: LocalDateFlow
) : ViewModel() {
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode

    val medications: StateFlow<List<MedicationEntity>> = medicationRepository
        .observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val activeSlots: StateFlow<List<MedicationSlotEntity>> = slotRepository
        .observeActiveSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /**
     * ISO local date scoped by the user's day-start hour preference.
     *
     * Backed by [LocalDateFlow], which combines the SoD source with a
     * wall-clock ticker that re-emits at every logical-day boundary. The
     * initial value uses calendar `LocalDate.now()` as a one-frame
     * fallback — the inner flow emits the SoD-correct value synchronously
     * on subscription, so the initial value is effectively never observed
     * by the UI.
     *
     * See `docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md` for the bug this
     * structure replaces.
     */
    val todayDate: StateFlow<String> = localDateFlow
        .observeIsoString(taskBehaviorPreferences.getStartOfDay())
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            LocalDate.now().toString()
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
            // Synthetic-skip rows exist only as scheduling anchors for the
            // interval-mode reminder rescheduler — they should not make a
            // med look "taken" in the per-med checkbox UI or in the
            // achieved-tier auto-compute pass.
            val takenIds = doses.asSequence()
                .filter {
                    it.medicationId in linkedMedIds &&
                        it.slotKey == slot.id.toString() &&
                        !it.isSyntheticSkip
                }
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

    // ── Bulk tier marking ──────────────────────────────────────────────

    /**
     * Apply [tier] to every medication in [scope] by writing real
     * dose-log rows for meds at that tier or below (the ladder semantic
     * `MedicationTierComputer` already uses for auto-compute), and
     * routing the work through the batch infrastructure so Settings →
     * Batch History can reverse the whole action under one `batch_id`.
     *
     * **Mutation choice by tier:**
     *  - `SKIPPED` → [BatchMutationType.SKIP] for every linked med. The
     *    batch handler deletes any real doses for the slot today and
     *    writes a synthetic-skip row plus a `USER_SET=SKIPPED` tier-state
     *    so interval-mode reminders re-anchor.
     *  - non-`SKIPPED` → [BatchMutationType.COMPLETE] for every linked
     *    med whose [MedicationTier] sits at or below the clicked rung
     *    AND that doesn't already have a real dose for today. Already-
     *    taken meds at higher tiers stay taken — clicking a lower tier
     *    never auto-unchecks the user's manual marks.
     *
     * After a non-`SKIPPED` apply, the viewmodel deletes any
     * `USER_SET` tier-state rows for the affected slot so the
     * achieved-tier display flips back onto auto-compute (otherwise a
     * stale `USER_SET=SKIPPED` from an earlier Skip would mask the new
     * dose log).
     *
     * Empty scope / no eligible meds → no-op (returns null without
     * touching the batch infra).
     */
    fun bulkMark(scope: BulkMarkScope, slotId: Long?, tier: AchievedTier) {
        viewModelScope.launch { bulkMarkInternal(scope, slotId, tier) }
    }

    /**
     * Suspending implementation extracted so unit tests can `runTest`
     * without spinning up a full `viewModelScope` lifecycle. Returns
     * the [BatchOperationsRepository.BatchApplyResult] of the apply
     * call, or `null` if the scope produced zero targets.
     */
    internal suspend fun bulkMarkInternal(
        scope: BulkMarkScope,
        slotId: Long?,
        tier: AchievedTier
    ): BatchOperationsRepository.BatchApplyResult? {
        val date = todayDate.value
        val rawTargets: List<Pair<MedicationEntity, MedicationSlotEntity>> = when (scope) {
            BulkMarkScope.SLOT -> {
                val slot = activeSlots.value.firstOrNull { it.id == slotId } ?: return null
                medicationsForSlotOnce(slot.id).map { it to slot }
            }
            BulkMarkScope.FULL_DAY -> {
                activeSlots.value.flatMap { slot ->
                    medicationsForSlotOnce(slot.id).map { it to slot }
                }
            }
        }
        if (rawTargets.isEmpty()) return null

        val mutations: List<ProposedMutationResponse>
        val storageTier = tier.toStorage()
        val now = System.currentTimeMillis()

        if (tier == AchievedTier.SKIPPED) {
            mutations = rawTargets.map { (med, slot) ->
                ProposedMutationResponse(
                    entityType = BatchEntityType.MEDICATION.name,
                    entityId = med.id.toString(),
                    mutationType = BatchMutationType.SKIP.name,
                    proposedNewValues = mapOf(
                        // slotKey on dose rows is the numeric slot id stringified
                        // (the per-med checkbox path uses slot.id.toString()); writing
                        // slot.name here would leave doses invisible to the slot-card UI.
                        "slot_key" to slot.id.toString(),
                        "date" to date,
                        "tier" to storageTier
                    ),
                    humanReadableDescription = "Skip ${med.name} (${slot.name})"
                )
            }
        } else {
            // Filter by tier ladder: only meds at or below the clicked
            // tier are eligible; already-taken meds are left alone so we
            // don't pile up duplicate dose rows.
            val takenByMed: Map<Long, Set<Long>> = todaysDoses.value
                .asSequence()
                .filter { !it.isSyntheticSkip }
                .groupBy { it.medicationId }
                .mapValues { (_, doses) -> doses.map { it.slotKey.toLongOrNull() ?: -1L }.toSet() }
            mutations = rawTargets
                .filter { (med, _) ->
                    val medTier = MedicationTier.fromStorage(med.tier)
                    AchievedTier.from(medTier).ordinal <= tier.ordinal
                }
                .filterNot { (med, slot) -> takenByMed[med.id]?.contains(slot.id) == true }
                .map { (med, slot) ->
                    ProposedMutationResponse(
                        entityType = BatchEntityType.MEDICATION.name,
                        entityId = med.id.toString(),
                        mutationType = BatchMutationType.COMPLETE.name,
                        proposedNewValues = mapOf(
                            "slot_key" to slot.id.toString(),
                            "date" to date,
                            "tier" to storageTier,
                            "taken_at" to now
                        ),
                        humanReadableDescription = "Mark ${med.name} (${slot.name}) taken"
                    )
                }
        }

        if (mutations.isEmpty()) return null

        val commandText = when (scope) {
            BulkMarkScope.SLOT -> {
                val slotName = rawTargets.first().second.name
                "Bulk mark ${mutations.size} medication(s) in slot \"$slotName\" as $storageTier"
            }
            BulkMarkScope.FULL_DAY -> {
                "Bulk mark ${mutations.size} medication(s) across today as $storageTier"
            }
        }

        val result = batchOperationsRepository.applyBatch(commandText, mutations)

        // Stale USER_SET tier-state rows would mask the freshly logged
        // doses — the slot card would still show "Skipped" with every
        // checkbox now ticked. Drop them on the affected slots so
        // auto-compute drives the display.
        if (tier != AchievedTier.SKIPPED) {
            val affectedSlotIds = rawTargets.map { it.second.id }.toSet()
            val priorStates = slotRepository.getTierStatesForDateOnce(date)
            priorStates
                .filter { it.slotId in affectedSlotIds && it.tierSource == "user_set" }
                .forEach { slotRepository.deleteTierState(it) }
        }

        return result
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
