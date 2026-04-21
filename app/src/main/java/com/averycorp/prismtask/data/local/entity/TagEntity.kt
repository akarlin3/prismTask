package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["cloud_id"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "cloud_id") val cloudId: String? = null,
    val name: String,
    val color: String = "#6B7280",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
