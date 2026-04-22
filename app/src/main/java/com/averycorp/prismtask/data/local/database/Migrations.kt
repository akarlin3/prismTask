package com.averycorp.prismtask.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations for [PrismTaskDatabase].
 *
 * Previously these lived inside `PrismTaskDatabase.companion object`, but
 * after 30+ schema bumps the database file had grown to ~580 lines. Moving
 * them here keeps PrismTaskDatabase.kt focused on entity list + DAO
 * accessors while this file is the one-stop shop for schema evolution.
 *
 * [ALL_MIGRATIONS] is a sorted array that can be spread into
 * `Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS)`.
 */

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
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_course_completions_date_course_id` ON `course_completions` (`date`, `course_id`)"
        )
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
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_self_care_logs_routine_type_date` ON `self_care_logs` (`routine_type`, `date`)"
        )
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

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE self_care_steps ADD COLUMN time_of_day TEXT NOT NULL DEFAULT 'morning'")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE self_care_logs ADD COLUMN tiers_by_time TEXT NOT NULL DEFAULT '{}'")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN track_booking INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN track_previous_period INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
        // Seed sort_order for existing subtasks based on created_at so current
        // ordering is preserved.
        db.execSQL(
            """
            UPDATE tasks
            SET sort_order = (
                SELECT COUNT(*)
                FROM tasks AS t2
                WHERE t2.parent_task_id = tasks.parent_task_id
                  AND t2.created_at < tasks.created_at
            )
            WHERE parent_task_id IS NOT NULL
            """.trimIndent()
        )
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Seed sort_order for root tasks so they have a natural initial
        // order when a user first switches to Custom sort. Subtasks were
        // already seeded in MIGRATION_20_21 and are left alone here.
        db.execSQL(
            """
            UPDATE tasks
            SET sort_order = id
            WHERE parent_task_id IS NULL
            """.trimIndent()
        )
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN eisenhower_quadrant TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN eisenhower_updated_at INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN eisenhower_reason TEXT")
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN is_bookable INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN is_booked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN booked_date INTEGER")
        db.execSQL("ALTER TABLE habits ADD COLUMN booked_note TEXT")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `habit_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `habit_id` INTEGER NOT NULL,
                `date` INTEGER NOT NULL,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_habit_id` ON `habit_logs` (`habit_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_date` ON `habit_logs` (`date`)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN show_streak INTEGER NOT NULL DEFAULT 0")
    }
}

// v1.3.0 P4: add is_flagged column to tasks (default 0)
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN is_flagged INTEGER NOT NULL DEFAULT 0")
    }
}

// v1.3.0 P7: add nlp_shortcuts table
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `nlp_shortcuts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `trigger` TEXT NOT NULL,
                `expansion` TEXT NOT NULL,
                `sort_order` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_nlp_shortcuts_trigger` ON `nlp_shortcuts` (`trigger`)")
    }
}

// v1.3.0 P8: add saved_filters table
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `saved_filters` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `filter_json` TEXT NOT NULL,
                `icon_emoji` TEXT,
                `sort_order` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL
            )"""
        )
    }
}

// v1.3.0 P14: add reminder_profiles table
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `reminder_profiles` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `offsets_csv` TEXT NOT NULL,
                `escalation` INTEGER NOT NULL DEFAULT 0,
                `escalation_interval_minutes` INTEGER,
                `is_built_in` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL
            )"""
        )
    }
}

