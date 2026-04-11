package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named TaskFilter preset. The filter configuration is stored as a JSON
 * string (serialized via Gson) so future TaskFilter fields automatically
 * round-trip without a schema change.
 *
 * Added in v1.3.0 (P8).
 */
@Entity(tableName = "saved_filters")
data class SavedFilterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "filter_json")
    val filterJson: String,

    @ColumnInfo(name = "icon_emoji")
    val iconEmoji: String? = null,

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
