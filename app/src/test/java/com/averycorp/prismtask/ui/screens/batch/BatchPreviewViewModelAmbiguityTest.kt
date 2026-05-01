package com.averycorp.prismtask.ui.screens.batch

import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.preferences.NdPreferences
import com.averycorp.prismtask.data.preferences.NdPreferencesDataStore
import com.averycorp.prismtask.data.remote.api.AmbiguousEntityHintResponse
import com.averycorp.prismtask.data.remote.api.BatchParseResponse
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BatchPreviewViewModel]'s ambiguity-handling paths. The
 * audit (`cowork_outputs/medication_ambiguous_name_resolution_REPORT.md`)
 * verdicted RED-P1 on the silent-wrong-pick risk: when Haiku flags a phrase
 * as ambiguous AND emits a mutation for one of the candidates, the user
 * could approve a wrong-medication dose without realising. The auto-strip
 * safeguard in [BatchPreviewViewModel.loadPreview] is the belt-and-suspenders
 * guard regardless of whether the picker is ever shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchPreviewViewModelAmbiguityTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var repository: BatchOperationsRepository
    private lateinit var undoBus: BatchUndoEventBus
    private lateinit var ndPreferencesDataStore: NdPreferencesDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        undoBus = mockk(relaxed = true)
        ndPreferencesDataStore = mockk()
        every { ndPreferencesDataStore.ndPreferencesFlow } returns flowOf(NdPreferences())
        coEvery { repository.getTagNamesForTasks(any()) } returns emptyMap()
        coEvery { repository.getMedicationsByIds(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadPreview_stripsMutationsListedInAmbiguousCandidates() = runTest(dispatcher) {
        coEvery { repository.parseCommand(any()) } returns BatchParseResponse(
            mutations = listOf(
                medicationCompleteMutation(entityId = "42", slotKey = "morning")
            ),
            confidence = 0.5f,
            ambiguousEntities = listOf(
                AmbiguousEntityHintResponse(
                    phrase = "Wellbutrin",
                    candidateEntityType = "MEDICATION",
                    candidateEntityIds = listOf("42", "43"),
                    note = "Two medications match"
                )
            )
        )

        val viewModel = newViewModel()
        viewModel.loadPreview("took my Wellbutrin")
        advanceUntilIdle()

        val loaded = viewModel.state.value as BatchPreviewState.Loaded
        assertTrue(
            "ambiguous-id mutation should be stripped before reaching the UI",
            loaded.mutations.isEmpty()
        )
        assertEquals(1, loaded.strippedAmbiguousCount)
        assertEquals(
            "stripped mutation retained for picker recovery",
            1,
            loaded.strippedMutations.size
        )
        assertEquals("42", loaded.strippedMutations.single().entityId)
    }

    @Test
    fun loadPreview_keepsMutationsNotListedInAmbiguousCandidates() = runTest(dispatcher) {
        // Haiku flagged Wellbutrin as ambiguous (candidates 42, 43) but emitted
        // a mutation against entity 99 — that's NOT in the candidate list,
        // so it must pass through. Otherwise the safeguard becomes overzealous
        // and drops legitimate work.
        coEvery { repository.parseCommand(any()) } returns BatchParseResponse(
            mutations = listOf(
                medicationCompleteMutation(entityId = "99", slotKey = "evening")
            ),
            confidence = 0.7f,
            ambiguousEntities = listOf(
                AmbiguousEntityHintResponse(
                    phrase = "Wellbutrin",
                    candidateEntityType = "MEDICATION",
                    candidateEntityIds = listOf("42", "43")
                )
            )
        )

        val viewModel = newViewModel()
        viewModel.loadPreview("complex command")
        advanceUntilIdle()

        val loaded = viewModel.state.value as BatchPreviewState.Loaded
        assertEquals(1, loaded.mutations.size)
        assertEquals("99", loaded.mutations.single().entityId)
        assertEquals(0, loaded.strippedAmbiguousCount)
    }

    @Test
    fun loadPreview_emptyAmbiguousEntities_keepsAllMutations() = runTest(dispatcher) {
        coEvery { repository.parseCommand(any()) } returns BatchParseResponse(
            mutations = listOf(
                medicationCompleteMutation(entityId = "42", slotKey = "morning"),
                medicationCompleteMutation(entityId = "43", slotKey = "evening")
            ),
            confidence = 0.95f,
            ambiguousEntities = emptyList()
        )

        val viewModel = newViewModel()
        viewModel.loadPreview("took both meds")
        advanceUntilIdle()

        val loaded = viewModel.state.value as BatchPreviewState.Loaded
        assertEquals(2, loaded.mutations.size)
        assertEquals(0, loaded.strippedAmbiguousCount)
        assertTrue(loaded.strippedMutations.isEmpty())
    }

    @Test
    fun resolveAmbiguity_substitutesPickedEntityIdAndRemovesHint() = runTest(dispatcher) {
        coEvery { repository.parseCommand(any()) } returns BatchParseResponse(
            mutations = listOf(
                medicationCompleteMutation(entityId = "42", slotKey = "morning")
            ),
            confidence = 0.4f,
            ambiguousEntities = listOf(
                AmbiguousEntityHintResponse(
                    phrase = "Wellbutrin",
                    candidateEntityType = "MEDICATION",
                    candidateEntityIds = listOf("42", "43")
                )
            )
        )
        coEvery { repository.getMedicationsByIds(listOf(42L, 43L)) } returns listOf(
            MedicationEntity(id = 42L, name = "Wellbutrin XL 150mg"),
            MedicationEntity(id = 43L, name = "Wellbutrin SR 100mg")
        )

        val viewModel = newViewModel()
        viewModel.loadPreview("took my Wellbutrin")
        advanceUntilIdle()

        // Sanity: stripped state set up, picker candidates resolved.
        val loadedBefore = viewModel.state.value as BatchPreviewState.Loaded
        assertEquals(1, loadedBefore.strippedAmbiguousCount)
        assertNotNull(loadedBefore.medicationCandidates[0])

        // User picks the SR — substitute and re-emit.
        viewModel.resolveAmbiguity(hintIndex = 0, pickedEntityId = "43")

        val loadedAfter = viewModel.state.value as BatchPreviewState.Loaded
        assertEquals(
            "picker resolution adds one mutation back to the list",
            1,
            loadedAfter.mutations.size
        )
        val resolved = loadedAfter.mutations.single()
        assertEquals("43", resolved.entityId)
        assertEquals("COMPLETE", resolved.mutationType)
        assertEquals("morning", resolved.proposedNewValues["slot_key"])
        assertTrue("hint dropped after resolution", loadedAfter.ambiguousEntities.isEmpty())
        assertFalse(
            "stripped mutation consumed by resolution",
            loadedAfter.strippedMutations.any { it.entityId == "42" }
        )
        assertEquals(0, loadedAfter.strippedAmbiguousCount)
    }

    private fun newViewModel(): BatchPreviewViewModel = BatchPreviewViewModel(
        repository = repository,
        undoBus = undoBus,
        ndPreferencesDataStore = ndPreferencesDataStore
    )

    private fun medicationCompleteMutation(
        entityId: String,
        slotKey: String,
        date: String = "2026-04-25"
    ): ProposedMutationResponse = ProposedMutationResponse(
        entityType = "MEDICATION",
        entityId = entityId,
        mutationType = "COMPLETE",
        proposedNewValues = mapOf("slot_key" to slotKey, "date" to date),
        humanReadableDescription = "COMPLETE on medication $entityId"
    )
}
