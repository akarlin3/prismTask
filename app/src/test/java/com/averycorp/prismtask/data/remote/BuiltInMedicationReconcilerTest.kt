package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.preferences.MedicationMigrationPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BuiltInMedicationReconciler] — the post-sync dedup
 * pass that collapses cross-device duplicates of the same medication
 * into the row with the most dose history (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §7.2).
 */
class BuiltInMedicationReconcilerTest {
    private lateinit var medicationDao: FakeMedicationDao
    private lateinit var medicationDoseDao: FakeMedicationDoseDao
    private lateinit var migrationPrefs: MedicationMigrationPreferences
    private lateinit var syncTracker: SyncTracker
    private lateinit var logger: PrismSyncLogger
    private lateinit var reconciler: BuiltInMedicationReconciler

    @Before
    fun setUp() {
        medicationDao = FakeMedicationDao()
        medicationDoseDao = FakeMedicationDoseDao()
        migrationPrefs = mockk(relaxed = true)
        syncTracker = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        // Default: reconciliation hasn't run yet.
        coEvery { migrationPrefs.isReconciliationDone() } returns false
        reconciler = BuiltInMedicationReconciler(
            medicationDao = medicationDao,
            medicationDoseDao = medicationDoseDao,
            migrationPreferences = migrationPrefs,
            syncTracker = syncTracker,
            logger = logger
        )
    }