// v1.3.0 P15: add project_templates + habit_templates tables
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `project_templates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `color` TEXT,
                `icon_emoji` TEXT,
                `category` TEXT,
                `task_templates_json` TEXT NOT NULL,
                `is_built_in` INTEGER NOT NULL DEFAULT 0,
                `usage_count` INTEGER NOT NULL DEFAULT 0,
                `last_used_at` INTEGER,
                `created_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `habit_templates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `icon_emoji` TEXT,
                `color` TEXT,
                `category` TEXT,
                `frequency` TEXT NOT NULL,
                `target_count` INTEGER NOT NULL DEFAULT 1,
                `active_days_csv` TEXT NOT NULL,
                `is_built_in` INTEGER NOT NULL DEFAULT 0,
                `usage_count` INTEGER NOT NULL DEFAULT 0,
                `last_used_at` INTEGER,
                `created_at` INTEGER NOT NULL
            )"""
        )
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `task_templates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` TEXT,
                `remoteId` INTEGER,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `icon` TEXT,
                `category` TEXT,
                `template_title` TEXT,
                `template_description` TEXT,
                `template_priority` INTEGER,
                `templateProjectId` INTEGER,
                `template_tags_json` TEXT,
                `template_recurrence_json` TEXT,
                `template_duration` INTEGER,
                `template_subtasks_json` TEXT,
                `is_built_in` INTEGER NOT NULL DEFAULT 0,
                `usage_count` INTEGER NOT NULL DEFAULT 0,
                `last_used_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`templateProjectId`) REFERENCES `projects`(`id`) ON DELETE SET NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_templates_templateProjectId` ON `task_templates` (`templateProjectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_templates_userId` ON `task_templates` (`userId`)")
    }
}

// v1.4.0 V1: add life_category column to tasks (Work-Life Balance Engine)
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN life_category TEXT")
    }
}

// v1.4.0 V10 follow-up: add medication_name to self_care_steps so the
// existing medication self-care routine can link to MedicationRefillEntity
// rows by exact name match.
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE self_care_steps ADD COLUMN medication_name TEXT")
    }
}

// v1.4.0 V3/V4/V6: add boundary_rules, check_in_logs, weekly_reviews
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // V3 — boundary_rules
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `boundary_rules` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `rule_type` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `start_time` TEXT NOT NULL,
                `end_time` TEXT NOT NULL,
                `active_days_csv` TEXT NOT NULL,
                `is_enabled` INTEGER NOT NULL DEFAULT 1,
                `is_built_in` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL
            )"""
        )
        // V4 — check_in_logs
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `check_in_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` INTEGER NOT NULL,
                `steps_completed_csv` TEXT NOT NULL,
                `medications_confirmed` INTEGER NOT NULL DEFAULT 0,
                `tasks_reviewed` INTEGER NOT NULL DEFAULT 0,
                `habits_completed` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_check_in_logs_date` ON `check_in_logs` (`date`)")
        // V6 — weekly_reviews
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `weekly_reviews` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `week_start_date` INTEGER NOT NULL,
                `metrics_json` TEXT NOT NULL,
                `ai_insights_json` TEXT,
                `created_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_reviews_week_start_date` ON `weekly_reviews` (`week_start_date`)")
    }
}

// v1.4.0 V10: add medication_refills table (pill count + refill tracking)
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `medication_refills` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `medication_name` TEXT NOT NULL,
                `pill_count` INTEGER NOT NULL,
                `pills_per_dose` INTEGER NOT NULL DEFAULT 1,
                `doses_per_day` INTEGER NOT NULL DEFAULT 1,
                `last_refill_date` INTEGER,
                `pharmacy_name` TEXT,
                `pharmacy_phone` TEXT,
                `reminder_days_before` INTEGER NOT NULL DEFAULT 3,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_refills_medication_name` ON `medication_refills` (`medication_name`)"
        )
    }
}

// v1.4.0 V7: add mood_energy_logs table (mood + energy daily check-ins)
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `mood_energy_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` INTEGER NOT NULL,
                `mood` INTEGER NOT NULL,
                `energy` INTEGER NOT NULL,
                `notes` TEXT,
                `time_of_day` TEXT NOT NULL DEFAULT 'morning',
                `created_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mood_energy_logs_date` ON `mood_energy_logs` (`date`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_energy_logs_date_time_of_day` ON `mood_energy_logs` (`date`, `time_of_day`)"
        )
    }
}

// Task Analytics: task_completions table + backfill from existing completed tasks
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create the task_completions table
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `task_completions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `task_id` INTEGER,
                `project_id` INTEGER,
                `completed_date` INTEGER NOT NULL,
                `completed_at_time` INTEGER NOT NULL,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `was_overdue` INTEGER NOT NULL DEFAULT 0,
                `days_to_complete` INTEGER,
                `tags` TEXT,
                FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON DELETE SET NULL,
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON DELETE SET NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_completions_completed_date` ON `task_completions` (`completed_date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_completions_project_id` ON `task_completions` (`project_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_completions_task_id` ON `task_completions` (`task_id`)")

        // Backfill from existing completed tasks
        db.execSQL(
            """INSERT INTO `task_completions` (`task_id`, `project_id`, `completed_date`, `completed_at_time`, `priority`, `was_overdue`, `days_to_complete`, `tags`)
               SELECT
                   t.`id`,
                   t.`project_id`,
                   t.`completed_at`,
                   t.`completed_at`,
                   t.`priority`,
                   CASE WHEN t.`due_date` IS NOT NULL AND t.`due_date` < t.`completed_at` THEN 1 ELSE 0 END,
                   CASE WHEN t.`created_at` > 0 AND t.`completed_at` > t.`created_at`
                        THEN CAST((t.`completed_at` - t.`created_at`) / 86400000 AS INTEGER)
                        ELSE NULL END,
                   (SELECT GROUP_CONCAT(tg.`name`, ',') FROM `task_tags` tt INNER JOIN `tags` tg ON tt.`tagId` = tg.`id` WHERE tt.`taskId` = t.`id`)
               FROM `tasks` t
               WHERE t.`is_completed` = 1 AND t.`completed_at` IS NOT NULL"""
        )
    }
}

