package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-uploaded notification sound.
 *
 * Files are copied into the app's sandbox (`context.filesDir/sounds/…`) at
 * import time; [uri] points at that local file. We keep [originalFilename]
 * for display and [durationMs] to enforce the 30-second cap up front.
 *
 * Upload validation (format, size, duration) is performed by the importer
 * before a row lands here — this entity stores already-validated content.
 */
@Entity(
    tableName = "custom_sounds",
    indices = [Index(value = ["name"])]
)
data class CustomSoundEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "original_filename")
    val originalFilename: String,

    /** Local file URI under the app sandbox (e.g., file:///data/user/0/.../sounds/xyz.mp3). */
    @ColumnInfo(name = "uri")
    val uri: String,

    /** One of mp3 / wav / m4a / ogg — validated at import time. */
    @ColumnInfo(name = "format")
    val format: String,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    fun soundId(): String = "custom_$id"

    companion object {
        const val CUSTOM_SOUND_PREFIX = "custom_"
        const val MAX_SIZE_BYTES = 10L * 1024L * 1024L // 10 MB
        const val MAX_DURATION_MS = 30_000L // 30 seconds
        val SUPPORTED_FORMATS = setOf("mp3", "wav", "m4a", "ogg")

        fun parseId(soundId: String): Long? =
            if (soundId.startsWith(CUSTOM_SOUND_PREFIX)) {
                soundId.removePrefix(CUSTOM_SOUND_PREFIX).toLongOrNull()
            } else {
                null
            }
    }
}
