package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "leisure_logs",
    indices = [Index("date", unique = true)]
)
data class LeisureLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "date")
    val date: Long,
    @ColumnInfo(name = "music_pick")
    val musicPick: String? = null,
    @ColumnInfo(name = "music_done")
    val musicDone: Boolean = false,
    @ColumnInfo(name = "flex_pick")
    val flexPick: String? = null,
    @ColumnInfo(name = "flex_done")
    val flexDone: Boolean = false,
    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