// Focus & Release Mode: add per-task override columns + analytics log table
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN good_enough_minutes_override INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN max_revisions_override INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN revision_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN revision_locked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN cumulative_edit_minutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `focus_release_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `event_type` TEXT NOT NULL,
                `task_id` INTEGER,
                `context` TEXT,
                `created_at` INTEGER NOT NULL
            )"""
        )
    }
}

// v1.4.0 Notifications Overhaul: expand reminder_profiles into a full
// notification-delivery profile (sound, vibration, display mode,
// lock-screen visibility, escalation, quiet hours, snooze, re-alert,
// watch sync), and introduce custom_sounds for user-uploaded audio.
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- reminder_profiles expansion ---
        val adds = listOf(
            "ALTER TABLE reminder_profiles ADD COLUMN urgency_tier TEXT NOT NULL DEFAULT 'medium'",
            "ALTER TABLE reminder_profiles ADD COLUMN sound_id TEXT NOT NULL DEFAULT '__system_default__'",
            "ALTER TABLE reminder_profiles ADD COLUMN sound_volume_percent INTEGER NOT NULL DEFAULT 70",
            "ALTER TABLE reminder_profiles ADD COLUMN sound_fade_in_ms INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE reminder_profiles ADD COLUMN sound_fade_out_ms INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE reminder_profiles ADD COLUMN silent INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE reminder_profiles ADD COLUMN vibration_preset TEXT NOT NULL DEFAULT 'single'",
            "ALTER TABLE reminder_profiles ADD COLUMN vibration_intensity TEXT NOT NULL DEFAULT 'medium'",
            "ALTER TABLE reminder_profiles ADD COLUMN vibration_repeat_count INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE reminder_profiles ADD COLUMN vibration_continuous INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE reminder_profiles ADD COLUMN custom_vibration_pattern_csv TEXT",
            "ALTER TABLE reminder_profiles ADD COLUMN display_mode TEXT NOT NULL DEFAULT 'standard'",
            "ALTER TABLE reminder_profiles ADD COLUMN lock_screen_visibility TEXT NOT NULL DEFAULT 'app_name'",
            "ALTER TABLE reminder_profiles ADD COLUMN accent_color_hex TEXT",
            "ALTER TABLE reminder_profiles ADD COLUMN badge_mode TEXT NOT NULL DEFAULT 'total'",
            "ALTER TABLE reminder_profiles ADD COLUMN toast_position TEXT NOT NULL DEFAULT 'top_right'",
            "ALTER TABLE reminder_profiles ADD COLUMN escalation_chain_json TEXT",
            "ALTER TABLE reminder_profiles ADD COLUMN quiet_hours_json TEXT",
            "ALTER TABLE reminder_profiles ADD COLUMN snooze_durations_csv TEXT NOT NULL DEFAULT '5,15,30,60'",
            "ALTER TABLE reminder_profiles ADD COLUMN re_alert_interval_minutes INTEGER NOT NULL DEFAULT 5",
            "ALTER TABLE reminder_profiles ADD COLUMN re_alert_max_attempts INTEGER NOT NULL DEFAULT 3",
            "ALTER TABLE reminder_profiles ADD COLUMN watch_sync_mode TEXT NOT NULL DEFAULT 'mirror'",
            "ALTER TABLE reminder_profiles ADD COLUMN watch_haptic_preset_key TEXT NOT NULL DEFAULT 'single'",
            "ALTER TABLE reminder_profiles ADD COLUMN auto_switch_rules_json TEXT"
        )
        adds.forEach { db.execSQL(it) }

        // --- custom_sounds table ---
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `custom_sounds` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `original_filename` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `size_bytes` INTEGER NOT NULL,
                `duration_ms` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_sounds_name` ON `custom_sounds` (`name`)")
    }
}

// v1.4.0: add per-profile volume override to notification profiles
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder_profiles ADD COLUMN volume_override INTEGER NOT NULL DEFAULT 0")
    }
}

// Habit nag suppression: per-habit override columns for notification delay
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN nag_suppression_override_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN nag_suppression_days_override INTEGER NOT NULL DEFAULT -1")
    }
}

