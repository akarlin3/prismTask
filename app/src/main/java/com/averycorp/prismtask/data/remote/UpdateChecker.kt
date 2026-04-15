package com.averycorp.prismtask.data.remote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import com.averycorp.prismtask.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class VersionInfo(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("release_notes") val releaseNotes: String?,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("apk_size_bytes") val apkSizeBytes: Long?,
    @SerializedName("sha256") val sha256: String?,
    @SerializedName("is_mandatory") val isMandatory: Boolean
)

class UpdateChecker(
    private val context: Context,
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkForUpdate(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request
                .Builder()
                .url("$baseUrl/api/v1/app/version")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val info = gson.fromJson(response.body?.string(), VersionInfo::class.java)
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()

            if (info.versionCode > currentVersionCode) info else null
        } catch (_: Exception) {
            null
        }
    }

    fun downloadAndInstall(versionInfo: VersionInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return
        val uri = Uri.parse("$baseUrl${versionInfo.apkUrl}")

        val request = DownloadManager.Request(uri).apply {
            setTitle("PrismTask Update ${versionInfo.versionName}")
            setDescription("Downloading update...")
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "PrismTask-${versionInfo.versionName}.apk"
            )
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
        }

        downloadManager.enqueue(request)
    }
}
