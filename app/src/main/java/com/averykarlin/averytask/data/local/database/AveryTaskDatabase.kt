package com.averykarlin.averytask.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity

@Database(
    entities = [TaskEntity::class, ProjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AveryTaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
}
