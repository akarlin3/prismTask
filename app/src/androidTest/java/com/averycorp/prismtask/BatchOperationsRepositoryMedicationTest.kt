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