    @Test
    fun noDuplicates_leavesEveryMedicationInPlace() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 1, name = "Lipitor"))
        medicationDao.insert(MedicationEntity(id = 2, name = "Adderall"))
        medicationDao.insert(MedicationEntity(id = 3, name = "Metformin"))

        reconciler.reconcileAfterSyncIfNeeded()

        assertEquals(3, medicationDao.rows.size)
        assertEquals(setOf(1L, 2L, 3L), medicationDao.rows.map { it.id }.toSet())
    }

    @Test
    fun duplicateNames_collapseToRowWithMostDoseHistory() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 10, name = "Lipitor"))
        medicationDao.insert(MedicationEntity(id = 11, name = "Lipitor"))
        medicationDao.insert(MedicationEntity(id = 12, name = "Lipitor"))

        // id=10 → 3 doses, id=11 → 1 dose, id=12 → 0 doses.
        // Winner should be 10.
        repeat(3) { medicationDoseDao.insertDoseForMed(10L) }
        medicationDoseDao.insertDoseForMed(11L)

        reconciler.reconcileAfterSyncIfNeeded()

        val keeper = medicationDao.rows.singleOrNull { it.name == "Lipitor" }
        assertEquals("exactly one Lipitor row survives", 10L, keeper?.id)
    }

    @Test
    fun duplicates_reassignDoseHistoryToKeeper() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 20, name = "Adderall"))
        medicationDao.insert(MedicationEntity(id = 21, name = "Adderall"))

        // Winner = 20 (more doses).
        repeat(2) { medicationDoseDao.insertDoseForMed(20L) }
        medicationDoseDao.insertDoseForMed(21L)

        reconciler.reconcileAfterSyncIfNeeded()

        // All three doses should now point at the keeper.
        assertEquals(3, medicationDoseDao.rows.count { it.medicationId == 20L })
        assertEquals(0, medicationDoseDao.rows.count { it.medicationId == 21L })
    }

    @Test
    fun duplicates_tieBreakOnSmallestId() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 99, name = "Vitamin D"))
        medicationDao.insert(MedicationEntity(id = 30, name = "Vitamin D"))
        medicationDao.insert(MedicationEntity(id = 50, name = "Vitamin D"))
        // All zero doses — tiebreak goes to smallest id.

        reconciler.reconcileAfterSyncIfNeeded()

        val keeper = medicationDao.rows.singleOrNull { it.name == "Vitamin D" }
        assertEquals("smallest id wins on zero-dose tie", 30L, keeper?.id)
    }

    @Test
    fun namesAreCaseAndWhitespaceInsensitiveForGrouping() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 40, name = "Lipitor"))
        medicationDao.insert(MedicationEntity(id = 41, name = "  lipitor "))
        medicationDao.insert(MedicationEntity(id = 42, name = "LIPITOR"))

        reconciler.reconcileAfterSyncIfNeeded()

        val survivors = medicationDao.rows.filter {
            it.name.trim().equals("lipitor", ignoreCase = true)
        }
        assertEquals(
            "all three name variants collapse to one row",
            1,
            survivors.size
        )
    }

    @Test
    fun runningTwice_isIdempotent() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 1, name = "Lipitor"))
        medicationDao.insert(MedicationEntity(id = 2, name = "Lipitor"))

        // First pass: runs, dedups, and sets the flag.
        coEvery { migrationPrefs.isReconciliationDone() } returns false
        reconciler.reconcileAfterSyncIfNeeded()
        val countAfterFirst = medicationDao.rows.size

        // Second pass: flag is set → early return, nothing changes.
        coEvery { migrationPrefs.isReconciliationDone() } returns true
        medicationDao.insert(MedicationEntity(id = 3, name = "Lipitor"))
        reconciler.reconcileAfterSyncIfNeeded()

        assertEquals(
            "second pass no-ops once the flag is set",
            countAfterFirst + 1,
            medicationDao.rows.size
        )
    }

    @Test
    fun singleRow_isANoOp() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 1, name = "Lipitor"))

        reconciler.reconcileAfterSyncIfNeeded()

        assertEquals(1, medicationDao.rows.size)
    }

    @Test
    fun emptyDb_isANoOp() = runBlocking {
        reconciler.reconcileAfterSyncIfNeeded()
        assertTrue(medicationDao.rows.isEmpty())
    }

    @Test
    fun duplicates_queueLoserMedicationForCloudDelete() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 50, name = "Lipitor"))
        medicationDao.insert(MedicationEntity(id = 51, name = "Lipitor"))
        // Winner = 50 (smallest id; both zero doses).

        reconciler.reconcileAfterSyncIfNeeded()

        // Without trackDelete the loser's cloud document survives; the
        // next pull re-introduces the duplicate locally as a fresh row
        // with a fresh local_id, undoing the dedup.
        coVerify(exactly = 1) { syncTracker.trackDelete(51L, "medication") }
    }

    @Test
    fun duplicates_queueReassignedDosesForCloudUpdate() = runBlocking {
        medicationDao.insert(MedicationEntity(id = 60, name = "Adderall"))
        medicationDao.insert(MedicationEntity(id = 61, name = "Adderall"))

        // id=60 wins on dose count; id=61's two doses get reassigned.
        repeat(3) { medicationDoseDao.insertDoseForMed(60L) }
        val loserDose1 = medicationDoseDao.insertAndReturnId(61L)
        val loserDose2 = medicationDoseDao.insertAndReturnId(61L)

        reconciler.reconcileAfterSyncIfNeeded()

        // Each reassigned dose's cloud copy still carries the loser's
        // medicationCloudId — push must re-upload them so the cloud
        // medicationCloudId resolves to the keeper.
        coVerify(exactly = 1) { syncTracker.trackUpdate(loserDose1, "medication_dose") }
        coVerify(exactly = 1) { syncTracker.trackUpdate(loserDose2, "medication_dose") }
    }
}

// --- in-memory fake DAOs ------------------------------------------------

private class FakeMedicationDao : MedicationDao {
    val rows = mutableListOf<MedicationEntity>()

    override suspend fun insert(medication: MedicationEntity): Long {
        rows += medication
        return medication.id
    }

    override suspend fun update(medication: MedicationEntity) {
        val idx = rows.indexOfFirst { it.id == medication.id }
        if (idx >= 0) rows[idx] = medication
    }

