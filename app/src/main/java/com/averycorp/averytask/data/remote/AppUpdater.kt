package com.averycorp.averytask.data.remote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.averycorp.averytask.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
        private const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/$REPO/releases/latest"
        private const val APK_ASSET_NAME = "averyTask-debug.apk"

        /**
         * Parses a release tag like "v0.7.13" or "v0.7.13-build.2" into a
         * comparable (major, minor, patch) triple. The "-build.N" suffix is
         * intentionally ignored — it is only set by CI when the same version
         * is re-pushed, and the running app's BuildConfig.VERSION_NAME never
         * carries that suffix, so including it would produce permanent false
         * positives. Returns null if the tag cannot be parsed.
         */
        internal fun parseVersion(raw: String?): IntArray? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            val base = trimmed.substringBefore("-")
            val parts = base.split(".")
            if (parts.isEmpty() || parts[0].isBlank()) return null
            return try {
                val major = parts.getOrNull(0)?.toInt() ?: 0
                val minor = parts.getOrNull(1)?.toInt() ?: 0
                val patch = parts.getOrNull(2)?.toInt() ?: 0
                intArrayOf(major, minor, patch)
            } catch (_: NumberFormatException) {
                null
            }
        }

        /**
         * Returns true if [remote] represents a strictly newer version than
         * [installed]. Falls back to false if either cannot be parsed so we
         * don't falsely prompt users to "update" when the server is broken.
         */
        internal fun isRemoteNewer(remote: String?, installed: String?): Boolean {
            val r = parseVersion(remote) ?: return false
            val i = parseVersion(installed) ?: return false
            for (idx in 0 until maxOf(r.size, i.size)) {
                val rv = r.getOrNull(idx) ?: 0
                val iv = i.getOrNull(idx) ?: 0
                if (rv != iv) return rv > iv
            }
            return false
        }
    }

    private val _status = MutableStateFlow(UpdateStatus.IDLE)
    val status: StateFlow<UpdateStatus> = _status

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _latestReleaseTag = MutableStateFlow<String?>(null)
    val latestReleaseTag: StateFlow<String?> = _latestReleaseTag

    private var latestApkDownloadUrl: String? = null

    suspend fun checkForUpdate() {
        _status.value = UpdateStatus.CHECKING
        _errorMessage.value = null
        try {
            withContext(Dispatchers.IO) {
                val release = fetchLatestReleaseJson()
                    ?: throw Exception("No releases found for $REPO")

                val tag = release.optString("tag_name", "").takeIf { it.isNotBlank() }
                    ?: throw Exception("Latest release has no tag")
                _latestReleaseTag.value = tag

                // Locate the debug APK asset on the release
                val assets = release.optJSONArray("assets") ?: JSONArray()
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.equals(APK_ASSET_NAME, ignoreCase = true) ||
                        (name.endsWith(".apk", ignoreCase = true) && apkUrl == null)
                    ) {
                        apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                        if (name.equals(APK_ASSET_NAME, ignoreCase = true)) break
                    }
                }
                latestApkDownloadUrl = apkUrl

                val installed = BuildConfig.VERSION_NAME
                _status.value = if (isRemoteNewer(tag, installed)) {
                    UpdateStatus.UPDATE_AVAILABLE
                } else {
                    UpdateStatus.NO_UPDATE
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Update check failed", e)
            _errorMessage.value = e.message ?: "Update check failed"
            _status.value = UpdateStatus.ERROR
        }
    }

    suspend fun downloadAndInstall() {
        _status.value = UpdateStatus.DOWNLOADING
        _errorMessage.value = null
        try {
            val apkFile = withContext(Dispatchers.IO) {
                val downloadUrl = latestApkDownloadUrl
                    ?: throw Exception("No APK download URL — check for update first")

                // Clean up old APKs in cache
                val updateDir = File(context.cacheDir, "apk_updates")
                if (updateDir.exists()) {
                    updateDir.listFiles()?.forEach { it.delete() }
                }
                updateDir.mkdirs()

                val targetFile = File(updateDir, "PrismTask-update.apk")

                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "PrismTask-Android")
                conn.setRequestProperty("Accept", "application/octet-stream")
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

                verifyApkSignature(targetFile)

                targetFile
            }

            _status.value = UpdateStatus.READY_TO_INSTALL
            installApk(apkFile)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Download/install failed", e)
            _errorMessage.value = e.message ?: "Download failed"
            _status.value = UpdateStatus.ERROR
        }
    }

    private fun verifyApkSignature(apkFile: File) {
        val installedSigs = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.apkContentsSigners
            ?: throw Exception("Cannot read installed app signatures")

        val apkInfo: PackageInfo = context.packageManager
            .getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw Exception("Downloaded APK is not a valid Android package")

        val apkSigs = apkInfo.signingInfo?.apkContentsSigners
            ?: throw Exception("Downloaded APK has no signing info")

        val installedDigests = installedSigs.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()

        val apkDigests = apkSigs.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()

        if (installedDigests.intersect(apkDigests).isEmpty()) {
            apkFile.delete()
            throw Exception(
                "APK signature does not match installed app. This means the " +
                    "installed build was signed with a different key than the " +
                    "release. Uninstall PrismTask and reinstall from the latest " +
                    "GitHub release to continue receiving in-app updates."
            )
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

    suspend fun fetchLatestReleaseTag() {
        try {
            withContext(Dispatchers.IO) {
                val release = fetchLatestReleaseJson() ?: return@withContext
                val tag = release.optString("tag_name", "").takeIf { it.isNotBlank() }
                _latestReleaseTag.value = tag
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Latest release fetch failed", e)
            // Leave _latestReleaseTag as-is; About section will fall back gracefully.
        }
    }

    private fun fetchLatestReleaseJson(): JSONObject? {
        val url = URL(LATEST_RELEASE_API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "PrismTask-Android")
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.useCaches = false
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        val code = conn.responseCode
        if (code != 200) {
            conn.disconnect()
            throw Exception("GitHub API returned $code")
        }

        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return JSONObject(body)
    }

    fun resetStatus() {
        _status.value = UpdateStatus.IDLE
        _errorMessage.value = null
    }
}
