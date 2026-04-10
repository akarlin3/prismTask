package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_status_prefs")

@Singleton
class ProStatusPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val IS_PRO_CACHED_KEY = booleanPreferencesKey("is_pro_user")
        private val PRO_EXPIRES_AT_KEY = longPreferencesKey("pro_expires_at")
        private val LAST_VERIFIED_AT_KEY = longPreferencesKey("last_verified_at")
    }

    suspend fun isProCached(): Boolean = context.dataStore.data.map { prefs ->
        prefs[IS_PRO_CACHED_KEY] ?: false
    }.first()

    suspend fun proExpiresAt(): Long = context.dataStore.data.map { prefs ->
        prefs[PRO_EXPIRES_AT_KEY] ?: 0L
    }.first()

    suspend fun lastVerifiedAt(): Long = context.dataStore.data.map { prefs ->
        prefs[LAST_VERIFIED_AT_KEY] ?: 0L
    }.first()

    suspend fun setProCached(isPro: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_PRO_CACHED_KEY] = isPro }
    }

    suspend fun setProExpiresAt(expiresAt: Long) {
        context.dataStore.edit { prefs -> prefs[PRO_EXPIRES_AT_KEY] = expiresAt }
    }

    suspend fun setLastVerifiedAt(verifiedAt: Long) {
        context.dataStore.edit { prefs -> prefs[LAST_VERIFIED_AT_KEY] = verifiedAt }
    }
}
