package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncTracker
    @Inject
    constructor(
        private val authManager: AuthManager,
        private val syncMetadataDao: SyncMetadataDao
    ) {
        private val isActive: Boolean get() = authManager.userId != null

        suspend fun trackCreate(localId: Long, entityType: String) {
            if (!isActive) return
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    localId = localId,
                    entityType = entityType,
                    pendingAction = "create",
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
        }

        suspend fun trackUpdate(localId: Long, entityType: String) {
            if (!isActive) return
            val existing = syncMetadataDao.get(localId, entityType)
            if (existing == null) {
                // Never synced — treat as create
                trackCreate(localId, entityType)
            } else if (existing.pendingAction == "create") {
                // Still pending create — leave it as create
            } else {
                syncMetadataDao.upsert(existing.copy(pendingAction = "update"))
            }
        }

        suspend fun trackDelete(localId: Long, entityType: String) {
            if (!isActive) return
            val existing = syncMetadataDao.get(localId, entityType)
            if (existing == null) return // never synced, nothing to delete remotely
            if (existing.cloudId.isEmpty()) {
                // Never made it to the cloud — just remove the metadata
                syncMetadataDao.delete(localId, entityType)
            } else {
                syncMetadataDao.upsert(existing.copy(pendingAction = "delete"))
            }
        }
    }