// Calendar sync: the device-calendar path was hard-deprecated and replaced
// by backend-mediated Google Calendar sync. Existing `calendar_sync` rows
// were written by the device path and reference device-provider event IDs
// that no longer resolve anywhere, so we drop them as part of the bump.
// The new schema adds `calendar_id` (which Google calendar the event lives
// on), `sync_state` (SYNCED / PENDING_PUSH / PENDING_DELETE / ERROR) and
// `etag` (for incremental change detection against the Google API).
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `calendar_sync`")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `calendar_sync` (
                `task_id` INTEGER NOT NULL PRIMARY KEY,
                `calendar_event_id` TEXT NOT NULL,
                `calendar_id` TEXT NOT NULL DEFAULT 'primary',
                `last_synced_at` INTEGER NOT NULL,
                `last_synced_version` INTEGER NOT NULL DEFAULT 0,
                `sync_state` TEXT NOT NULL DEFAULT 'SYNCED',
                `etag` TEXT,
                FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_sync_calendar_id` ON `calendar_sync` (`calendar_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_sync_sync_state` ON `calendar_sync` (`sync_state`)")
    }
}

// Today-screen habit skip windows: per-habit "skip if completed within N days"
// and "skip if scheduled within N days" overrides. -1 = inherit global default
// configured in HabitListPreferences.
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN today_skip_after_complete_days INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE habits ADD COLUMN today_skip_before_schedule_days INTEGER NOT NULL DEFAULT -1")
    }
}

// Data integrity hardening: backfill missing foreign keys on
// `study_logs.course_pick`, `study_logs.assignment_pick`, and
// `focus_release_logs.task_id`. SQLite has no `ALTER TABLE ADD CONSTRAINT`,
// so each table is recreated. Existing rows that point to deleted parents
// are first nulled out; the new FKs use `ON DELETE SET NULL` so future
// parent deletes preserve the historical log.
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- study_logs: add FKs for course_pick and assignment_pick ---
        db.execSQL(
            "UPDATE study_logs SET course_pick = NULL " +
                "WHERE course_pick IS NOT NULL " +
                "AND course_pick NOT IN (SELECT id FROM courses)"
        )
        db.execSQL(
            "UPDATE study_logs SET assignment_pick = NULL " +
                "WHERE assignment_pick IS NOT NULL " +
                "AND assignment_pick NOT IN (SELECT id FROM assignments)"
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `study_logs_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` INTEGER NOT NULL,
                `course_pick` INTEGER,
                `study_done` INTEGER NOT NULL DEFAULT 0,
                `assignment_pick` INTEGER,
                `assignment_done` INTEGER NOT NULL DEFAULT 0,
                `started_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`course_pick`) REFERENCES `courses`(`id`) ON DELETE SET NULL,
                FOREIGN KEY(`assignment_pick`) REFERENCES `assignments`(`id`) ON DELETE SET NULL
            )"""
        )
        db.execSQL(
            """INSERT INTO study_logs_new (id, date, course_pick, study_done, assignment_pick, assignment_done, started_at, created_at)
               SELECT id, date, course_pick, study_done, assignment_pick, assignment_done, started_at, created_at
               FROM study_logs"""
        )
        db.execSQL("DROP TABLE study_logs")
        db.execSQL("ALTER TABLE study_logs_new RENAME TO study_logs")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_study_logs_date` ON `study_logs` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_logs_course_pick` ON `study_logs` (`course_pick`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_logs_assignment_pick` ON `study_logs` (`assignment_pick`)")

        // --- focus_release_logs: add FK for task_id ---
        db.execSQL(
            "UPDATE focus_release_logs SET task_id = NULL " +
                "WHERE task_id IS NOT NULL " +
                "AND task_id NOT IN (SELECT id FROM tasks)"
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `focus_release_logs_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `event_type` TEXT NOT NULL,
                `task_id` INTEGER,
                `context` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON DELETE SET NULL
            )"""
        )
        db.execSQL(
            """INSERT INTO focus_release_logs_new (id, event_type, task_id, context, created_at)
               SELECT id, event_type, task_id, context, created_at
               FROM focus_release_logs"""
        )
        db.execSQL("DROP TABLE focus_release_logs")
        db.execSQL("ALTER TABLE focus_release_logs_new RENAME TO focus_release_logs")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_release_logs_task_id` ON `focus_release_logs` (`task_id`)")
    }
}

// Add materialized slot completions for Daily Essentials medication time
// slots. Rows are inserted lazily the first time the user interacts with a
// slot on a given day; reads merge these with the live virtual derivation.
// No FK to parent medication tables — dose keys inside ``med_ids_json`` are
// synthetic identifiers that survive refill renames.
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `daily_essential_slot_completions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` INTEGER NOT NULL,
                `slot_key` TEXT NOT NULL,
                `med_ids_json` TEXT NOT NULL DEFAULT '[]',
                `taken_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_essential_slot_completions_date_slot_key` " +
                "ON `daily_essential_slot_completions` (`date`, `slot_key`)"
        )
    }
}

// Allow users to add custom leisure sections (beyond the built-in music and
// flex slots). Their per-day pick/done state lives in this nullable JSON
// column; null means the user has no custom sections or hasn't interacted
// with any yet today.
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE leisure_logs ADD COLUMN custom_sections_state TEXT")
    }
}

// v1.4.0 Projects feature (Phase 1). Expands `projects` beyond a name+color
// container into a lifecycle-aware project-management entity (status, dates,
// completion / archive timestamps, theme-color token) and introduces the
// `milestones` child table with a CASCADE FK back to the project.
//
// Existing rows are preserved — new columns all default safely
// (status='ACTIVE', everything else NULL). No backfill; legacy tasks stay
// orphan-capable and the existing `tasks.project_id SET_NULL` FK is
// untouched.
val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE projects ADD COLUMN theme_color_key TEXT")
        db.execSQL("ALTER TABLE projects ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE projects ADD COLUMN start_date INTEGER")
        db.execSQL("ALTER TABLE projects ADD COLUMN end_date INTEGER")
        db.execSQL("ALTER TABLE projects ADD COLUMN completed_at INTEGER")
        db.execSQL("ALTER TABLE projects ADD COLUMN archived_at INTEGER")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `milestones` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `project_id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `is_completed` INTEGER NOT NULL DEFAULT 0,
                `completed_at` INTEGER,
                `order_index` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_milestones_project_id` ON `milestones` (`project_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_milestones_is_completed` ON `milestones` (`is_completed`)")
    }
}

// Built-in habit identity: add is_built_in flag and template_key so devices can
// reconcile independently-seeded built-in habits across a shared Firestore account.
// Backfills known built-in names so existing installs are covered immediately.
val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN is_built_in INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN template_key TEXT")
        db.execSQL("UPDATE habits SET is_built_in = 1, template_key = 'builtin_school' WHERE name = 'School'")
        db.execSQL("UPDATE habits SET is_built_in = 1, template_key = 'builtin_leisure' WHERE name = 'Leisure'")
        db.execSQL("UPDATE habits SET is_built_in = 1, template_key = 'builtin_morning_selfcare' WHERE name = 'Morning Self-Care'")
        db.execSQL("UPDATE habits SET is_built_in = 1, template_key = 'builtin_bedtime_selfcare' WHERE name = 'Bedtime Self-Care'")
        db.execSQL("UPDATE habits SET is_built_in = 1, template_key = 'builtin_medication' WHERE name = 'Medication'")
        db.execSQL("UPDATE habits SET is_built_in = 1, template_key = 'builtin_housework' WHERE name = 'Housework'")
    }
}

// Timezone-neutral habit completion dates: add `completed_date_local` (ISO
// LocalDate string in the device's local timezone at write time). Legacy rows
// are backfilled from the existing epoch `completed_date` using the migrating
// device's current timezone — see PR body / investigation notes for the
// single-user caveat. Future writes populate the field directly.
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habit_completions ADD COLUMN completed_date_local TEXT")
        db.execSQL(
            """
            UPDATE habit_completions
            SET completed_date_local =
                strftime('%Y-%m-%d', completed_date / 1000, 'unixepoch', 'localtime')
            WHERE completed_date_local IS NULL
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_habit_completions_completed_date_local` " +
                "ON `habit_completions` (`completed_date_local`)"
        )
    }
}

