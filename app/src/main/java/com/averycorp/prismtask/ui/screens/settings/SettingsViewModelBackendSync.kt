package com.averycorp.prismtask.ui.screens.settings

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.remote.api.ImportResponse
import com.averycorp.prismtask.data.remote.api.LoginRequest
import com.averycorp.prismtask.data.remote.api.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

/**
 * Backend sync / auth / cloud export+import action handlers extracted from
 * [SettingsViewModel].
 */
internal fun SettingsViewModel.onBackendSync() {
    viewModelScope.launch {
        if (_isBackendSyncing.value) return@launch
        _isBackendSyncing.value = true
        try {
            val result = backendSyncService.fullSync()
            result.fold(
                onSuccess = { summary ->
                    _messages.emit(
                        "Backend sync complete — pushed ${summary.pushed}, pulled ${summary.pulled}"
                    )
                },
                onFailure = { e ->
                    Log.e("SettingsVM", "Backend sync failed", e)
                    _messages.emit("Backend sync failed: ${e.message ?: "unknown error"}")
                }
            )
        } finally {
            _isBackendSyncing.value = false
        }
    }
}

internal fun SettingsViewModel.onBackendLogin(
    email: String,
    password: String,
    onComplete: (Boolean) -> Unit
) {
    viewModelScope.launch {
        if (_isBackendAuthenticating.value) return@launch
        _isBackendAuthenticating.value = true
        try {
            val tokens = prismTaskApi.login(LoginRequest(email = email, password = password))
            authTokenPreferences.saveTokens(tokens.accessToken, tokens.refreshToken)
            _messages.emit("Connected to backend")
            onComplete(true)
        } catch (e: Exception) {
            Log.e("SettingsVM", "Backend login failed", e)
            _messages.emit("Login failed: ${e.message ?: "unknown error"}")
            onComplete(false)
        } finally {
            _isBackendAuthenticating.value = false
        }
    }
}

internal fun SettingsViewModel.onBackendRegister(
    email: String,
    password: String,
    name: String,
    onComplete: (Boolean) -> Unit
) {
    viewModelScope.launch {
        if (_isBackendAuthenticating.value) return@launch
        _isBackendAuthenticating.value = true
        try {
            val tokens = prismTaskApi.register(
                RegisterRequest(email = email, password = password, name = name)
            )
            authTokenPreferences.saveTokens(tokens.accessToken, tokens.refreshToken)
            _messages.emit("Backend account created")
            onComplete(true)
        } catch (e: Exception) {
            Log.e("SettingsVM", "Backend register failed", e)
            _messages.emit("Registration failed: ${e.message ?: "unknown error"}")
            onComplete(false)
        } finally {
            _isBackendAuthenticating.value = false
        }
    }
}

internal fun SettingsViewModel.onBackendDisconnect() {
    viewModelScope.launch {
        authTokenPreferences.clearTokens()
        backendSyncPreferences.clear()
        templatePreferences.setFirstSyncDone(false)
        _messages.emit("Disconnected from backend")
    }
}

internal fun SettingsViewModel.onExportToCloud() {
    viewModelScope.launch {
        if (_isCloudExporting.value) return@launch
        _isCloudExporting.value = true
        try {
            val responseBody = withContext(Dispatchers.IO) { prismTaskApi.exportJson() }
            val bytes = withContext(Dispatchers.IO) { responseBody.bytes() }
            val timestamp = java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                java.util.Locale.US
            ).format(java.util.Date())
            val filename = "prismtask_cloud_$timestamp.json"
            val savedName = withContext(Dispatchers.IO) {
                saveBytesToDownloads(filename, bytes)
            }
            _messages.emit("Saved $savedName to Downloads")
        } catch (e: Exception) {
            Log.e("SettingsVM", "Cloud export failed", e)
            _messages.emit("Cloud export failed: ${e.message ?: "unknown error"}")
        } finally {
            _isCloudExporting.value = false
        }
    }
}

internal fun SettingsViewModel.onImportFromCloud(jsonBytes: ByteArray, filename: String) {
    viewModelScope.launch {
        if (_isCloudImporting.value) return@launch
        _isCloudImporting.value = true
        try {
            val mediaType = "application/json".toMediaType()
            val part = MultipartBody.Part.createFormData(
                name = "file",
                filename = filename,
                body = jsonBytes.toRequestBody(mediaType)
            )
            val result: ImportResponse = withContext(Dispatchers.IO) {
                prismTaskApi.importJson(part, mode = "merge")
            }
            val parts = mutableListOf<String>()
            if (result.tasksImported > 0) parts.add("${result.tasksImported} tasks")
            if (result.projectsImported > 0) parts.add("${result.projectsImported} projects")
            if (result.tagsImported > 0) parts.add("${result.tagsImported} tags")
            if (result.habitsImported > 0) parts.add("${result.habitsImported} habits")
            val summary = if (parts.isEmpty()) "Nothing imported" else "Imported ${parts.joinToString(", ")}"
            _messages.emit(summary)
        } catch (e: Exception) {
            Log.e("SettingsVM", "Cloud import failed", e)
            _messages.emit("Cloud import failed: ${e.message ?: "unknown error"}")
        } finally {
            _isCloudImporting.value = false
        }
    }
}

private fun SettingsViewModel.saveBytesToDownloads(filename: String, bytes: ByteArray): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create Downloads entry")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Unable to open output stream")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return filename
    } else {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("External storage unavailable")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }
}
