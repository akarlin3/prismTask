package com.averykarlin.averytask.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.averykarlin.averytask.data.local.dao.AttachmentDao
import com.averykarlin.averytask.data.local.dao.CalendarSyncDao
import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.dao.LeisureDao
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.SchoolworkDao
import com.averykarlin.averytask.data.local.dao.SelfCareDao
import com.averykarlin.averytask.data.local.dao.SyncMetadataDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.dao.UsageLogDao
import com.averykarlin.averytask.data.local.entity.AttachmentEntity
import com.averykarlin.averytask.data.local.entity.CalendarSyncEntity
import com.averykarlin.averytask.data.local.entity.CourseCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitEntity
import com.averykarlin.averytask.data.local.entity.AssignmentEntity
import com.averykarlin.averytask.data.local.entity.CourseEntity
import com.averykarlin.averytask.data.local.entity.LeisureLogEntity
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.SelfCareLogEntity
import com.averykarlin.averytask.data.local.entity.SelfCareStepEntity
import com.averykarlin.averytask.data.local.entity.StudyLogEntity
import com.averykarlin.averytask.data.local.entity.SyncMetadataEntity
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.local.entity.TaskTagCrossRef
import com.averykarlin.averytask.data.local.entity.UsageLogEntity

@Database(
    entities = [TaskEntity::class, ProjectEntity::class, TagEntity::class, TaskTagCrossRef::class, AttachmentEntity::class, UsageLogEntity::class, SyncMetadataEntity::class, CalendarSyncEntity::class, HabitEntity::class, HabitCompletionEntity::class, LeisureLogEntity::class, CourseEntity::class, AssignmentEntity::class, StudyLogEntity::class, CourseCompletionEntity::class, SelfCareLogEntity::class, SelfCareStepEntity::class],
    version = 18,
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
    abstract fun habitDao(): HabitDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun leisureDao(): LeisureDao
    abstract fun schoolworkDao(): SchoolworkDao
    abstract fun selfCareDao(): SelfCareDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `habits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `target_frequency` INTEGER NOT NULL DEFAULT 1,
                        `frequency_period` TEXT NOT NULL DEFAULT 'daily',
                        `active_days` TEXT,
                        `color` TEXT NOT NULL DEFAULT '#4A90D9',
                        `icon` TEXT NOT NULL DEFAULT '⭐',
                        `reminder_time` INTEGER,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `is_archived` INTEGER NOT NULL DEFAULT 0,
                        `create_daily_task` INTEGER NOT NULL DEFAULT 0,
                        `category` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `habit_completions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `habit_id` INTEGER NOT NULL,
                        `completed_date` INTEGER NOT NULL,
                        `completed_at` INTEGER NOT NULL,
                        `notes` TEXT,
                        FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_completions_habit_id` ON `habit_completions` (`habit_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_completions_completed_date` ON `habit_completions` (`completed_date`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `leisure_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `music_pick` TEXT,
                        `music_done` INTEGER NOT NULL DEFAULT 0,
                        `flex_pick` TEXT,
                        `flex_done` INTEGER NOT NULL DEFAULT 0,
                        `started_at` INTEGER,
                        `created_at` INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_leisure_logs_date` ON `leisure_logs` (`date`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `courses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `color` INTEGER NOT NULL DEFAULT 0,
                        `icon` TEXT NOT NULL DEFAULT '📚',
                        `active` INTEGER NOT NULL DEFAULT 1,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `assignments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `course_id` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `due_date` INTEGER,
                        `completed` INTEGER NOT NULL DEFAULT 0,
                        `completed_at` INTEGER,
                        `notes` TEXT,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`course_id`) REFERENCES `courses`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_course_id` ON `assignments` (`course_id`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `study_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `course_pick` INTEGER,
                        `study_done` INTEGER NOT NULL DEFAULT 0,
                        `assignment_pick` INTEGER,
                        `assignment_done` INTEGER NOT NULL DEFAULT 0,
                        `started_at` INTEGER,
                        `created_at` INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_study_logs_date` ON `study_logs` (`date`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `course_completions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `course_id` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL DEFAULT 0,
                        `completed_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`course_id`) REFERENCES `courses`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_course_completions_date_course_id` ON `course_completions` (`date`, `course_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_completions_course_id` ON `course_completions` (`course_id`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `self_care_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `routine_type` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `selected_tier` TEXT NOT NULL DEFAULT 'solid',
                        `completed_steps` TEXT NOT NULL DEFAULT '[]',
                        `is_complete` INTEGER NOT NULL DEFAULT 0,
                        `started_at` INTEGER,
                        `created_at` INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_self_care_logs_routine_type_date` ON `self_care_logs` (`routine_type`, `date`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `self_care_steps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `step_id` TEXT NOT NULL,
                        `routine_type` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `duration` TEXT NOT NULL,
                        `tier` TEXT NOT NULL,
                        `note` TEXT NOT NULL DEFAULT '',
                        `phase` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN reminder_interval_millis INTEGER")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN source_habit_id INTEGER")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN has_logging INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Removed create_daily_task from habits and source_habit_id from tasks.
                // Columns remain in DB but are no longer mapped by Room entities.
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN reminder_times_per_day INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE self_care_steps ADD COLUMN reminder_delay_millis INTEGER")
            }
        }
    }
}
