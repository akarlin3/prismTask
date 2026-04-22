package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-definable text shortcut that expands in the quick-add bar before
 * any other NLP parsing happens. Added in v1.3.0 (P7).
 */
@Entity(
    tableName = "nlp_shortcuts",
    indices = [
        Index(value = ["trigger"], unique = true),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class NlpShortcutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trigger: String,
    val expansion: String,
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    /** Firestore document ID; NULL until first sync push assigns one. */
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    /** Last-write-wins timestamp in ms; bumped on every user-visible write. */
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
