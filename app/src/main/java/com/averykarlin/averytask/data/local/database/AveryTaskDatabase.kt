package com.averykarlin.averytask.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.averykarlin.averytask.data.local.dao.AttachmentDao
import com.averykarlin.averytask.data.local.dao.CalendarSyncDao
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.SyncMetadataDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.dao.UsageLogDao
import com.averykarlin.averytask.data.local.entity.AttachmentEntity
import com.averykarlin.averytask.data.local.entity.CalendarSyncEntity
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.SyncMetadataEntity
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.local.entity.TaskTagCrossRef
import com.averykarlin.averytask.data.local.entity.UsageLogEntity

@Database(
    entities = [TaskEntity::class, ProjectEntity::class, TagEntity::class, TaskTagCrossRef::class, AttachmentEntity::class, UsageLogEntity::class, SyncMetadataEntity::class, CalendarSyncEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AveryTaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun usageLogDao(): UsageLogDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun calendarSyncDao(): CalendarSyncDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` TEXT NOT NULL DEFAULT '#6B7280',
                        `created_at` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `task_tags` (
                        `taskId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`, `tagId`),
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_taskId` ON `task_tags` (`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `file_name` TEXT,
                        `thumbnail_uri` TEXT,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_taskId` ON `attachments` (`taskId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN planned_date INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `usage_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `event_type` TEXT NOT NULL,
                        `entity_id` INTEGER,
                        `entity_name` TEXT,
                        `task_title` TEXT NOT NULL,
                        `title_keywords` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimated_duration INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN scheduled_start_time INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_metadata` (
                        `local_id` INTEGER NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `cloud_id` TEXT NOT NULL DEFAULT '',
                        `last_synced_at` INTEGER NOT NULL DEFAULT 0,
                        `sync_version` INTEGER NOT NULL DEFAULT 0,
                        `pending_action` TEXT,
                        `retry_count` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`local_id`, `entity_type`)
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_metadata_pending_action` ON `sync_metadata` (`pending_action`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `calendar_sync` (
                        `task_id` INTEGER NOT NULL PRIMARY KEY,
                        `calendar_event_id` TEXT NOT NULL,
                        `last_synced_at` INTEGER NOT NULL,
                        `last_synced_version` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON DELETE CASCADE
                    )"""
                )
            }
        }
    }
}
