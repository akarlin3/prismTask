package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.builtInSyncDataStore: DataStore<Preferences> by preferencesDataStore(name = "built_in_sync_prefs")

/**
 * Guards the one-time built-in habit reconciliation passes so neither
 * the local drift cleanup nor the post-sync reconciliation re-runs after
 * it has already succeeded.
 */
@Singleton
class BuiltInSyncPreferences
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val BUILT_INS_RECONCILED = booleanPreferencesKey("built_ins_reconciled")
        private val DRIFT_CLEANUP_DONE = booleanPreferencesKey("drift_cleanup_done")
        private val BUILT_IN_BACKFILL_DONE = booleanPreferencesKey("built_in_backfill_done")
        private val NEW_ENTITIES_BACKFILL_DONE = booleanPreferencesKey("new_entities_backfill_done")
        private val INITIAL_UPLOAD_DONE = booleanPreferencesKey("initial_upload_done")
    }

    suspend fun isBuiltInsReconciled(): Boolean =
        context.builtInSyncDataStore.data.first()[BUILT_INS_RECONCILED] ?: false

    suspend fun setBuiltInsReconciled(done: Boolean) {
        context.builtInSyncDataStore.edit { it[BUILT_INS_RECONCILED] = done }
    }

    suspend fun isDriftCleanupDone(): Boolean =
        context.builtInSyncDataStore.data.first()[DRIFT_CLEANUP_DONE] ?: false

    suspend fun setDriftCleanupDone(done: Boolean) {
        context.builtInSyncDataStore.edit { it[DRIFT_CLEANUP_DONE] = done }
    }

    suspend fun isBuiltInBackfillDone(): Boolean =
        context.builtInSyncDataStore.data.first()[BUILT_IN_BACKFILL_DONE] ?: false

    suspend fun setBuiltInBackfillDone(done: Boolean) {
        context.builtInSyncDataStore.edit { it[BUILT_IN_BACKFILL_DONE] = done }
    }

    suspend fun isNewEntitiesBackfillDone(): Boolean =
        context.builtInSyncDataStore.data.first()[NEW_ENTITIES_BACKFILL_DONE] ?: false

    suspend fun setNewEntitiesBackfillDone(done: Boolean) {
        context.builtInSyncDataStore.edit { it[NEW_ENTITIES_BACKFILL_DONE] = done }
    }

    /**
     * Guard for [com.averycorp.prismtask.data.remote.SyncService.initialUpload].
     * Set to true only after the upload loop finishes successfully; stays false
     * on failure so a retry can happen on the next sign-in. Prevents the
     * duplication spiral where every sign-in re-uploaded every local row as a
     * brand-new Firestore doc.
     */
    suspend fun isInitialUploadDone(): Boolean =
        context.builtInSyncDataStore.data.first()[INITIAL_UPLOAD_DONE] ?: false

    suspend fun setInitialUploadDone(done: Boolean) {
        context.builtInSyncDataStore.edit { it[INITIAL_UPLOAD_DONE] = done }
    }
}
