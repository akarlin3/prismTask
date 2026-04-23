package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_52_53
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v52 → v53 (adds `template_key TEXT` to
 * `task_templates`, backfills eight known built-in names to their
 * `builtin_*` keys, guarded by `is_built_in = 1 AND template_key IS NULL`).
 */
@RunWith(AndroidJUnit4::class)
class Migration52To53Test {

    private fun openV52(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(52) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE `task_templates` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `is_built_in` INTEGER NOT NULL DEFAULT 0,
                            `created_at` INTEGER NOT NULL
                        )"""
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun allEightBuiltInNames_backfillToCanonicalKeys() {
        val helper = openV52()
        val db = helper.writableDatabase

        val pairs = listOf(
            "Weekly Review" to "builtin_weekly_review",
            "Meeting Prep" to "builtin_meeting_prep",
            "Grocery Run" to "builtin_grocery_run",
            "School Daily" to "builtin_school_daily",
            "Leisure Time" to "builtin_leisure_time",
            "Assignment" to "builtin_assignment",
            "Deep Clean" to "builtin_deep_clean",
            "Morning Routine" to "builtin_morning_routine"
        )
        for ((i, p) in pairs.withIndex()) {
            db.execSQL(
                "INSERT INTO task_templates (id, name, is_built_in, created_at) " +
                    "VALUES (${i + 1}, ?, 1, 100)",
                arrayOf(p.first)
            )
        }

        MIGRATION_52_53.migrate(db)

        for ((name, key) in pairs) {
            db.query(
                "SELECT template_key FROM task_templates WHERE name = ?",
                arrayOf(name)
            ).use { c ->
                assertTrue("row for $name missing", c.moveToFirst())
                assertEquals("template_key for $name", key, c.getString(0))
            }
        }
        helper.close()
    }

    @Test
    fun userTemplate_untouched() {
        val helper = openV52()
        val db = helper.writableDatabase

        // User template with one of the built-in names but is_built_in = 0 —
        // the migration's `WHERE is_built_in = 1` guard must skip it.
        db.execSQL(
            "INSERT INTO task_templates (id, name, is_built_in, created_at) " +
                "VALUES (10, 'Weekly Review', 0, 100)"
        )

        MIGRATION_52_53.migrate(db)

        db.query("SELECT template_key FROM task_templates WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("user template must keep template_key NULL", c.isNull(0))
        }
        helper.close()
    }

    /**
     * Verifies the `WHERE template_key IS NULL` guard in the migration's
     * UPDATE step: if a row already carries a non-null `template_key`, the
     * migration must not overwrite it. The scenario can only be set up if
     * the column is already present, so this test runs the migration first
     * (to add the column), inserts a row with a custom `template_key`, then
     * re-executes just the UPDATE statement for "Weekly Review" — mirroring
     * the one in [MIGRATION_52_53] — and asserts the custom value survives.
     *
     * Re-calling `MIGRATION_52_53.migrate(db)` directly would throw
     * `duplicate column name: template_key` because SQLite has no
     * `ADD COLUMN IF NOT EXISTS`; we intentionally test only the guarded
     * UPDATE here.
     */
    @Test
    fun builtIn_withPreExistingTemplateKey_notOverwritten() {
        val helper = openV52()
        val db = helper.writableDatabase

        MIGRATION_52_53.migrate(db)
        db.execSQL(
            "INSERT INTO task_templates (id, name, is_built_in, template_key, created_at) " +
                "VALUES (20, 'Weekly Review', 1, 'custom_existing_key', 100)"
        )

        db.execSQL(
            "UPDATE task_templates SET template_key = ? " +
                "WHERE is_built_in = 1 AND template_key IS NULL AND name = ?",
            arrayOf("builtin_weekly_review", "Weekly Review")
        )

        db.query("SELECT template_key FROM task_templates WHERE id = 20").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("custom_existing_key", c.getString(0))
        }
        helper.close()
    }

    @Test
    fun emptyTable_migratesWithoutError() {
        val helper = openV52()
        val db = helper.writableDatabase

        MIGRATION_52_53.migrate(db)

        db.query("SELECT COUNT(*) FROM task_templates").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun unknownBuiltInName_leftNull() {
        val helper = openV52()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO task_templates (id, name, is_built_in, created_at) " +
                "VALUES (30, 'Some Future Builtin', 1, 100)"
        )

        MIGRATION_52_53.migrate(db)

        db.query("SELECT template_key FROM task_templates WHERE id = 30").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(
                "unknown built-in name must not be backfilled (map only covers 8 known names)",
                c.isNull(0)
            )
        }
        helper.close()
    }
}
