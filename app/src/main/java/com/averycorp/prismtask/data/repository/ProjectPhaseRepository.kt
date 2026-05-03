package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.ProjectPhaseDao
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectPhaseRepository
@Inject
constructor(
    private val phaseDao: ProjectPhaseDao,
    private val syncTracker: SyncTracker
) {
    fun observePhases(projectId: Long): Flow<List<ProjectPhaseEntity>> =
        phaseDao.observePhases(projectId)

    suspend fun getPhasesOnce(projectId: Long): List<ProjectPhaseEntity> =
        phaseDao.getPhasesOnce(projectId)

    suspend fun getById(id: Long): ProjectPhaseEntity? = phaseDao.getByIdOnce(id)

    suspend fun addPhase(
        projectId: Long,
        title: String,
        description: String? = null,
        colorKey: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        versionAnchor: String? = null,
        versionNote: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val nextOrder = phaseDao.getMaxOrderIndex(projectId) + 1
        val phase = ProjectPhaseEntity(
            projectId = projectId,
            title = title,
            description = description,
            colorKey = colorKey,
            startDate = startDate,
            endDate = endDate,
            versionAnchor = versionAnchor,
            versionNote = versionNote,
            orderIndex = nextOrder,
            createdAt = now,
            updatedAt = now
        )
        val id = phaseDao.insert(phase)
        syncTracker.trackCreate(id, ENTITY_TYPE)
        return id
    }

    suspend fun updatePhase(phase: ProjectPhaseEntity) {
        phaseDao.update(phase.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(phase.id, ENTITY_TYPE)
    }

    suspend fun markCompleted(id: Long, completedAt: Long = System.currentTimeMillis()) {
        val phase = phaseDao.getByIdOnce(id) ?: return
        updatePhase(phase.copy(completedAt = completedAt))
    }

    suspend fun reorder(phases: List<ProjectPhaseEntity>) {
        val now = System.currentTimeMillis()
        val updated = phases.mapIndexed { index, phase ->
            phase.copy(orderIndex = index, updatedAt = now)
        }
        phaseDao.updateAll(updated)
        for (phase in updated) {
            syncTracker.trackUpdate(phase.id, ENTITY_TYPE)
        }
    }

    suspend fun deletePhase(id: Long) {
        syncTracker.trackDelete(id, ENTITY_TYPE)
        phaseDao.deleteById(id)
    }

    companion object { const val ENTITY_TYPE = "project_phase" }
}