val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE self_care_logs ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE leisure_logs ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE self_care_steps ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE courses ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE course_completions ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
    }
}

// v51→v52 adds a nullable `cloud_id` column + unique index on every syncable
// entity, backfilled from sync_metadata.cloud_id. Fix E of the sync-duplication
// remediation: the Firestore document ID becomes a first-class, uniqueness-
// enforced column on the entity instead of living only in the separate
// sync_metadata table. Duplicate (entity_type, cloud_id) mappings in
// sync_metadata (a symptom of the pre-fix duplication race) are resolved by
// keeping the mapping with the lowest local_id and nulling the column on the
// rest. sync_metadata is NOT touched by this migration — the cleanup of that
// table comes in a later phase. The table/entity_type pairs below must mirror
// `SyncService.collectionNameFor` to stay consistent with the rest of sync.
val MIGRATION_51_52 = object : Migration(51, 52) {
    private val syncableTables = listOf(
        "tasks" to "task",
        "projects" to "project",
        "tags" to "tag",
        "habits" to "habit",
        "habit_completions" to "habit_completion",
        "habit_logs" to "habit_log",
        "task_completions" to "task_completion",
        "task_templates" to "task_template",
        "milestones" to "milestone",
        "courses" to "course",
        "course_completions" to "course_completion",
        "leisure_logs" to "leisure_log",
        "self_care_steps" to "self_care_step",
        "self_care_logs" to "self_care_log"
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        for ((table, entityType) in syncableTables) {
            // A. Add nullable TEXT column. No DEFAULT — new rows pass NULL.
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `cloud_id` TEXT")

            // B. Backfill from sync_metadata. NULLIF maps the sentinel empty
            //    string (SyncMetadataEntity.cloudId defaults to "") to NULL so
            //    unmapped rows don't collide on the unique index.
            db.execSQL(
                """
                UPDATE `$table` SET `cloud_id` = (
                    SELECT NULLIF(sm.cloud_id, '')
                    FROM sync_metadata sm
                    WHERE sm.local_id = `$table`.id
                      AND sm.entity_type = '$entityType'
                )
                """.trimIndent()
            )

            // C. Detect duplicate (entity_type, cloud_id) mappings. Keep the
            //    mapping whose local_id is smallest; NULL cloud_id on the
            //    others so the unique index can be created without
            //    constraint violations.
            val collisionCursor = db.query(
                """
                SELECT cloud_id, GROUP_CONCAT(local_id) AS local_ids, COUNT(*) AS n
                FROM sync_metadata
                WHERE entity_type = '$entityType' AND cloud_id != ''
                GROUP BY cloud_id
                HAVING n > 1
                """.trimIndent()
            )
            var collisionCount = 0
            collisionCursor.use { c ->
                while (c.moveToNext()) {
                    collisionCount++
                    val cid = c.getString(0)
                    val ids = c.getString(1)
                    val n = c.getInt(2)
                    android.util.Log.w(
                        "PrismSync.Migration_51_52",
                        "collision: entity_type=$entityType cloudId=$cid " +
                            "local_ids=[$ids] n=$n — keeping lowest local_id, " +
                            "nulling cloud_id on rest"
                    )
                }
            }
            if (collisionCount > 0) {
                db.execSQL(
                    """
                    UPDATE `$table` SET `cloud_id` = NULL
                    WHERE id IN (
                        SELECT sm.local_id
                        FROM sync_metadata sm
                        JOIN (
                            SELECT cloud_id, MIN(local_id) AS keep_id
                            FROM sync_metadata
                            WHERE entity_type = '$entityType' AND cloud_id != ''
                            GROUP BY cloud_id
                            HAVING COUNT(*) > 1
                        ) winners ON winners.cloud_id = sm.cloud_id
                        WHERE sm.entity_type = '$entityType'
                          AND sm.local_id <> winners.keep_id
                    )
                    """.trimIndent()
                )
                android.util.Log.w(
                    "PrismSync.Migration_51_52",
                    "entity_type=$entityType: $collisionCount colliding cloudId(s) resolved"
                )
            }

            // D. Create unique index AFTER collision resolution so inserts
            //    can safely multiply NULLs (SQLite treats each NULL as
            //    distinct in a UNIQUE index) while rejecting duplicate
            //    non-null cloudIds.
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_cloud_id` " +
                    "ON `$table` (`cloud_id`)"
            )
        }
    }
}

// v52→v53 adds a nullable `template_key` column to `task_templates` so that
// built-in templates have a stable, rename-proof identity string (parity with
// the `habits.template_key` column added in v48→v49). Backfills the known
// built-in names — including the three v1.3-era entries (`Assignment`,
// `Deep Clean`, `Morning Routine`) that are no longer seeded but may still
// live in Room from earlier installs — so the post-sync reconciler can group
// duplicate built-ins by key. User-created templates keep template_key NULL.
val MIGRATION_52_53 = object : Migration(52, 53) {
    private val builtInKeyMap = mapOf(
        "Weekly Review" to "builtin_weekly_review",
        "Meeting Prep" to "builtin_meeting_prep",
        "Grocery Run" to "builtin_grocery_run",
        "School Daily" to "builtin_school_daily",
        "Leisure Time" to "builtin_leisure_time",
        "Assignment" to "builtin_assignment",
        "Deep Clean" to "builtin_deep_clean",
        "Morning Routine" to "builtin_morning_routine"
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task_templates ADD COLUMN template_key TEXT")
        for ((name, key) in builtInKeyMap) {
            db.execSQL(
                "UPDATE task_templates SET template_key = ? " +
                    "WHERE is_built_in = 1 AND template_key IS NULL AND name = ?",
                arrayOf(key, name)
            )
        }
    }
}

// v53→v54 creates `medications` + `medication_doses` as new top-level entity
// tables (spec: `docs/SPEC_MEDICATIONS_TOP_LEVEL.md`, §3.2). Backfills
// `medications` rows from `self_care_steps` WHERE routine_type='medication',
// grouping duplicate-name source rows via GROUP_CONCAT/REPLACE. Quarantines
// the source data — source rows stay readable for the convergence window
// and are dropped by a future Phase 2 cleanup migration (v54→v55).
//
// Deviations from spec §3.2 for on-device safety:
//  - No json_each() dose backfill here (spec Step D) — the `->>` operator
//    requires SQLite 3.38+, but min SDK 26 ships SQLite 3.19 and JSON1
//    availability is OEM-dependent. Dose backfill runs in
//    `MedicationMigrationRunner` (Kotlin, PR 2) which parses
//    self_care_logs.completed_steps in-process.
//  - No sync_metadata sentinel row (spec Step F) — the one-shot flags live
//    in `MedicationMigrationPreferences` DataStore instead (PR 2).
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // A. New tables.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `medications` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `name` TEXT NOT NULL,
              `display_label` TEXT,
              `notes` TEXT NOT NULL DEFAULT '',
              `tier` TEXT NOT NULL DEFAULT 'essential',
              `is_archived` INTEGER NOT NULL DEFAULT 0,
              `sort_order` INTEGER NOT NULL DEFAULT 0,
              `schedule_mode` TEXT NOT NULL DEFAULT 'TIMES_OF_DAY',
              `times_of_day` TEXT,
              `specific_times` TEXT,
              `interval_millis` INTEGER,
              `doses_per_day` INTEGER NOT NULL DEFAULT 1,
              `pill_count` INTEGER,
              `pills_per_dose` INTEGER NOT NULL DEFAULT 1,
              `last_refill_date` INTEGER,
              `pharmacy_name` TEXT,
              `pharmacy_phone` TEXT,
              `reminder_days_before` INTEGER NOT NULL DEFAULT 3,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_medications_cloud_id` " +
                "ON `medications` (`cloud_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_medications_name` " +
                "ON `medications` (`name`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `medication_doses` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `medication_id` INTEGER NOT NULL,
              `slot_key` TEXT NOT NULL,
              `taken_at` INTEGER NOT NULL,
              `taken_date_local` TEXT NOT NULL,
              `note` TEXT NOT NULL DEFAULT '',
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              FOREIGN KEY(`medication_id`) REFERENCES `medications`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_doses_cloud_id` " +
                "ON `medication_doses` (`cloud_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_medication_doses_medication_id_taken_date_local` " +
                "ON `medication_doses` (`medication_id`, `taken_date_local`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_medication_doses_taken_date_local` " +
                "ON `medication_doses` (`taken_date_local`)"
        )

        // B. Quarantine tables — belt-and-braces copies of pre-migration
        //    source rows for forensics / emergency rollback. Not registered
        //    with Room (so Room's identity-hash check ignores them, same
        //    pattern as Fix D's quarantine_task_completions_null_taskid).
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quarantine_medication_selfcare_steps` AS " +
                "SELECT * FROM `self_care_steps` WHERE `routine_type` = 'medication'"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quarantine_medication_selfcare_logs` AS " +
                "SELECT * FROM `self_care_logs` WHERE `routine_type` = 'medication'"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quarantine_medication_refills` AS " +
                "SELECT * FROM `medication_refills`"
        )

        // C. Backfill medications from self_care_steps (routine_type='medication').
        //    Groups by COALESCE(NULLIF(TRIM(medication_name), ''), label) so
        //    duplicate-name source rows collapse to one row. `display_label`
        //    preserves disambiguating detail by concatenating distinct labels
        //    with " / ". `tier` stably picks the tier from the MIN-id source
        //    row in each group. Refill data (pharmacy_name, pill_count, etc.)
        //    merges in-line via LEFT JOIN on medication_name. schedule_mode
        //    is hard-coded 'TIMES_OF_DAY' here; the post-migration
        //    `MedicationMigrationRunner` overrides it based on the user's
        //    existing `MedicationPreferences` + built-in Medication habit's
        //    `reminderIntervalMillis`.
        //
        //    The REPLACE(GROUP_CONCAT, ',', ' / ') assumes no literal ','
        //    appears in any self_care_steps.label string (verified against
        //    representative data at spec-time; see spec Appendix B#4).
        db.execSQL(
            """
            INSERT INTO medications (
              name, display_label, notes, tier,
              schedule_mode, times_of_day,
              pill_count, pills_per_dose, doses_per_day, last_refill_date,
              pharmacy_name, pharmacy_phone, reminder_days_before,
              created_at, updated_at
            )
            SELECT
              grouped.normalized_name,
              grouped.merged_labels,
              '',
              grouped.picked_tier,
              'TIMES_OF_DAY',
              grouped.merged_times_of_day,
              r.pill_count,
              COALESCE(r.pills_per_dose, 1),
              COALESCE(r.doses_per_day, 1),
              r.last_refill_date,
              r.pharmacy_name,
              r.pharmacy_phone,
              COALESCE(r.reminder_days_before, 3),
              strftime('%s','now') * 1000,
              strftime('%s','now') * 1000
            FROM (
              SELECT
                COALESCE(NULLIF(TRIM(medication_name), ''), label) AS normalized_name,
                REPLACE(GROUP_CONCAT(DISTINCT label), ',', ' / ') AS merged_labels,
                GROUP_CONCAT(DISTINCT time_of_day) AS merged_times_of_day,
                (SELECT s2.tier FROM self_care_steps s2
                  WHERE s2.routine_type = 'medication'
                    AND COALESCE(NULLIF(TRIM(s2.medication_name), ''), s2.label) =
                        COALESCE(NULLIF(TRIM(self_care_steps.medication_name), ''), self_care_steps.label)
                  ORDER BY s2.id ASC LIMIT 1) AS picked_tier
              FROM self_care_steps
              WHERE routine_type = 'medication'
              GROUP BY normalized_name
            ) grouped
            LEFT JOIN medication_refills r
              ON r.medication_name = grouped.normalized_name
            """.trimIndent()
        )

        // D. Dose backfill intentionally NOT here — handled by
        //    MedicationMigrationRunner (PR 2) because the JSON parsing
        //    strategy is safer in Kotlin than SQLite's JSON1.
        //
        // E. Source tables (self_care_steps, self_care_logs, habits,
        //    medication_refills) are NOT modified. Phase 2 cleanup migration
        //    drops them after a convergence window.
    }
}

/**
 * v54 → v55: opt the seven remaining user-config Room entities into Firestore
 * sync by adding `cloud_id TEXT` (unique-indexed) and `updated_at INTEGER NOT
 * NULL DEFAULT 0`.
 *
 * - `reminder_profiles` (NotificationProfileEntity)
 * - `custom_sounds`     (CustomSoundEntity)
 * - `saved_filters`     (SavedFilterEntity)
 * - `nlp_shortcuts`     (NlpShortcutEntity)
 * - `habit_templates`   (HabitTemplateEntity)
 * - `project_templates` (ProjectTemplateEntity)
 * - `boundary_rules`    (BoundaryRuleEntity)
 *
 * Each table previously had no sync surface — so unlike [MIGRATION_51_52]
 * there is no backfill from `sync_metadata` and no collision resolution:
 * every row's `cloud_id` starts NULL, and `SyncService.doInitialUpload`
 * will assign cloud IDs on the next sign-in.
 *
 * `updated_at` defaults to `0` for existing rows — a fresh update will bump
 * it to the current wall clock, which beats every remote timestamp and
 * causes the first push after migration to win any last-write conflicts.
 * That's the desired semantic: the device that gets migrated first also
 * owns the seed copy of these tables cloud-side.
 */
val MIGRATION_54_55 = object : Migration(54, 55) {
    private val syncableTables = listOf(
        "reminder_profiles",
        "custom_sounds",
        "saved_filters",
        "nlp_shortcuts",
        "habit_templates",
        "project_templates",
        "boundary_rules"
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in syncableTables) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `cloud_id` TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_cloud_id` " +
                    "ON `$table` (`cloud_id`)"
            )
        }
    }
}

