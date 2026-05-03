package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.AutomationLogDao
import com.averycorp.prismtask.data.local.dao.AutomationRuleDao
import com.averycorp.prismtask.data.local.entity.AutomationLogEntity
import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationCondition
import com.averycorp.prismtask.domain.automation.AutomationJsonAdapter
import com.averycorp.prismtask.domain.automation.AutomationTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns CRUD over [AutomationRuleEntity] + [AutomationLogEntity]. The
 * engine ([AutomationEngine]) reads via this repository so test doubles
 * can stub it; UI viewmodels do the same.
 */
@Singleton
class AutomationRuleRepository @Inject constructor(
    private val ruleDao: AutomationRuleDao,
    private val logDao: AutomationLogDao,
    private val syncTracker: SyncTracker
) {
    fun observeAll(): Flow<List<AutomationRuleEntity>> = ruleDao.observeAll()
    fun observeEnabled(): Flow<List<AutomationRuleEntity>> = ruleDao.observeEnabled()
    suspend fun getEnabledOnce(): List<AutomationRuleEntity> = ruleDao.getEnabledOnce()
    suspend fun getAllOnce(): List<AutomationRuleEntity> = ruleDao.observeAll().first()
    suspend fun getByIdOnce(id: Long): AutomationRuleEntity? = ruleDao.getByIdOnce(id)
    suspend fun getByTemplateKeyOnce(key: String): AutomationRuleEntity? =
        ruleDao.getByTemplateKeyOnce(key)
    suspend fun getTimeBasedEnabledOnce(): List<AutomationRuleEntity> =
        ruleDao.getTimeBasedEnabledOnce()

    fun observeRecentLogs(limit: Int = 200): Flow<List<AutomationLogEntity>> =
        logDao.observeRecent(limit)
    fun observeLogsForRule(ruleId: Long, limit: Int = 100): Flow<List<AutomationLogEntity>> =
        logDao.observeForRule(ruleId, limit)

    suspend fun create(
        name: String,
        description: String?,
        trigger: AutomationTrigger,
        condition: AutomationCondition?,
        actions: List<AutomationAction>,
        priority: Int = 0,
        enabled: Boolean = true,
        isBuiltIn: Boolean = false,
        templateKey: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val rule = AutomationRuleEntity(
            name = name,
            description = description,
            enabled = enabled,
            priority = priority,
            isBuiltIn = isBuiltIn,
            templateKey = templateKey,
            triggerJson = AutomationJsonAdapter.encodeTrigger(trigger),
            conditionJson = AutomationJsonAdapter.encodeCondition(condition),
            actionJson = AutomationJsonAdapter.encodeActions(actions),
            createdAt = now,
            updatedAt = now
        )
        val id = ruleDao.insert(rule)
        syncTracker.trackCreate(id, "automation_rule")
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        ruleDao.setEnabled(id, enabled)
        syncTracker.trackUpdate(id, "automation_rule")
    }

    suspend fun update(rule: AutomationRuleEntity) {
        ruleDao.update(rule.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(rule.id, "automation_rule")
    }

    suspend fun delete(id: Long) {
        ruleDao.deleteById(id)
        syncTracker.trackDelete(id, "automation_rule")
    }

    suspend fun recordFiring(
        ruleId: Long,
        firedAt: Long,
        triggerEventJson: String?,
        conditionPassed: Boolean,
        actionsExecutedJson: String?,
        errorsJson: String?,
        durationMs: Long,
        chainDepth: Int,
        parentLogId: Long?
    ): Long = logDao.insert(
        AutomationLogEntity(
            ruleId = ruleId,
            firedAt = firedAt,
            triggerEventJson = triggerEventJson,
            conditionPassed = conditionPassed,
            actionsExecutedJson = actionsExecutedJson,
            errorsJson = errorsJson,
            durationMs = durationMs,
            chainDepth = chainDepth,
            parentLogId = parentLogId
        )
    )

    suspend fun bumpFireCount(rule: AutomationRuleEntity, today: String, now: Long) {
        // Reset the daily counter when the day rolls over before bumping;
        // this keeps the rate-limit semantics correct across midnight.
        if (rule.dailyFireCountDate != today) {
            ruleDao.resetDailyCounter(rule.id, today, now)
        }
        ruleDao.incrementFireCount(rule.id, now)
    }

    suspend fun pruneLogsOlderThan(cutoff: Long): Int = logDao.deleteOlderThan(cutoff)
}
