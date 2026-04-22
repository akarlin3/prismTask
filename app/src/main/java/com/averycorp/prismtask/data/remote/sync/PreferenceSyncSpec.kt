package com.averycorp.prismtask.data.remote.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Describes one DataStore file that should be synced to Firestore by
 * [com.averycorp.prismtask.data.remote.GenericPreferenceSyncService].
 *
 * @property firestoreDocName Document name under `/users/{uid}/prefs/{docName}`.
 *   Must be stable across releases — renaming it orphans already-synced state.
 * @property dataStore The DataStore instance backing the preference file.
 * @property excludeKeys Key names that must NOT be pushed to Firestore, e.g.
 *   device-local watermarks or PII that should stay on-device. Meta keys
 *   starting with `__pref_` are always excluded regardless.
 */
data class PreferenceSyncSpec(
    val firestoreDocName: String,
    val dataStore: DataStore<Preferences>,
    val excludeKeys: Set<String> = emptySet()
)
