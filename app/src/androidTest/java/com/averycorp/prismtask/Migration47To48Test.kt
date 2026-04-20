package com.averycorp.prismtask

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_47_48
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v47 → v48 (Projects feature Phase 1).
 *
 * The project ships with `exportSchema = false`, so Room's
 * [androidx.room.testing.MigrationTestHelper] isn't wired up. This test uses
 * [SupportSQLiteOpenHelper] to build a stripped-down v47 schema
 * (`projects` + `tasks` — everything else the migration touches is
 * greenfield), seeds representative rows, invokes the migration directly,
 * and asserts the after-state.
 */
@RunWith(AndroidJUnit4::class)
class Migration47To48Test {

    private fun openV47(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(47) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Pre-migration `projects` table: just id/name/color/icon + timestamps.
                    db.execSQL(
                        """CREATE TABLE `projects` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `color` TEXT NOT NULL DEFAULT '#4A90D9',
                            `icon` TEXT NOT NULL DEFAULT '📁',
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )"""
                    )
                    // Minimal `tasks` table with the FK that Phase 1 should
                    // leave untouched.
                    db.execSQL(
                        """CREATE TABLE `tasks` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `project_id` INTEGER,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON DELETE SET NULL
                        )"""
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    // The migration body is invoked manually in the test; no-op here.
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun migration_47to48_preservesExistingProjectRowsAndAddsNewColumns() {
        val helper = openV47()
        val db = helper.writableDatabase

        // Seed a pre-migration project.
        db.execSQL(
            "INSERT INTO projects (name, color, icon, created_at, updated_at) " +
                "VALUES ('Legacy', '#112233', '📁', 1000, 1000)"
        )
        // Seed a task linked to it — the SET_NULL FK must still work after migration.
        db.execSQL(
            "INSERT INTO tasks (title, project_id, created_at, updated_at) " +
                "VALUES ('Task on legacy project', 1, 1000, 1000)"
        )

        // Run the migration under test.
        MIGRATION_47_48.migrate(db)

        // Existing project row survives with the original fields intact.
        db.query(
            "SELECT id, name, color, icon, description, theme_color_key, " +
                "status, start_date, end_date, completed_at, archived_at FROM projects"
        ).use { cursor ->
            assertTrue("legacy project row should still exist", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("Legacy", cursor.getString(1))
            assertEquals("#112233", cursor.getString(2))
            assertEquals("📁", cursor.getString(3))
            assertTrue("description should default to null", cursor.isNull(4))
            assertTrue("theme_color_key should default to null", cursor.isNull(5))
            assertEquals("ACTIVE", cursor.getString(6))
            assertTrue("start_date should default to null", cursor.isNull(7))
            assertTrue("end_date should default to null", cursor.isNull(8))
            assertTrue("completed_at should default to null", cursor.isNull(9))
            assertTrue("archived_at should default to null", cursor.isNull(10))
        }

        // The linked task row still references the original project_id.
        db.query("SELECT project_id FROM tasks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }

        // `milestones` table was created with the expected columns.
        db.execSQL(
            "INSERT INTO milestones (project_id, title, is_completed, order_index, created_at, updated_at) " +
                "VALUES (1, 'First milestone', 0, 0, 2000, 2000)"
        )
        db.query("SELECT id, project_id, title, is_completed, completed_at, order_index FROM milestones").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNotNull(cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals("First milestone", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
            assertTrue(cursor.isNull(4))
            assertEquals(0, cursor.getInt(5))
        }

        helper.close()
    }

    @Test
    fun migration_47to48_statusDefaultsToActiveForExistingRows() {
        val helper = openV47()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO projects (name, color, icon, created_at, updated_at) VALUES " +
                "('A', '#111111', '📁', 1, 1), " +
                "('B', '#222222', '📁', 2, 2), " +
                "('C', '#333333', '📁', 3, 3)"
        )

        MIGRATION_47_48.migrate(db)

        db.query("SELECT COUNT(*) FROM projects WHERE status = 'ACTIVE'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
        helper.close()
    }

    @Test
    fun migration_47to48_milestonesCascadeDeleteWithParentProject() {
        val helper = openV47()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO projects (name, color, icon, created_at, updated_at) " +
                "VALUES ('Deletable', '#000', '📁', 1, 1)"
        )
        MIGRATION_47_48.migrate(db)

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO milestones (project_id, title, is_completed, order_index, created_at, updated_at) " +
                "VALUES (1, 'will disappear', 0, 0, 1, 1)"
        )

        db.execSQL("DELETE FROM projects WHERE id = 1")

        db.query("SELECT COUNT(*) FROM milestones").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        helper.close()
    }
}
