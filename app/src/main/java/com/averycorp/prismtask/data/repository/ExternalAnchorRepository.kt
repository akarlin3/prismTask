package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.ExternalAnchorDao
import com.averycorp.prismtask.data.local.entity.ExternalAnchorEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.anchor.ExternalAnchorJsonAdapter
import com.averycorp.prismtask.domain.model.ExternalAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [ExternalAnchorEntity] rows. Callers work with the typed
 * [ExternalAnchor] sealed hierarchy; the repository round-trips through
 * [ExternalAnchorJsonAdapter] before hitting the DAO so the storage
 * layer never sees the variant types directly.
 */
@Singleton
class ExternalAnchorRepository
@Inject
constructor(
    private val anchorDao: ExternalAnchorDao,
    private val syncTracker: SyncTracker
) {
    fun observeAnchorsForProject(projectId: Long): Flow<List<ResolvedAnchor>> =
        anchorDao.observeAnchorsForProject(projectId).map { rows -> rows.map(::resolve) }

    fun observeAnchorsForPhase(phaseId: Long): Flow<List<ResolvedAnchor>> =
        anchorDao.observeAnchorsForPhase(phaseId).map { rows -> rows.map(::resolve) }

    suspend fun getAnchorsForProjectOnce(projectId: Long): List<ResolvedAnchor> =
        anchorDao.getAnchorsForProjectOnce(projectId).map(::resolve)

    suspend fun addAnchor(
        projectId: Long,
        label: String,
        anchor: ExternalAnchor,
        phaseId: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        val row = ExternalAnchorEntity(
            projectId = projectId,
            phaseId = phaseId,
            label = label,
            anchorJson = ExternalAnchorJsonAdapter.encode(anchor),
            createdAt = now,
            updatedAt = now
        )
        val id = anchorDao.insert(row)
        syncTracker.trackCreate(id, ENTITY_TYPE)
        return id
    }

    suspend fun updateAnchor(
        id: Long,
        label: String? = null,
        anchor: ExternalAnchor? = null,
        phaseId: Long? = null
    ) {
        val current = anchorDao.getByIdOnce(id) ?: return
        val updated = current.copy(
            label = label ?: current.label,
            anchorJson = anchor?.let(ExternalAnchorJsonAdapter::encode) ?: current.anchorJson,
            phaseId = phaseId ?: current.phaseId,
            updatedAt = System.currentTimeMillis()
        )
        anchorDao.update(updated)
        syncTracker.trackUpdate(id, ENTITY_TYPE)
    }

    suspend fun deleteAnchor(id: Long) {
        syncTracker.trackDelete(id, ENTITY_TYPE)
        anchorDao.deleteById(id)
    }

    private fun resolve(row: ExternalAnchorEntity): ResolvedAnchor =
        ResolvedAnchor(row, ExternalAnchorJsonAdapter.decode(row.anchorJson))

    /**
     * View-model-friendly pairing of the persisted row with its parsed
     * anchor variant. [anchor] is null when the JSON is malformed —
     * UI surfaces it as a "couldn't parse anchor" placeholder rather
     * than crashing.
     */
    data class ResolvedAnchor(
        val row: ExternalAnchorEntity,
        val anchor: ExternalAnchor?
    )

    companion object {
        const val ENTITY_TYPE = "external_anchor"
    }
}
