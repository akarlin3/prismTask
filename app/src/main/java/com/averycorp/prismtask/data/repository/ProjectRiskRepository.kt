package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.ProjectRiskDao
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.model.RiskLevel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRiskRepository
@Inject
constructor(
    private val riskDao: ProjectRiskDao,
    private val syncTracker: SyncTracker
) {
    fun observeRisks(projectId: Long): Flow<List<ProjectRiskEntity>> =
        riskDao.observeRisks(projectId)

    fun observeActiveRisks(projectId: Long): Flow<List<ProjectRiskEntity>> =
        riskDao.observeActiveRisks(projectId)

    suspend fun getById(id: Long): ProjectRiskEntity? = riskDao.getByIdOnce(id)

    suspend fun addRisk(
        projectId: Long,
        title: String,
        level: RiskLevel,
        mitigation: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val risk = ProjectRiskEntity(
            projectId = projectId,
            title = title,
            level = level.name,
            mitigation = mitigation,
            createdAt = now,
            updatedAt = now
        )
        val id = riskDao.insert(risk)
        syncTracker.trackCreate(id, ENTITY_TYPE)
        return id
    }

    suspend fun updateRisk(risk: ProjectRiskEntity) {
        riskDao.update(risk.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(risk.id, ENTITY_TYPE)
    }

    suspend fun resolve(id: Long, at: Long = System.currentTimeMillis()) {
        val risk = riskDao.getByIdOnce(id) ?: return
        updateRisk(risk.copy(resolvedAt = at))
    }

    suspend fun reopen(id: Long) {
        val risk = riskDao.getByIdOnce(id) ?: return
        updateRisk(risk.copy(resolvedAt = null))
    }

    suspend fun deleteRisk(id: Long) {
        syncTracker.trackDelete(id, ENTITY_TYPE)
        riskDao.deleteById(id)
    }

    companion object {
        const val ENTITY_TYPE = "project_risk"
    }
}