/**
 * v55 → v56: opt the nine remaining user-authored content entities into
 * Firestore sync by adding `cloud_id TEXT` (unique-indexed). Seven of
 * them also gain `updated_at INTEGER NOT NULL DEFAULT 0`; the two that
 * already had `updated_at` (from earlier migrations) are left alone on
 * that column.
 *
 * Tables:
 * - `check_in_logs`                    (CheckInLogEntity)       — needs updated_at
 * - `mood_energy_logs`                 (MoodEnergyLogEntity)    — needs updated_at
 * - `focus_release_logs`               (FocusReleaseLogEntity)  — needs updated_at; FK task_id (SET_NULL)
 * - `medication_refills`               (MedicationRefillEntity) — updated_at already present
 * - `weekly_reviews`                   (WeeklyReviewEntity)     — needs updated_at
 * - `daily_essential_slot_completions` (DailyEssentialSlotCompletionEntity) — updated_at already present
 * - `assignments`                      (AssignmentEntity)       — needs updated_at; FK course_id (CASCADE)
 * - `attachments`                      (AttachmentEntity)       — needs updated_at; FK taskId (CASCADE)
 * - `study_logs`                       (StudyLogEntity)         — needs updated_at; FK course_pick + assignment_pick (SET_NULL)
 *
 * No backfill needed — none of these tables had any prior sync_metadata
 * mappings. First sign-in post-migration runs `SyncService.doInitialUpload`
 * which assigns cloud IDs.
 */
