package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.AutomationRuleDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AutomationDuplicateBackfiller] — the one-shot pass that
 * collapses cross-device duplicate automation rules sharing the same
 * `template_key` (audit `AUTOMATION_PHASE_I_POLISH_AUDIT.md` Item 2).
 */
class AutomationDuplicateBackfillerTest {
    private lateinit var ruleDao: FakeAutomationRuleDao
    private lateinit var syncMetadataDao: SyncMetadataDao
    private lateinit var syncTracker: SyncTracker
    private lateinit var prefs: BuiltInSyncPreferences
    private lateinit var logger: PrismSyncLogger
    private lateinit var backfiller: AutomationDuplicateBackfiller

    @Before
    fun setUp() {
        ruleDao = FakeAutomationRuleDao()
        syncMetadataDao = mockk(relaxed = true)
        syncTracker = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        coEvery { prefs.isAutomationDupBackfillDone() } returns false
        backfiller = AutomationDuplicateBackfiller(
            automationRuleDao = ruleDao,
            syncTracker = syncTracker,
            syncMetadataDao = syncMetadataDao,
            builtInSyncPreferences = prefs,
            logger = logger
        )
    }

    @Test
    fun emptyDb_isANoOpAndSetsFlag() = runBlocking {
        backfiller.runIfNeeded()
        assertTrue(ruleDao.rows.isEmpty())
        coVerify(exactly = 1) { prefs.setAutomationDupBackfillDone(true) }
    }

    @Test
    fun noDuplicates_leavesEveryRuleInPlace() = runBlocking {
        ruleDao.rows += rule(id = 1, templateKey = "starter.a", updatedAt = 100)
        ruleDao.rows += rule(id = 2, templateKey = "starter.b", updatedAt = 200)
        ruleDao.rows += rule(id = 3, templateKey = null, updatedAt = 300)

        backfiller.runIfNeeded()

        assertEquals(3, ruleDao.rows.size)
        coVerify(exactly = 0) { syncTracker.trackDelete(any(), any()) }
    }

    @Test
    fun duplicateTemplateKey_keepsNewestUpdatedAt() = runBlocking {
        ruleDao.rows += rule(id = 10, templateKey = "starter.med", updatedAt = 100)
        ruleDao.rows += rule(id = 11, templateKey = "starter.med", updatedAt = 200)
        ruleDao.rows += rule(id = 12, templateKey = "starter.med", updatedAt = 150)

        backfiller.runIfNeeded()

        val survivors = ruleDao.rows.filter { it.templateKey == "starter.med" }
        assertEquals("only newest survives", listOf(11L), survivors.map { it.id })
    }

    @Test
    fun tieOnUpdatedAt_breaksToSmallestId() = runBlocking {
        ruleDao.rows += rule(id = 99, templateKey = "starter.x", updatedAt = 500)
        ruleDao.rows += rule(id = 30, templateKey = "starter.x", updatedAt = 500)
        ruleDao.rows += rule(id = 50, templateKey = "starter.x", updatedAt = 500)

        backfiller.runIfNeeded()

        val survivor = ruleDao.rows.singleOrNull { it.templateKey == "starter.x" }
        assertEquals("smallest id wins on updatedAt tie", 30L, survivor?.id)
    }

    @Test
    fun nullTemplateKey_isNeverDeduplicated() = runBlocking {
        ruleDao.rows += rule(id = 1, templateKey = null, updatedAt = 100, name = "User rule A")
        ruleDao.rows += rule(id = 2, templateKey = null, updatedAt = 100, name = "User rule B")
        ruleDao.rows += rule(id = 3, templateKey = "", updatedAt = 100, name = "Edge case empty")

        backfiller.runIfNeeded()

        assertEquals(
            "user-authored rules with null/empty templateKey survive untouched",
            setOf(1L, 2L, 3L),
            ruleDao.rows.map { it.id }.toSet()
        )
        coVerify(exactly = 0) { syncTracker.trackDelete(any(), any()) }
    }

