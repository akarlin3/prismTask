package com.averycorp.prismtask.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.averycorp.prismtask.data.local.dao.CustomSoundDao
import com.averycorp.prismtask.data.local.entity.CustomSoundEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports, stores, and removes user-uploaded notification sounds.
 *
 * Imports are validated up-front: format must be in
 * [CustomSoundEntity.SUPPORTED_FORMATS], file size <=
 * [CustomSoundEntity.MAX_SIZE_BYTES], duration <=
 * [CustomSoundEntity.MAX_DURATION_MS]. Failed imports return
 * [ImportResult.Error] without writing to disk.
 *
 * On success the bytes are copied into
 * `filesDir/notification_sounds/<id>.<ext>` under the app sandbox, so the
 * sound survives even if the user deletes it from their downloads folder.
 */
@Singleton
class CustomSoundRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val dao: CustomSoundDao
) {
    fun getAll(): Flow<List<CustomSoundEntity>> = dao.getAll()

    suspend fun getById(id: Long): CustomSoundEntity? = dao.getById(id)

    suspend fun delete(sound: CustomSoundEntity) {
        runCatching { File(Uri.parse(sound.uri).path ?: "").delete() }
        dao.delete(sound)
    }

    sealed class ImportResult {
        data class Success(val entity: CustomSoundEntity) : ImportResult()
        data class Error(val reason: String) : ImportResult()
    }

    suspend fun import(
        sourceUri: Uri,
        originalFilename: String,
        displayName: String
    ): ImportResult = withContext(Dispatchers.IO) {
        val format = originalFilename
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (format !in CustomSoundEntity.SUPPORTED_FORMATS) {
            return@withContext ImportResult.Error(
                "Unsupported format .$format — allowed: mp3, wav, m4a, ogg"
            )
        }

        val bytes = try {
            context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext ImportResult.Error("Could not open file")
        } catch (e: Exception) {
            return@withContext ImportResult.Error("Read failed: ${e.message}")
        }

        if (bytes.size > CustomSoundEntity.MAX_SIZE_BYTES) {
            return@withContext ImportResult.Error(
                "File is too large (${bytes.size / 1024 / 1024}MB). Max 10MB."
            )
        }

        val soundsDir = File(context.filesDir, "notification_sounds")
        if (!soundsDir.exists()) soundsDir.mkdirs()

        val targetName = "${System.currentTimeMillis()}.$format"
        val targetFile = File(soundsDir, targetName)
        try {
            FileOutputStream(targetFile).use { it.write(bytes) }
        } catch (e: Exception) {
            return@withContext ImportResult.Error("Write failed: ${e.message}")
        }

        val durationMs = extractDurationMs(targetFile) ?: -1L
        if (durationMs < 0L) {
            targetFile.delete()
            return@withContext ImportResult.Error("Could not read audio duration")
        }
        if (durationMs > CustomSoundEntity.MAX_DURATION_MS) {
            targetFile.delete()
            return@withContext ImportResult.Error(
                "Audio too long (${durationMs / 1000}s). Max 30 seconds."
            )
        }

        val entity = CustomSoundEntity(
            name = displayName.ifBlank { originalFilename },
            originalFilename = originalFilename,
            uri = Uri.fromFile(targetFile).toString(),
            format = format,
            sizeBytes = bytes.size.toLong(),
            durationMs = durationMs,
            createdAt = System.currentTimeMillis()
        )
        val id = dao.insert(entity)
        ImportResult.Success(entity.copy(id = id))
    }

    private fun extractDurationMs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
