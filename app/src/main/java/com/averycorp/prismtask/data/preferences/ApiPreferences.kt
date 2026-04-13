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
        private const val FALLBACK_PREFS_NAME = "api_prefs_fallback"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
    }

    /**
     * EncryptedSharedPreferences backed by the AndroidKeyStore. Falls back to
     * a plain SharedPreferences if the KeyStore is corrupted (a known issue
     * with security-crypto 1.1.0-alpha06 on certain devices/OS versions).
     */
    private val encryptedPrefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.e("ApiPreferences", "EncryptedSharedPreferences failed, using fallback", e)
        context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

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
