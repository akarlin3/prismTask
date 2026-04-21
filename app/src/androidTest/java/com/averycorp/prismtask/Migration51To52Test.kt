package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_51_52
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v51 → v52 (sync `cloud_id` column + unique
 * index on every syncable entity, backfilled from `sync_metadata`). Follows
 * the pattern of [Migration47To48Test] — the project ships with
 * `exportSchema = false`, so Room's [androidx.room.testing.MigrationTestHelper]
 * isn't wired up. Instead, a stripped-down v51 schema is built via
 * [SupportSQLiteOpenHelper], rows are seeded, the migration is invoked
 * directly, and after-state is asserted.
 *
 * Covers:
 *  - one-to-one backfill (Room row + sync_metadata entry → cloud_id populated)
 *  - collision path (N Room rows sharing the same sync_metadata.cloud_id →
 *    smallest local_id wins, losers get cloud_id NULL)
 *  - unique-index enforcement (attempted duplicate non-null cloud_id rejected)
 *  - multiple NULLs coexist under the unique index (SQLite NULL-distinct)
 *  - empty-string sync_metadata.cloud_id sentinel backfills to NULL
 *  - orphan sync_metadata entries (no matching Room row) are a no-op
 *  - different entity_types with the same local_id are independent per table
 */
@RunWith(AndroidJUnit4::class)
class Migration51To52Test {