    override suspend fun archive(id: Long, now: Long) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(isArchived = true, updatedAt = now)
    }

    override suspend fun delete(medication: MedicationEntity) {
        rows.removeAll { it.id == medication.id }
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun getByIdOnce(id: Long): MedicationEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun getByNameOnce(name: String): MedicationEntity? =
        rows.firstOrNull { it.name == name }

    override suspend fun getActiveOnce(): List<MedicationEntity> =
        rows.filter { !it.isArchived }

    override suspend fun getAllOnce(): List<MedicationEntity> =
        rows.toList()

    override suspend fun getByCloudIdOnce(cloudId: String): MedicationEntity? =
        rows.firstOrNull { it.cloudId == cloudId }

    override suspend fun setCloudId(id: Long, cloudId: String?, now: Long) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(cloudId = cloudId, updatedAt = now)
    }

    override fun getActive() = error("flow read not exercised in this test")
    override fun getAll() = error("flow read not exercised in this test")
    override fun observeById(id: Long) = error("flow read not exercised in this test")

    override suspend fun getIntervalModeMedicationsOnce(): List<MedicationEntity> =
        rows.filter { !it.isArchived && it.reminderMode == "INTERVAL" }
}

private class FakeMedicationDoseDao : MedicationDoseDao {
    val rows = mutableListOf<MedicationDoseEntity>()
    private var nextId = 1L

    fun insertDoseForMed(medicationId: Long) {
        insertAndReturnId(medicationId)
    }

    fun insertAndReturnId(medicationId: Long): Long {
        val id = nextId++
        rows += MedicationDoseEntity(
            id = id,
            medicationId = medicationId,
            slotKey = "anytime",
            takenAt = System.currentTimeMillis(),
            takenDateLocal = "2026-04-22"
        )
        return id
    }

    override suspend fun insert(dose: MedicationDoseEntity): Long {
        val withId = if (dose.id == 0L) dose.copy(id = nextId++) else dose
        rows += withId
        return withId.id
    }

    override suspend fun update(dose: MedicationDoseEntity) {
        val idx = rows.indexOfFirst { it.id == dose.id }
        if (idx >= 0) rows[idx] = dose
    }

    override suspend fun delete(dose: MedicationDoseEntity) {
        rows.removeAll { it.id == dose.id }
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun countForMedOnce(medicationId: Long): Int =
        rows.count { it.medicationId == medicationId }

    override suspend fun countForMedOnDateOnce(medicationId: Long, date: String): Int =
        rows.count { it.medicationId == medicationId && it.takenDateLocal == date }

    override suspend fun getAllOnce(): List<MedicationDoseEntity> = rows.toList()

    override suspend fun getAllForMedOnce(medicationId: Long): List<MedicationDoseEntity> =
        rows.filter { it.medicationId == medicationId }

    override suspend fun getLatestForMedOnce(medicationId: Long): MedicationDoseEntity? =
        rows.filter { it.medicationId == medicationId }.maxByOrNull { it.takenAt }

    override suspend fun getByCloudIdOnce(cloudId: String): MedicationDoseEntity? =
        rows.firstOrNull { it.cloudId == cloudId }

    override suspend fun setCloudId(id: Long, cloudId: String?, now: Long) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(cloudId = cloudId, updatedAt = now)
    }

    override suspend fun reassignMedicationId(oldId: Long, newId: Long) {
        val updated = rows.map { if (it.medicationId == oldId) it.copy(medicationId = newId) else it }
        rows.clear()
        rows += updated
    }

    override fun observeAll() = error("flow read not exercised in this test")
    override fun getForDate(date: String) = error("flow read not exercised in this test")
    override fun getForMedOnDate(medicationId: Long, date: String) =
        error("flow read not exercised in this test")

    override suspend fun getMostRecentDoseAnyOnce(): MedicationDoseEntity? =
        rows.maxByOrNull { it.takenAt }

    override fun observeMostRecentDoseAny() =
        error("flow read not exercised in this test")

    override suspend fun getMostRecentRealDoseOnce(): MedicationDoseEntity? =
        rows.filterNot { it.isSyntheticSkip }.maxByOrNull { it.takenAt }
}