    @Test
    fun mixedDuplicatesAndUserRules_collapsesOnlyTemplated() = runBlocking {
        ruleDao.rows += rule(id = 1, templateKey = "starter.x", updatedAt = 100)
        ruleDao.rows += rule(id = 2, templateKey = "starter.x", updatedAt = 200)
        ruleDao.rows += rule(id = 3, templateKey = null, updatedAt = 100)
        ruleDao.rows += rule(id = 4, templateKey = null, updatedAt = 100)

        backfiller.runIfNeeded()

        assertEquals(
            "loser templated row gone; both user rows survive",
            setOf(2L, 3L, 4L),
            ruleDao.rows.map { it.id }.toSet()
        )
    }

    @Test
    fun duplicates_queueLoserForCloudDelete() = runBlocking {
        ruleDao.rows += rule(id = 50, templateKey = "starter.x", updatedAt = 100)
        ruleDao.rows += rule(id = 51, templateKey = "starter.x", updatedAt = 200)

        backfiller.runIfNeeded()

        coVerify(exactly = 1) { syncTracker.trackDelete(50L, "automation_rule") }
        coVerify(exactly = 1) { syncMetadataDao.delete(50L, "automation_rule") }
    }

    @Test
    fun runningTwice_isIdempotentViaFlag() = runBlocking {
        ruleDao.rows += rule(id = 1, templateKey = "starter.x", updatedAt = 100)
        ruleDao.rows += rule(id = 2, templateKey = "starter.x", updatedAt = 200)

        coEvery { prefs.isAutomationDupBackfillDone() } returns false
        backfiller.runIfNeeded()
        val countAfterFirst = ruleDao.rows.size

        coEvery { prefs.isAutomationDupBackfillDone() } returns true
        ruleDao.rows += rule(id = 3, templateKey = "starter.x", updatedAt = 300)
        backfiller.runIfNeeded()

        assertEquals(
            "second pass no-ops once the flag is set",
            countAfterFirst + 1,
            ruleDao.rows.size
        )
    }

    @Test
    fun failureLeavesFlagUnsetForRetry() = runBlocking {
        ruleDao.failOnGetAll = true
        backfiller.runIfNeeded()
        coVerify(exactly = 0) { prefs.setAutomationDupBackfillDone(true) }
    }

    private fun rule(
        id: Long,
        templateKey: String?,
        updatedAt: Long,
        name: String = "rule_$id"
    ) = AutomationRuleEntity(
        id = id,
        name = name,
        templateKey = templateKey,
        triggerJson = "{}",
        actionJson = "[]",
        createdAt = 0L,
        updatedAt = updatedAt
    )
}

private class FakeAutomationRuleDao : AutomationRuleDao {
    val rows = mutableListOf<AutomationRuleEntity>()
    var failOnGetAll: Boolean = false

    override suspend fun getAllOnce(): List<AutomationRuleEntity> {
        check(!failOnGetAll) { "simulated DAO failure" }
        return rows.toList()
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun getByIdOnce(id: Long): AutomationRuleEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun getByTemplateKeyOnce(templateKey: String): AutomationRuleEntity? =
        rows.firstOrNull { it.templateKey == templateKey }

    // The remaining DAO surface isn't exercised by this test.
    override fun observeAll(): Flow<List<AutomationRuleEntity>> =
        error("not exercised in this test")

    override fun observeEnabled(): Flow<List<AutomationRuleEntity>> =
        error("not exercised in this test")

    override suspend fun getEnabledOnce(): List<AutomationRuleEntity> =
        error("not exercised in this test")

    override suspend fun setCloudId(id: Long, cloudId: String) =
        error("not exercised in this test")

    override suspend fun findIdByCloudId(cloudId: String): Long? =
        error("not exercised in this test")

    override suspend fun insert(rule: AutomationRuleEntity): Long =
        error("not exercised in this test")

    override suspend fun update(rule: AutomationRuleEntity) =
        error("not exercised in this test")

    override suspend fun setEnabled(id: Long, enabled: Boolean, now: Long) =
        error("not exercised in this test")

    override suspend fun incrementFireCount(id: Long, now: Long) =
        error("not exercised in this test")

    override suspend fun resetDailyCounter(id: Long, today: String, now: Long) =
        error("not exercised in this test")

    override suspend fun getTimeBasedEnabledOnce(): List<AutomationRuleEntity> =
        error("not exercised in this test")
}

/**
 * SyncMetadataEntity reference kept so the import resolves cleanly under
 * test classpath; the class itself isn't constructed here.
 */
@Suppress("unused")
private val syncMetadataEntityRef: Class<SyncMetadataEntity> = SyncMetadataEntity::class.java