    private fun openV51(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(51) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Real-ish schemas for the two tables the tests actually
                    // exercise. The other 12 tables the migration iterates
                    // exist as bare shells (just `id`) — enough for
                    // `ALTER TABLE ... ADD COLUMN` + `CREATE UNIQUE INDEX`
                    // to succeed. The backfill UPDATE finds no matching
                    // sync_metadata for those, so it's a no-op.
                    db.execSQL(
                        """CREATE TABLE `tags` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `color` TEXT NOT NULL DEFAULT '#6B7280',
                            `created_at` INTEGER NOT NULL
                        )"""
                    )
                    db.execSQL(
                        """CREATE TABLE `projects` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `description` TEXT,
                            `color` TEXT NOT NULL DEFAULT '#4A90D9',
                            `icon` TEXT NOT NULL DEFAULT '📁',
                            `theme_color_key` TEXT,
                            `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                            `start_date` INTEGER,
                            `end_date` INTEGER,
                            `completed_at` INTEGER,
                            `archived_at` INTEGER,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )"""
                    )
                    val bareShells = listOf(
                        "tasks", "habits", "habit_completions", "habit_logs",
                        "task_completions", "task_templates", "milestones",
                        "courses", "course_completions", "leisure_logs",
                        "self_care_steps", "self_care_logs"
                    )
                    for (t in bareShells) {
                        db.execSQL(
                            "CREATE TABLE `$t` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" +
                                ")"
                        )
                    }
                    // sync_metadata — real schema required so backfill
                    // correlated-subqueries resolve correctly.
                    db.execSQL(
                        """CREATE TABLE `sync_metadata` (
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
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    // Migration invoked manually in each test.
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun backfill_populatesCloudId_forOneToOneMapping() {
        val helper = openV51()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (1, 'work', 100)")
        db.execSQL(
            "INSERT INTO sync_metadata (local_id, entity_type, cloud_id, last_synced_at) " +
                "VALUES (1, 'tag', 'cloud_work_A', 100)"
        )

        MIGRATION_51_52.migrate(db)

        db.query("SELECT cloud_id FROM tags WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("cloud_work_A", c.getString(0))
        }
        helper.close()
    }

    @Test
    fun backfill_collision_keepsSmallestLocalId_nullsOthers() {
        val helper = openV51()
        val db = helper.writableDatabase

        // Three rows, all duplicates with the same sync_metadata cloud_id.
        // Mirrors the 12-way collision seen in real data (e.g. cloud_id
        // `BKPk6JCAQNqs1ms41Ugf` mapped to tag local_ids 2333..2344, all
        // named 'assignment').
        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (10, 'assignment', 100)")
        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (11, 'assignment', 100)")
        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (12, 'assignment', 100)")
        db.execSQL(
            "INSERT INTO sync_metadata (local_id, entity_type, cloud_id, last_synced_at) VALUES " +
                "(10, 'tag', 'cloud_dup_X', 100), " +
                "(11, 'tag', 'cloud_dup_X', 100), " +
                "(12, 'tag', 'cloud_dup_X', 100)"
        )

        MIGRATION_51_52.migrate(db)

        db.query("SELECT id, cloud_id FROM tags ORDER BY id").use { c ->
            assertTrue(c.moveToNext())
            assertEquals(10L, c.getLong(0))
            assertEquals("winner must keep cloud_id", "cloud_dup_X", c.getString(1))

            assertTrue(c.moveToNext())
            assertEquals(11L, c.getLong(0))
            assertTrue("loser row must have NULL cloud_id", c.isNull(1))

            assertTrue(c.moveToNext())
            assertEquals(12L, c.getLong(0))
            assertTrue("loser row must have NULL cloud_id", c.isNull(1))
        }
        helper.close()
    }

    @Test
    fun uniqueIndex_rejectsDuplicateNonNullCloudId() {
        val helper = openV51()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (20, 'a', 100)")
        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (21, 'b', 100)")
        db.execSQL(
            "INSERT INTO sync_metadata (local_id, entity_type, cloud_id, last_synced_at) " +
                "VALUES (20, 'tag', 'cloud_unique', 100)"
        )

        MIGRATION_51_52.migrate(db)

        // Post-migration: attempting to populate a second row with the same
        // non-null cloud_id must throw on the unique index.
        var threw = false
        try {
            db.execSQL("UPDATE tags SET cloud_id = 'cloud_unique' WHERE id = 21")
        } catch (_: Exception) {
            threw = true
        }
        assertTrue(
            "unique index on cloud_id should reject a duplicate non-null write",
            threw
        )
        helper.close()
    }

    @Test
    fun uniqueIndex_allowsMultipleNullCloudIds() {
        val helper = openV51()
        val db = helper.writableDatabase

        // Three rows with no sync_metadata entries — all backfill to NULL
        // and coexist under the unique index (SQLite treats each NULL as
        // distinct in a UNIQUE index).
        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (30, 'x', 100)")
        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (31, 'y', 100)")
        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (32, 'z', 100)")

        MIGRATION_51_52.migrate(db)

        db.query("SELECT COUNT(*) FROM tags WHERE cloud_id IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun backfill_emptyStringCloudId_becomesNull() {
        val helper = openV51()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (40, 'w', 100)")
        db.execSQL(
            // NULLIF in the migration maps this '' sentinel (SyncMetadataEntity
            // default) to NULL rather than an empty string — otherwise two
            // unmapped rows would collide on the unique index.
            "INSERT INTO sync_metadata (local_id, entity_type, cloud_id, last_synced_at) " +
                "VALUES (40, 'tag', '', 100)"
        )

        MIGRATION_51_52.migrate(db)

        db.query("SELECT cloud_id FROM tags WHERE id = 40").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("'' sentinel must backfill to NULL, not ''", c.isNull(0))
        }
        helper.close()
    }

    @Test
    fun orphanSyncMetadata_noCrash_noSpuriousInsert() {
        val helper = openV51()
        val db = helper.writableDatabase

        // sync_metadata references a tag id that doesn't exist in tags.
        db.execSQL(
            "INSERT INTO sync_metadata (local_id, entity_type, cloud_id, last_synced_at) " +
                "VALUES (999, 'tag', 'cloud_orphan', 100)"
        )

        MIGRATION_51_52.migrate(db)

        db.query("SELECT COUNT(*) FROM tags").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        // The orphan sync_metadata row is preserved — migration never
        // deletes from sync_metadata.
        db.query("SELECT COUNT(*) FROM sync_metadata WHERE local_id = 999").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun backfill_spansMultipleEntityTypes_independentlyPerTable() {
        val helper = openV51()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (50, 'a', 100)")
        db.execSQL(
            """INSERT INTO projects
                   (id, name, color, icon, status, created_at, updated_at)
               VALUES (50, 'P1', '#000', '📁', 'ACTIVE', 100, 100)"""
        )
        // Same local_id=50 across two entity_types — a common, valid case
        // since the sync_metadata PK is (local_id, entity_type).
        db.execSQL(
            "INSERT INTO sync_metadata (local_id, entity_type, cloud_id, last_synced_at) VALUES " +
                "(50, 'tag', 'cloud_tag_Z', 100), " +
                "(50, 'project', 'cloud_project_Z', 100)"
        )

        MIGRATION_51_52.migrate(db)

        db.query("SELECT cloud_id FROM tags WHERE id = 50").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("cloud_tag_Z", c.getString(0))
        }
        db.query("SELECT cloud_id FROM projects WHERE id = 50").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("cloud_project_Z", c.getString(0))
        }
        helper.close()
    }
}
