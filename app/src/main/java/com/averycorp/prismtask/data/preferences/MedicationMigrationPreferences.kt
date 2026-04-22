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

private val Context.medicationMigrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "medication_migration_prefs"
)

/**
 * One-shot guard flags for the v53 → v54 medication-top-level migration
 * follow-up passes. Each flag is set only after the corresponding pass
 * succeeds so a mid-run crash stays retryable on the next app start.
 *
 * See `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §4.4.
 */
@Singleton
class MedicationMigrationPreferences
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SCHEDULE_PRESERVED = booleanPreferencesKey("schedule_preserved")
        private val DOSE_BACKFILL_DONE = booleanPreferencesKey("dose_backfill_done")
        private val RECONCILIATION_DONE = booleanPreferencesKey("reconciliation_done")
        private val MIGRATION_PUSHED_TO_CLOUD = booleanPreferencesKey("migration_pushed_to_cloud")
        private val SOURCE_DATA_PURGED_PHASE_2 = booleanPreferencesKey("source_data_purged_phase_2")
    }

    /**
     * True once `MedicationMigrationRunner.preserveScheduleIfNeeded` has
     * written the user's pre-migration global schedule onto every row in
     * `medications`.
     */
    suspend fun isSchedulePreserved(): Boolean =
        context.medicationMigrationDataStore.data.first()[SCHEDULE_PRESERVED] ?: false

    suspend fun setSchedulePreserved(done: Boolean) {
        context.medicationMigrationDataStore.edit { it[SCHEDULE_PRESERVED] = done }
    }

    /**
     * True once `MedicationMigrationRunner.backfillDosesIfNeeded` has
     * parsed every legacy `self_care_logs` row (where
     * `routine_type='medication'`) into `medication_doses` rows.
     */
    suspend fun isDoseBackfillDone(): Boolean =
        context.medicationMigrationDataStore.data.first()[DOSE_BACKFILL_DONE] ?: false

    suspend fun setDoseBackfillDone(done: Boolean) {
        context.medicationMigrationDataStore.edit { it[DOSE_BACKFILL_DONE] = done }
    }

    /**
     * True once `BuiltInMedicationReconciler.reconcileAfterSyncIfNeeded`
     * has deduped any cross-device cloud-pulled medications with the
     * locally-migrated ones.
     */
    suspend fun isReconciliationDone(): Boolean =
        context.medicationMigrationDataStore.data.first()[RECONCILIATION_DONE] ?: false

    suspend fun setReconciliationDone(done: Boolean) {
        context.medicationMigrationDataStore.edit { it[RECONCILIATION_DONE] = done }
    }

    /**
     * True once every post-migration `medication` + `medication_dose` row
     * has been pushed to Firestore as a fresh cloud document.
     */
    suspend fun isMigrationPushedToCloud(): Boolean =
        context.medicationMigrationDataStore.data.first()[MIGRATION_PUSHED_TO_CLOUD] ?: false

    suspend fun setMigrationPushedToCloud(done: Boolean) {
        context.medicationMigrationDataStore.edit { it[MIGRATION_PUSHED_TO_CLOUD] = done }
    }

    /**
     * Reserved for the future Phase 2 cleanup migration (v54 → v55) that
     * drops the quarantine staging tables + source rows after the 2-week
     * convergence window.
     */
    suspend fun isSourceDataPurgedPhase2(): Boolean =
        context.medicationMigrationDataStore.data.first()[SOURCE_DATA_PURGED_PHASE_2] ?: false

    suspend fun setSourceDataPurgedPhase2(done: Boolean) {
        context.medicationMigrationDataStore.edit { it[SOURCE_DATA_PURGED_PHASE_2] = done }
    }
}
