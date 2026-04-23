package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_48_49
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v48 → v49 (adds `is_built_in` and
 * `template_key` to `habits`, backfills six built-in names to their
 * `builtin_*` keys).
 */
@RunWith(AndroidJUnit4::class)
class Migration48To49Test {

    private fun openV48(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(48) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE `habits` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `description` TEXT,
                            `color` TEXT NOT NULL DEFAULT '#6B7280',
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
    fun builtIn_backfills_forAllSixKnownNames() {
        val helper = openV48()
        val db = helper.writableDatabase

        val names = listOf(
            "School",
            "Leisure",
            "Morning Self-Care",
            "Bedtime Self-Care",
            "Medication",
            "Housework"
        )
        for ((i, name) in names.withIndex()) {
            db.execSQL(
                "INSERT INTO habits (id, name, created_at) VALUES (${i + 1}, ?, 100)",
                arrayOf(name)
            )
        }

        MIGRATION_48_49.migrate(db)

        val expected = mapOf(
            "School" to "builtin_school",
            "Leisure" to "builtin_leisure",
            "Morning Self-Care" to "builtin_morning_selfcare",
            "Bedtime Self-Care" to "builtin_bedtime_selfcare",
            "Medication" to "builtin_medication",
            "Housework" to "builtin_housework"
        )
        for ((name, key) in expected) {
            db.query(
                "SELECT is_built_in, template_key FROM habits WHERE name = ?",
                arrayOf(name)
            ).use { c ->
                assertTrue("row for $name missing", c.moveToFirst())
                assertEquals("is_built_in for $name", 1, c.getInt(0))
                assertEquals("template_key for $name", key, c.getString(1))
            }
        }
        helper.close()
    }

    @Test
    fun userHabit_withUnmatchedName_leftUntouched() {
        val helper = openV48()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO habits (id, name, created_at) VALUES (10, 'Read 20 pages', 100)")

        MIGRATION_48_49.migrate(db)

        db.query("SELECT is_built_in, template_key FROM habits WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("is_built_in default 0 for user habit", 0, c.getInt(0))
            assertTrue("template_key NULL for user habit", c.isNull(1))
        }
        helper.close()
    }

    @Test
    fun newlyInsertedRow_defaultsAppliedPostMigration() {
        val helper = openV48()
        val db = helper.writableDatabase

        MIGRATION_48_49.migrate(db)

        db.execSQL("INSERT INTO habits (id, name, created_at) VALUES (99, 'Post-migration habit', 100)")

        db.query("SELECT is_built_in, template_key FROM habits WHERE id = 99").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertTrue(c.isNull(1))
        }
        helper.close()
    }

    @Test
    fun nameCaseMismatch_doesNotBackfill() {
        val helper = openV48()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO habits (id, name, created_at) VALUES (20, 'school', 100)")
        db.execSQL("INSERT INTO habits (id, name, created_at) VALUES (21, 'SCHOOL', 100)")

        MIGRATION_48_49.migrate(db)

        db.query("SELECT id, is_built_in, template_key FROM habits WHERE id IN (20, 21)").use { c ->
            while (c.moveToNext()) {
                assertEquals("case-mismatch name must not backfill", 0, c.getInt(1))
                assertNull(c.getString(2))
            }
        }
        helper.close()
    }
}
