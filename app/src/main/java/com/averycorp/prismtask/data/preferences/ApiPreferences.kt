package com.averycorp.prismtask.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "encrypted_api_prefs"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _claudeApiKeyFlow = MutableStateFlow(
        encryptedPrefs.getString(KEY_CLAUDE_API_KEY, "") ?: ""
    )

    fun getClaudeApiKey(): Flow<String> = _claudeApiKeyFlow

    suspend fun setClaudeApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_CLAUDE_API_KEY, key).apply()
        _claudeApiKeyFlow.value = key
    }

    suspend fun clearClaudeApiKey() {
        encryptedPrefs.edit().remove(KEY_CLAUDE_API_KEY).apply()
        _claudeApiKeyFlow.value = ""
    }

    suspend fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        _claudeApiKeyFlow.value = ""
    }
}
