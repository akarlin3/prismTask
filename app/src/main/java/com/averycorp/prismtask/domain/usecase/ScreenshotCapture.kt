package com.averycorp.prismtask.domain.usecase

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class ScreenshotCapture @Inject constructor() {

    suspend fun capture(activity: Activity): Uri? {
        val window = activity.window ?: return null

        // Delay slightly to let screen settle during transitions
        kotlinx.coroutines.delay(300)

        val bitmap = captureWindow(window) ?: return null

        return saveBitmapToCache(activity, bitmap)
    }

    private suspend fun captureWindow(window: Window): Bitmap? = suspendCoroutine { cont ->
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    cont.resume(bitmap)
                } else {
                    cont.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            cont.resume(null)
        }
    }

    private fun saveBitmapToCache(activity: Activity, bitmap: Bitmap): Uri? {
        return try {
            val cacheDir = File(activity.cacheDir, "screenshots")
            cacheDir.mkdirs()
            val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        }
    }

    fun cleanupOldScreenshots(activity: Activity) {
        try {
            val cacheDir = File(activity.cacheDir, "screenshots")
            if (!cacheDir.exists()) return
            val cutoff = System.currentTimeMillis() - 60 * 60 * 1000 // 1 hour
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Best effort cleanup
        }
    }
}
