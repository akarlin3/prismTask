package com.averykarlin.averytask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "api_prefs")

@Singleton
class ApiPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
    }

    fun getClaudeApiKey(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CLAUDE_API_KEY] ?: ""
    }

    suspend fun setClaudeApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[CLAUDE_API_KEY] = key
        }
    }

    suspend fun clearClaudeApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(CLAUDE_API_KEY)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
