package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import com.averycorp.prismtask.domain.usecase.BatchUserContextProvider
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers MEDICATION batch ops for [BatchOperationsRepository]. Mirrors the
 * tag-change regression net (PR #761) — apply + undo end-to-end through
 * `applyBatch` / `undoBatch`, exercising real Room DAOs.
 *
 * The four supported (MEDICATION, mutation) pairs:
 *  - COMPLETE: inserts a `MedicationDoseEntity` (real dose, slot_key as-is)
 *  - SKIP: inserts a synthetic-skip dose + (if slot resolves) writes
 *    `MedicationTierStateEntity.tier = "skipped"`
 *  - DELETE: removes the matching dose row for (medication, slot_key, date)
 *  - STATE_CHANGE: writes `MedicationTierStateEntity` with the proposed
 *    tier and `tier_source = "user_set"`
 */
@RunWith(AndroidJUnit4::class)
class BatchOperationsRepositoryMedicationTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var repository: BatchOperationsRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val api = mockk<PrismTaskApi>(relaxed = true)
        val contextProvider = mockk<BatchUserContextProvider>(relaxed = true)

        repository = BatchOperationsRepository(
            database = database,
            api = api,
            taskDao = database.taskDao(),
            habitDao = database.habitDao(),
            projectDao = database.projectDao(),
            tagDao = database.tagDao(),
            habitCompletionDao = database.habitCompletionDao(),
            batchUndoLogDao = database.batchUndoLogDao(),
            medicationDao = database.medicationDao(),
            medicationDoseDao = database.medicationDoseDao(),
            medicationSlotDao = database.medicationSlotDao(),
            medicationTierStateDao = database.medicationTierStateDao(),
            contextProvider = contextProvider
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    // -------------------------------------------------------------------
    // Apply paths
    // -------------------------------------------------------------------

    @Test
    fun applyMedicationComplete_insertsDoseRow() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))

        val result = repository.applyBatch(
            commandText = "took my morning Adderall",
            mutations = listOf(completeMutation(medId, slotKey = "morning", date = "2026-04-25"))
        )

        assertEquals(1, result.appliedCount)
        val doses = database.medicationDoseDao().getAllOnce()
        assertEquals(1, doses.size)
        val dose = doses.single()
        assertEquals(medId, dose.medicationId)
        assertEquals("morning", dose.slotKey)
        assertEquals("2026-04-25", dose.takenDateLocal)
        assertEquals(false, dose.isSyntheticSkip)
    }

    @Test
    fun applyMedicationSkip_insertsSyntheticDoseAndTierState() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val slotId = database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Morning", idealTime = "09:00")
        )

        val result = repository.applyBatch(
            commandText = "skip my morning Adderall",
            mutations = listOf(skipMutation(medId, slotKey = "morning", date = "2026-04-25"))
        )

        assertEquals(1, result.appliedCount)
        val doses = database.medicationDoseDao().getAllOnce()
        assertEquals(1, doses.size)
        assertEquals(true, doses.single().isSyntheticSkip)

        val tierState = database.medicationTierStateDao()
            .getForTripleOnce(medId, "2026-04-25", slotId)
        assertNotNull("Tier-state row should exist for the skipped slot", tierState)
        assertEquals("skipped", tierState!!.tier)
        assertEquals("user_set", tierState.tierSource)
    }

    @Test
    fun applyMedicationDelete_removesMatchingDose() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        database.medicationDoseDao().insert(
            MedicationDoseEntity(
                medicationId = medId,
                slotKey = "morning",
                takenAt = System.currentTimeMillis(),
                takenDateLocal = "2026-04-25"
            )
        )

        val result = repository.applyBatch(
            commandText = "I didn't actually take morning Adderall",
            mutations = listOf(deleteMutation(medId, slotKey = "morning", date = "2026-04-25"))
        )

        assertEquals(1, result.appliedCount)
        assertTrue(database.medicationDoseDao().getAllOnce().isEmpty())
    }

    @Test
    fun applyMedicationDelete_skipsWhenNoMatchingDose() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))

        val result = repository.applyBatch(
            commandText = "undo morning Adderall",
            mutations = listOf(deleteMutation(medId, slotKey = "morning", date = "2026-04-25"))
        )

        assertEquals("Should skip when no matching dose exists", 0, result.appliedCount)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun applyMedicationStateChange_writesTierStateUserSet() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val slotId = database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Evening", idealTime = "20:00")
        )

        val result = repository.applyBatch(
            commandText = "set evening Adderall to prescription tier",
            mutations = listOf(
                stateChangeMutation(
                    medId,
                    slotKey = "evening",
                    date = "2026-04-25",
                    tier = "prescription"
                )
            )
        )

        assertEquals(1, result.appliedCount)
        val tierState = database.medicationTierStateDao()
            .getForTripleOnce(medId, "2026-04-25", slotId)
        assertNotNull(tierState)
        assertEquals("prescription", tierState!!.tier)
        assertEquals("user_set", tierState.tierSource)
    }

    @Test
    fun applyMedicationStateChange_overwritesExistingTierState() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val slotId = database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Evening", idealTime = "20:00")
        )
        // Pre-existing computed tier
        database.medicationTierStateDao().insert(
            MedicationTierStateEntity(
                medicationId = medId,
                slotId = slotId,
                logDate = "2026-04-25",
                tier = "essential",
                tierSource = "computed"
            )
        )

        val result = repository.applyBatch(
            commandText = "bump evening Adderall to complete",
            mutations = listOf(
                stateChangeMutation(
                    medId,
                    slotKey = "evening",
                    date = "2026-04-25",
                    tier = "complete"
                )
            )
        )

        assertEquals(1, result.appliedCount)
        val tierState = database.medicationTierStateDao()
            .getForTripleOnce(medId, "2026-04-25", slotId)
        assertNotNull(tierState)
        assertEquals("complete", tierState!!.tier)
        assertEquals("user_set", tierState.tierSource)
    }

    @Test
    fun applyMedicationComplete_skipsWhenMedicationMissing() = runTest {
        val result = repository.applyBatch(
            commandText = "took unknown med",
            mutations = listOf(completeMutation(99999L, slotKey = "morning", date = "2026-04-25"))
        )

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun applyMedicationComplete_silentSkipReason_includesMedicationNotFound() = runTest {
        // Closes audit Section E.2 gap: failure-mode-#4 silent-skip behavior
        // must be observable via the skip reason, not just a count, so a
        // future UX can surface "we couldn't find the medication you named"
        // rather than an opaque "1 skipped".
        val result = repository.applyBatch(
            commandText = "took unknown med",
            mutations = listOf(completeMutation(99999L, slotKey = "morning", date = "2026-04-25"))
        )

        assertEquals(1, result.skipped.size)
        assertEquals("medication not found", result.skipped.single().reason)
    }

    // -------------------------------------------------------------------
    // Undo paths
    // -------------------------------------------------------------------

    @Test
    fun undoMedicationComplete_deletesInsertedDose() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))

        val applyResult = repository.applyBatch(
            commandText = "took morning Adderall",
            mutations = listOf(completeMutation(medId, slotKey = "morning", date = "2026-04-25"))
        )
        assertEquals(1, applyResult.appliedCount)
        assertEquals(1, database.medicationDoseDao().getAllOnce().size)

        val undo = repository.undoBatch(applyResult.batchId)
        assertEquals(1, undo.restored)
        assertTrue(undo.failed.isEmpty())
        assertTrue(
            "Dose should be removed by undo",
            database.medicationDoseDao().getAllOnce().isEmpty()
        )
    }

    @Test
    fun undoMedicationSkip_removesSyntheticDoseAndRestoresPriorTier() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val slotId = database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Morning", idealTime = "09:00")
        )
        database.medicationTierStateDao().insert(
            MedicationTierStateEntity(
                medicationId = medId,
                slotId = slotId,
                logDate = "2026-04-25",
                tier = "essential",
                tierSource = "computed"
            )
        )

        val applyResult = repository.applyBatch(
            commandText = "skip morning Adderall",
            mutations = listOf(skipMutation(medId, slotKey = "morning", date = "2026-04-25"))
        )
        assertEquals(1, applyResult.appliedCount)
        // Sanity: tier was overwritten to skipped + a synthetic dose exists.
        assertEquals(
            "skipped",
            database.medicationTierStateDao()
                .getForTripleOnce(medId, "2026-04-25", slotId)!!.tier
        )
        assertEquals(1, database.medicationDoseDao().getAllOnce().size)

        val undo = repository.undoBatch(applyResult.batchId)
        assertEquals(1, undo.restored)
        assertTrue(undo.failed.isEmpty())
        // Synthetic dose removed, prior tier ("essential", "computed") restored.
        assertTrue(database.medicationDoseDao().getAllOnce().isEmpty())
        val restoredTier = database.medicationTierStateDao()
            .getForTripleOnce(medId, "2026-04-25", slotId)
        assertNotNull(restoredTier)
        assertEquals("essential", restoredTier!!.tier)
        assertEquals("computed", restoredTier.tierSource)
    }

    @Test
    fun undoMedicationStateChange_restoresPriorTier_orDeletesIfNew() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val slotId = database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Evening", idealTime = "20:00")
        )

        // No prior tier-state — STATE_CHANGE creates one; undo should delete it.
        val applyResult = repository.applyBatch(
            commandText = "set evening to complete",
            mutations = listOf(
                stateChangeMutation(
                    medId,
                    slotKey = "evening",
                    date = "2026-04-25",
                    tier = "complete"
                )
            )
        )
        assertEquals(1, applyResult.appliedCount)
        assertNotNull(
            database.medicationTierStateDao().getForTripleOnce(medId, "2026-04-25", slotId)
        )

        val undo = repository.undoBatch(applyResult.batchId)
        assertEquals(1, undo.restored)
        assertNull(
            "Tier-state created by batch should be deleted on undo",
            database.medicationTierStateDao().getForTripleOnce(medId, "2026-04-25", slotId)
        )
    }

    @Test
    fun undoMedicationDelete_reinsertsDoseRow() = runTest {
        val medId = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val originalTakenAt = 1_700_000_000_000L
        database.medicationDoseDao().insert(
            MedicationDoseEntity(
                medicationId = medId,
                slotKey = "morning",
                takenAt = originalTakenAt,
                takenDateLocal = "2026-04-25",
                note = "before breakfast"
            )
        )

        val applyResult = repository.applyBatch(
            commandText = "undo morning Adderall",
            mutations = listOf(deleteMutation(medId, slotKey = "morning", date = "2026-04-25"))
        )
        assertEquals(1, applyResult.appliedCount)
        assertTrue(database.medicationDoseDao().getAllOnce().isEmpty())

        val undo = repository.undoBatch(applyResult.batchId)
        assertEquals(1, undo.restored)
        val restored = database.medicationDoseDao().getAllOnce()
        assertEquals(1, restored.size)
        val dose = restored.single()
        assertEquals(medId, dose.medicationId)
        assertEquals("morning", dose.slotKey)
        assertEquals("2026-04-25", dose.takenDateLocal)
        assertEquals(originalTakenAt, dose.takenAt)
        assertEquals("before breakfast", dose.note)
    }

    // -------------------------------------------------------------------
    // Multi-target STATE_CHANGE / SKIP — bulk-mark feature follow-up
    //
    // These tests cover the fan-out approach the bulk-mark UI uses:
    // construct N single-target ProposedMutationResponse rows, call
    // applyBatch once. The shared batch_id property is what makes the
    // 24h durable history undo reverse the whole bulk action atomically.
    // -------------------------------------------------------------------

    @Test
    fun bulkStateChange_writesAllRowsUnderOneBatchId() = runTest {
        val medA = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val medB = database.medicationDao().insert(MedicationEntity(name = "Lexapro"))
        val medC = database.medicationDao().insert(MedicationEntity(name = "Vitamin D"))
        database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Morning", idealTime = "09:00")
        )

        val result = repository.applyBatch(
            commandText = "Bulk mark 3 medication(s) in slot \"Morning\" as complete",
            mutations = listOf(
                stateChangeMutation(medA, slotKey = "morning", date = "2026-04-25", tier = "complete"),
                stateChangeMutation(medB, slotKey = "morning", date = "2026-04-25", tier = "complete"),
                stateChangeMutation(medC, slotKey = "morning", date = "2026-04-25", tier = "complete")
            )
        )

        assertEquals(3, result.appliedCount)
        assertEquals(0, result.skipped.size)

        val tierStates = database.medicationTierStateDao().getForDateOnce("2026-04-25")
        assertEquals(3, tierStates.size)
        assertTrue("all rows lowercase tier", tierStates.all { it.tier == "complete" })
        assertTrue("all rows USER_SET", tierStates.all { it.tierSource == "user_set" })

        // Verify the batch_undo_log entries all share one batch_id.
        val entries = database.batchUndoLogDao().getEntriesForBatchOnce(result.batchId)
        assertEquals(3, entries.size)
        assertTrue(
            "every undo entry references the same batch",
            entries.all { it.batchId == result.batchId }
        )
        assertTrue(
            "every entry is a STATE_CHANGE",
            entries.all { it.mutationType == "STATE_CHANGE" }
        )
    }

    @Test
    fun bulkStateChange_undoBatch_reversesAllRows() = runTest {
        val medA = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val medB = database.medicationDao().insert(MedicationEntity(name = "Lexapro"))
        val slotId = database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Morning", idealTime = "09:00")
        )
        // Pre-existing tier-state for medA (essential, computed) — undo should restore.
        database.medicationTierStateDao().insert(
            MedicationTierStateEntity(
                medicationId = medA,
                slotId = slotId,
                logDate = "2026-04-25",
                tier = "essential",
                tierSource = "computed"
            )
        )

        val applied = repository.applyBatch(
            commandText = "Bulk mark 2 medications in slot \"Morning\" as complete",
            mutations = listOf(
                stateChangeMutation(medA, slotKey = "morning", date = "2026-04-25", tier = "complete"),
                stateChangeMutation(medB, slotKey = "morning", date = "2026-04-25", tier = "complete")
            )
        )
        assertEquals(2, applied.appliedCount)
        // After apply: medA flipped to complete/user_set, medB inserted as complete/user_set.
        val afterApply = database.medicationTierStateDao().getForDateOnce("2026-04-25")
        assertEquals(2, afterApply.size)
        assertTrue("all complete after apply", afterApply.all { it.tier == "complete" })

        val undo = repository.undoBatch(applied.batchId)
        assertEquals(2, undo.restored)
        assertEquals(0, undo.failed.size)

        // After undo: medA restored to essential/computed (the pre-existing row),
        // medB's freshly-inserted row deleted.
        val afterUndo = database.medicationTierStateDao().getForDateOnce("2026-04-25")
        assertEquals("medA row preserved, medB row deleted", 1, afterUndo.size)
        val medARow = afterUndo.single()
        assertEquals(medA, medARow.medicationId)
        assertEquals("essential", medARow.tier)
        assertEquals("computed", medARow.tierSource)
    }

    @Test
    fun bulkStateChange_partialFailure_appliesGoodSkipsBad() = runTest {
        val medA = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Morning", idealTime = "09:00")
        )

        val result = repository.applyBatch(
            commandText = "Bulk mark 3 medications in slot \"Morning\" as complete",
            mutations = listOf(
                stateChangeMutation(medA, slotKey = "morning", date = "2026-04-25", tier = "complete"),
                // Stale entity id — should be silently skipped, not break the batch.
                stateChangeMutation(99_999L, slotKey = "morning", date = "2026-04-25", tier = "complete"),
                // Missing slot_key — should be skipped with reason, not break the batch.
                ProposedMutationResponse(
                    entityType = "MEDICATION",
                    entityId = medA.toString(),
                    mutationType = "STATE_CHANGE",
                    proposedNewValues = mapOf("date" to "2026-04-25", "tier" to "complete"),
                    humanReadableDescription = "STATE_CHANGE missing slot_key"
                )
            )
        )

        assertEquals("only the well-formed mutation applies", 1, result.appliedCount)
        assertEquals(2, result.skipped.size)
        // batch_id is still issued; the one applied entry sits under it alone.
        val entries = database.batchUndoLogDao().getEntriesForBatchOnce(result.batchId)
        assertEquals(1, entries.size)
    }

    @Test
    fun bulkSkip_writesAllSyntheticDosesAndTierStatesUnderOneBatchId() = runTest {
        val medA = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val medB = database.medicationDao().insert(MedicationEntity(name = "Lexapro"))
        database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Evening", idealTime = "20:00")
        )

        val result = repository.applyBatch(
            commandText = "Bulk mark 2 medications in slot \"Evening\" as skipped",
            mutations = listOf(
                skipMutation(medA, slotKey = "evening", date = "2026-04-25"),
                skipMutation(medB, slotKey = "evening", date = "2026-04-25")
            )
        )

        assertEquals(2, result.appliedCount)
        // Each SKIP mutation writes a synthetic-skip dose AND a tier-state row.
        // The synthetic dose loop is what re-anchors interval-mode reminders —
        // verifying it fires on every target is the audit's "SKIPPED-bulk side
        // effect" guarantee from PHASE_D BULK_MEDICATION_MARK_AUDIT Item 6.
        val syntheticDoses = database.medicationDoseDao().getAllOnce()
            .filter { it.isSyntheticSkip }
        assertEquals(2, syntheticDoses.size)

        val tierStates = database.medicationTierStateDao().getForDateOnce("2026-04-25")
        assertEquals(2, tierStates.size)
        assertTrue("all rows lowercase skipped", tierStates.all { it.tier == "skipped" })
        assertTrue("all rows USER_SET", tierStates.all { it.tierSource == "user_set" })

        // Shared batch_id confirmed.
        val entries = database.batchUndoLogDao().getEntriesForBatchOnce(result.batchId)
        assertEquals(2, entries.size)
    }

    @Test
    fun bulkSkip_undoBatch_removesAllSyntheticDosesAndTierStates() = runTest {
        val medA = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val medB = database.medicationDao().insert(MedicationEntity(name = "Lexapro"))
        database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Evening", idealTime = "20:00")
        )

        val applied = repository.applyBatch(
            commandText = "bulk skip evening",
            mutations = listOf(
                skipMutation(medA, slotKey = "evening", date = "2026-04-25"),
                skipMutation(medB, slotKey = "evening", date = "2026-04-25")
            )
        )
        assertEquals(2, applied.appliedCount)

        val undo = repository.undoBatch(applied.batchId)
        assertEquals(2, undo.restored)

        // Synthetic doses and tier-state rows both gone.
        val remainingDoses = database.medicationDoseDao().getAllOnce()
        assertTrue("no synthetic doses left after undo", remainingDoses.none { it.isSyntheticSkip })
        val remainingTierStates = database.medicationTierStateDao().getForDateOnce("2026-04-25")
        assertEquals("no tier-state rows after undo", 0, remainingTierStates.size)
    }

    @Test
    fun bulkStateChange_emptyMutationList_emitsZeroEntries() = runTest {
        val result = repository.applyBatch(
            commandText = "noop bulk",
            mutations = emptyList()
        )

        assertEquals(0, result.appliedCount)
        assertEquals(0, result.skipped.size)
        // batch_id is still issued (UUID is stable per call) but there's no
        // log row referencing it. Verifies the empty-scope no-op branch
        // doesn't write a sentinel "empty batch" row.
        val entries = database.batchUndoLogDao().getEntriesForBatchOnce(result.batchId)
        assertEquals(0, entries.size)
    }

    @Test
    fun bulkStateChange_overwritesPriorUserSet_undoRestoresOriginalUserSet() = runTest {
        val medA = database.medicationDao().insert(MedicationEntity(name = "Adderall"))
        val slotId = database.medicationSlotDao().insert(
            MedicationSlotEntity(name = "Morning", idealTime = "09:00")
        )
        // Pre-existing USER_SET row — bulk-mark must overwrite it cleanly,
        // and undo must restore the prior tier + source.
        database.medicationTierStateDao().insert(
            MedicationTierStateEntity(
                medicationId = medA,
                slotId = slotId,
                logDate = "2026-04-25",
                tier = "prescription",
                tierSource = "user_set"
            )
        )

        val applied = repository.applyBatch(
            commandText = "Bulk mark 1 medication in slot \"Morning\" as complete",
            mutations = listOf(
                stateChangeMutation(medA, slotKey = "morning", date = "2026-04-25", tier = "complete")
            )
        )
        assertEquals(1, applied.appliedCount)
        assertEquals(
            "complete",
            database.medicationTierStateDao().getForDateOnce("2026-04-25").single().tier
        )

        repository.undoBatch(applied.batchId)
        val restored = database.medicationTierStateDao().getForDateOnce("2026-04-25").single()
        assertEquals("prior tier restored", "prescription", restored.tier)
        assertEquals("prior source restored", "user_set", restored.tierSource)
    }

    // -------------------------------------------------------------------
    // Mutation factories
    // -------------------------------------------------------------------

    private fun completeMutation(medId: Long, slotKey: String, date: String) =
        ProposedMutationResponse(
            entityType = "MEDICATION",
            entityId = medId.toString(),
            mutationType = "COMPLETE",
            proposedNewValues = mapOf("slot_key" to slotKey, "date" to date),
            humanReadableDescription = "COMPLETE on medication $medId"
        )

    private fun skipMutation(medId: Long, slotKey: String, date: String) =
        ProposedMutationResponse(
            entityType = "MEDICATION",
            entityId = medId.toString(),
            mutationType = "SKIP",
            proposedNewValues = mapOf("slot_key" to slotKey, "date" to date),
            humanReadableDescription = "SKIP on medication $medId"
        )

    private fun deleteMutation(medId: Long, slotKey: String, date: String) =
        ProposedMutationResponse(
            entityType = "MEDICATION",
            entityId = medId.toString(),
            mutationType = "DELETE",
            proposedNewValues = mapOf("slot_key" to slotKey, "date" to date),
            humanReadableDescription = "DELETE on medication $medId"
        )

    private fun stateChangeMutation(
        medId: Long,
        slotKey: String,
        date: String,
        tier: String
    ) = ProposedMutationResponse(
        entityType = "MEDICATION",
        entityId = medId.toString(),
        mutationType = "STATE_CHANGE",
        proposedNewValues = mapOf("slot_key" to slotKey, "date" to date, "tier" to tier),
        humanReadableDescription = "STATE_CHANGE on medication $medId"
    )
}
