package com.averykarlin.averytask.data.remote

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
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
            "https://github.com/$REPO/raw/main/$APK_PATH"
        private const val PREFS_NAME = "app_updater"
        private const val KEY_LAST_COMMIT_SHA = "last_commit_sha"
    }

    private val _status = MutableStateFlow(UpdateStatus.IDLE)
    val status: StateFlow<UpdateStatus> = _status

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _latestCommitDate = MutableStateFlow<String?>(null)
    val latestCommitDate: StateFlow<String?> = _latestCommitDate

    private var downloadId: Long = -1
    private var latestSha: String? = null

    private fun getSavedCommitSha(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_COMMIT_SHA, null)
    }

    private fun saveCommitSha(sha: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_COMMIT_SHA, sha).apply()
    }

    suspend fun checkForUpdate() {
        _status.value = UpdateStatus.CHECKING
        _errorMessage.value = null
        try {
            withContext(Dispatchers.IO) {
                val url = URL(COMMITS_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
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
                val commitDate = latestCommit.getJSONObject("commit")
                    .getJSONObject("committer")
                    .getString("date")
                _latestCommitDate.value = commitDate

                val savedSha = getSavedCommitSha()
                if (savedSha == null || savedSha != latestSha) {
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

    fun downloadAndInstall() {
        _status.value = UpdateStatus.DOWNLOADING

        // Clean up old APKs via ContentResolver (works with scoped storage)
        try {
            val resolver = context.contentResolver
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("AveryTask-%.apk")
            resolver.delete(collection, selection, selectionArgs)
        } catch (e: Exception) {
            Log.w(TAG, "Could not clean up old APKs", e)
        }

        // Use timestamp in filename to avoid DownloadManager conflict
        val fileName = "AveryTask-${System.currentTimeMillis()}.apk"

        val request = DownloadManager.Request(Uri.parse(RAW_DOWNLOAD_URL))
            .setTitle("AveryTask Update")
            .setDescription("Downloading latest build")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setMimeType("application/vnd.android.package-archive")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            downloadId = dm.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed to start", e)
            _errorMessage.value = e.message ?: "Download failed to start"
            _status.value = UpdateStatus.ERROR
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    val uri = dm.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        _status.value = UpdateStatus.READY_TO_INSTALL
                        // Save the sha so we don't prompt again for this version
                        latestSha?.let { saveCommitSha(it) }
                        installApk(uri)
                    } else {
                        _errorMessage.value = "Download failed"
                        _status.value = UpdateStatus.ERROR
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApk(uri: Uri) {
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
