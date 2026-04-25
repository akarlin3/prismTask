package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.repository.MedicationRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Cross-device sync scenarios for the medication subsystem (C3 of the
 * medication migration safety net). Both devices run the same schema
 * version — `SyncTestHarness` cannot pair Room DBs at different
 * versions; cross-version cases live in the manual runbook
 * (`docs/MEDICATION_MIGRATION_INSTRUMENTATION.md`).
 *
 * Each scenario exercises a real production sync path: the
 * `SyncService.pullRemoteChanges` pipeline at
 * `SyncService.kt:1952–2027` for slots/medications, plus the FK
 * resolution that ties `medication_doses` to its parent `medications`
 * row across the device boundary.
 *
 * Per memory `feedback_firestore_doc_iteration_order.md`, assertions
 * focus on convergence shape (row counts, FK integrity, junction
 * presence) — never on which `cloud_id` "wins" a natural-key dedup,
 * because the SDK's doc iteration order flips between CI runs.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MedicationCrossDeviceConvergenceTest : SyncScenarioTestBase() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    /**
     * Same-cloud_id concurrent edit: B's later write supersedes A's
     * local row on pull. Pin the last-write-wins contract for the
     * `medications` collection — beta installs will routinely
     * cross-edit the same med (refill date, pharmacy info) and the
     * loser must surface predictably.
     */
    @Test
    fun medicationLastWriteWins_remoteUpdateOverwritesLocal() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val medId = medicationRepository.insert(
                MedicationEntity(
                    name = "Lexapro",
                    notes = "A's original",
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
            syncService.pushLocalChanges()

            val cloudId = database.syncMetadataDao().getCloudId(medId, "medication")
            assertNotNull("medication cloud_id populated after push", cloudId)
            assertEquals(
                "exactly one Firestore medications doc after push",
                1,
                harness.firestoreCount("medications")
            )

            val futureUpdatedAt = System.currentTimeMillis() + 60_000L
            harness.writeAsDeviceB(
                subcollection = "medications",
                docId = cloudId!!,
                fields = mapOf(
                    "localId" to 9999L,
                    "name" to "Lexapro",
                    "notes" to "B's later write",
                    "tier" to "essential",
                    "isArchived" to false,
                    "sortOrder" to 0,
                    "scheduleMode" to "TIMES_OF_DAY",
                    "dosesPerDay" to 1,
                    "pillsPerDose" to 1,
                    "reminderDaysBefore" to 3,
                    "slotCloudIds" to emptyList<String>(),
                    "createdAt" to 0L,
                    "updatedAt" to futureUpdatedAt
                )
            )

            syncService.pullRemoteChanges()

            harness.waitFor(message = "B's note arrives on A") {
                database.medicationDao().getByIdOnce(medId)?.notes == "B's later write"
            }
            val finalLocal = database.medicationDao().getByIdOnce(medId)
            assertNotNull("local medication still present after pull", finalLocal)
            assertEquals("Lexapro", finalLocal!!.name)
            assertEquals("B's later write", finalLocal.notes)
            assertEquals(
                "Firestore unchanged at one doc (B overwrote in place)",
                1,
                harness.firestoreCount("medications")
            )
        }
    }

    /**
     * Cross-device parent FK resolution: A inserts the medication,
     * B writes a dose referencing it. After A pulls, the dose binds
     * to A's local medication via the cloud-id lookup at
     * `SyncService.kt:2032–2034`. Pin this so a future change to the
     * dose mapper that breaks `medicationCloudId` resolution surfaces
     * as a failing test, not a silent CASCADE-orphan in production.
     */
    @Test
    fun medicationDoseFkResolvesAcrossDevices() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val medId = medicationRepository.insert(
                MedicationEntity(
                    name = "Adderall",
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
            syncService.pushLocalChanges()
            val medCloudId = database.syncMetadataDao().getCloudId(medId, "medication")
            assertNotNull("parent medication cloud_id populated", medCloudId)

            val nowMs = System.currentTimeMillis()
            harness.writeAsDeviceB(
                subcollection = "medication_doses",
                docId = "dose-from-b",
                fields = mapOf(
                    "localId" to 8888L,
                    "medicationCloudId" to medCloudId!!,
                    "slotKey" to "morning",
                    "takenAt" to nowMs,
                    "takenDateLocal" to "2026-04-25",
                    "note" to "",
                    "isSyntheticSkip" to false,
                    "createdAt" to nowMs,
                    "updatedAt" to nowMs
                )
            )

            syncService.pullRemoteChanges()

            harness.waitFor(message = "B's dose lands locally with correct FK") {
                database.medicationDoseDao()
                    .getAllForMedOnce(medId)
                    .isNotEmpty()
            }
            val doses = database.medicationDoseDao()
                .getAllForMedOnce(medId)
            assertEquals(
                "exactly one dose tied to A's local medication after pull",
                1,
                doses.size
            )
            assertEquals(
                "dose's medicationId resolved to A's local row, not orphaned",
                medId,
                doses[0].medicationId
            )
        }
    }

    /**
     * Junction rebuild: B publishes a slot, then re-publishes the
     * medication with `slotCloudIds = [slot]`. A pulls and the
     * `medication_medication_slots` row reappears, even though A
     * never created the slot locally. Exercises the
     * pull-medications-after-slots ordering at
     * `SyncService.kt:1950–1980` and the junction rebuild at
     * `:2009–2027`.
     */
    @Test
    fun medicationSlotJunctionRebuildAfterRemoteSlotAdd() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val medId = medicationRepository.insert(
                MedicationEntity(
                    name = "Vitamin D",
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
            syncService.pushLocalChanges()
            val medCloudId = database.syncMetadataDao().getCloudId(medId, "medication")
            assertNotNull(medCloudId)

            val slotCloudId = "slot-from-b"
            val nowMs = System.currentTimeMillis()
            harness.writeAsDeviceB(
                subcollection = "medication_slots",
                docId = slotCloudId,
                fields = mapOf(
                    "localId" to 7777L,
                    "name" to "Lunch",
                    "idealTime" to "12:30",
                    "driftMinutes" to 90,
                    "sortOrder" to 1,
                    "isActive" to true,
                    "createdAt" to nowMs,
                    "updatedAt" to nowMs
                )
            )
            harness.writeAsDeviceB(
                subcollection = "medications",
                docId = medCloudId!!,
                fields = mapOf(
                    "localId" to 9999L,
                    "name" to "Vitamin D",
                    "notes" to "",
                    "tier" to "essential",
                    "isArchived" to false,
                    "sortOrder" to 0,
                    "scheduleMode" to "TIMES_OF_DAY",
                    "dosesPerDay" to 1,
                    "pillsPerDose" to 1,
                    "reminderDaysBefore" to 3,
                    "slotCloudIds" to listOf(slotCloudId),
                    "createdAt" to 0L,
                    "updatedAt" to nowMs + 60_000L
                )
            )

            syncService.pullRemoteChanges()

            harness.waitFor(message = "junction row appears on A") {
                database.medicationSlotDao()
                    .getSlotsForMedicationOnce(medId)
                    .isNotEmpty()
            }
            val linkedSlots = database.medicationSlotDao()
                .getSlotsForMedicationOnce(medId)
            assertEquals(
                "exactly one junction link after pull",
                1,
                linkedSlots.size
            )
            assertEquals("Lunch", linkedSlots[0].name)
            assertTrue(
                "slot has its updated_at populated from B's write",
                linkedSlots[0].updatedAt > 0L
            )
        }
    }

    companion object {
        private val TEST_TIMEOUT = 90.seconds
    }
}