val MIGRATION_55_56 = object : Migration(55, 56) {
    /** Tables that already have `updated_at` from earlier migrations. */
    private val alreadyHaveUpdatedAt = setOf("medication_refills", "daily_essential_slot_completions")

    private val syncableTables = listOf(
        "check_in_logs",
        "mood_energy_logs",
        "focus_release_logs",
        "medication_refills",
        "weekly_reviews",
        "daily_essential_slot_completions",
        "assignments",
        "attachments",
        "study_logs"
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in syncableTables) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `cloud_id` TEXT")
            if (table !in alreadyHaveUpdatedAt) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_cloud_id` " +
                    "ON `$table` (`cloud_id`)"
            )
        }
    }
}

/**
 * v1.4.x Eisenhower auto-classification: add `user_overrode_quadrant` so that
 * manual moves survive subsequent AI reclassification passes. Defaults to 0
 * (false) for all existing rows — they carry no prior override intent.
 */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `tasks` ADD COLUMN `user_overrode_quadrant` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_33,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36,
    MIGRATION_36_37,
    MIGRATION_37_38,
    MIGRATION_38_39,
    MIGRATION_39_40,
    MIGRATION_40_41,
    MIGRATION_41_42,
    MIGRATION_42_43,
    MIGRATION_43_44,
    MIGRATION_44_45,
    MIGRATION_45_46,
    MIGRATION_46_47,
    MIGRATION_47_48,
    MIGRATION_48_49,
    MIGRATION_49_50,
    MIGRATION_50_51,
    MIGRATION_51_52,
    MIGRATION_52_53,
    MIGRATION_53_54,
    MIGRATION_54_55,
    MIGRATION_55_56,
    MIGRATION_56_57
)
