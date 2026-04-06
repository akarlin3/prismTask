package com.averykarlin.averytask.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE,
    NO_UPDATE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AppUpdater"
        private const val REPO = "akarlin3/averytask"
        private const val APK_PATH = "apk/averytask-debug.apk"
        private const val COMMITS_API_URL =
            "https://api.github.com/repos/$REPO/commits?path=$APK_PATH&sha=main&per_page=1"
        private const val RAW_DOWNLOAD_URL =
            "https://raw.githubusercontent.com/$REPO/main/$APK_PATH"
    }

    private val _status = MutableStateFlow(UpdateStatus.IDLE)
    val status: StateFlow<UpdateStatus> = _status

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _latestCommitDate = MutableStateFlow<String?>(null)
    val latestCommitDate: StateFlow<String?> = _latestCommitDate

    private var latestSha: String? = null

    suspend fun checkForUpdate() {
        _status.value = UpdateStatus.CHECKING
        _errorMessage.value = null
        try {
            withContext(Dispatchers.IO) {
                val url = URL(COMMITS_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("Cache-Control", "no-cache")
                conn.useCaches = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                if (conn.responseCode != 200) {
                    throw Exception("GitHub API returned ${conn.responseCode}")
                }

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val commits = JSONArray(body)
                if (commits.length() == 0) {
                    throw Exception("No commits found for APK file")
                }

                val latestCommit = commits.getJSONObject(0)
                latestSha = latestCommit.getString("sha")
                val commitDateStr = latestCommit.getJSONObject("commit")
                    .getJSONObject("committer")
                    .getString("date")
                _latestCommitDate.value = commitDateStr

                // Compare repo APK commit time against app install/update time
                val commitTime = Instant.parse(commitDateStr)
                val packageInfo = context.packageManager
                    .getPackageInfo(context.packageName, 0)
                val appUpdateTime = Instant.ofEpochMilli(packageInfo.lastUpdateTime)

                if (commitTime.isAfter(appUpdateTime)) {
                    _status.value = UpdateStatus.UPDATE_AVAILABLE
                } else {
                    _status.value = UpdateStatus.NO_UPDATE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            _errorMessage.value = e.message ?: "Update check failed"
            _status.value = UpdateStatus.ERROR
        }
    }

    suspend fun downloadAndInstall() {
        _status.value = UpdateStatus.DOWNLOADING
        _errorMessage.value = null
        try {
            val apkFile = withContext(Dispatchers.IO) {
                // Clean up old APKs in cache
                val updateDir = File(context.cacheDir, "apk_updates")
                if (updateDir.exists()) {
                    updateDir.listFiles()?.forEach { it.delete() }
                }
                updateDir.mkdirs()

                val targetFile = File(updateDir, "AveryTask-update.apk")

                val url = URL(RAW_DOWNLOAD_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    conn.disconnect()
                    throw Exception("Download failed: HTTP $responseCode")
                }

                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                conn.disconnect()

                if (!targetFile.exists() || targetFile.length() < 1000) {
                    throw Exception("Downloaded file is invalid (${targetFile.length()} bytes)")
                }

                targetFile
            }

            _status.value = UpdateStatus.READY_TO_INSTALL
            installApk(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download/install failed", e)
            _errorMessage.value = e.message ?: "Download failed"
            _status.value = UpdateStatus.ERROR
        }
    }

    private fun installApk(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun resetStatus() {
        _status.value = UpdateStatus.IDLE
        _errorMessage.value = null
    }
}
