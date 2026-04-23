package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_56_57
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v56 → v57 (A2 Eisenhower auto-classify).
 *
 * Adds `user_overrode_quadrant INTEGER NOT NULL DEFAULT 0` to `tasks`.
 * Existing rows must backfill to `0` so the auto-classifier is free to
 * stamp a quadrant; rows the user manually moves post-migration set this
 * to 1 (via `TaskDao.setManualQuadrant`), guarding against subsequent AI
 * passes clobbering user intent.
 */
@RunWith(AndroidJUnit4::class)
class Migration56To57Test {

    private fun openV56(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(56) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Stripped-down v56 `tasks` shape — MIGRATION_56_57 only
                    // cares that the table exists; the ADD COLUMN doesn't
                    // depend on any specific existing column beyond the
                    // primary key.
                    db.execSQL(
                        "CREATE TABLE `tasks` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL DEFAULT '', " +
                            "`eisenhower_quadrant` TEXT, " +
                            "`updated_at` INTEGER NOT NULL DEFAULT 0" +
                            ")"
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
    fun migration_addsUserOverrodeQuadrantColumn() {
        val helper = openV56()
        val db = helper.writableDatabase

        MIGRATION_56_57.migrate(db)

        db.query("PRAGMA table_info(`tasks`)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
            assertTrue(
                "tasks should have user_overrode_quadrant after migration; got $cols",
                "user_overrode_quadrant" in cols
            )
        }
        helper.close()
    }

    @Test
    fun migration_addedColumnIsNotNullWithDefaultZero() {
        val helper = openV56()
        val db = helper.writableDatabase

        MIGRATION_56_57.migrate(db)

        db.query("PRAGMA table_info(`tasks`)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            val notNullIdx = c.getColumnIndexOrThrow("notnull")
            val defaultIdx = c.getColumnIndexOrThrow("dflt_value")
            var found = false
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == "user_overrode_quadrant") {
                    found = true
                    assertEquals(
                        "user_overrode_quadrant must be NOT NULL",
                        1,
                        c.getInt(notNullIdx)
                    )
                    assertEquals(
                        "user_overrode_quadrant must default to 0",
                        "0",
                        c.getString(defaultIdx)
                    )
                }
            }
            assertTrue("user_overrode_quadrant column row in PRAGMA output", found)
        }
        helper.close()
    }

    @Test
    fun migration_backfillsPreExistingRowsToZero() {
        val helper = openV56()
        val db = helper.writableDatabase

        // Seed a mix of quadrant-assigned and unclassified rows to show
        // every pre-existing row lands with user_overrode_quadrant=0.
        db.execSQL(
            "INSERT INTO `tasks` (title, eisenhower_quadrant, updated_at) VALUES " +
                "('Prior Q1 task', 'Q1', 1000), " +
                "('Prior Q3 task', 'Q3', 2000), " +
                "('Prior unclassified', NULL, 3000)"
        )

        MIGRATION_56_57.migrate(db)

        db.query(
            "SELECT title, user_overrode_quadrant FROM tasks ORDER BY id"
        ).use { c ->
            var count = 0
            while (c.moveToNext()) {
                count++
                assertEquals(
                    "${c.getString(0)} must backfill user_overrode_quadrant=0",
                    0,
                    c.getInt(1)
                )
            }
            assertEquals("all seeded rows survive migration", 3, count)
        }
        helper.close()
    }

    @Test
    fun migration_existingEisenhowerColumnsAreUnchanged() {
        val helper = openV56()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO `tasks` (title, eisenhower_quadrant, updated_at) VALUES " +
                "('Keep Q2', 'Q2', 5000)"
        )

        MIGRATION_56_57.migrate(db)

        db.query(
            "SELECT eisenhower_quadrant, updated_at FROM tasks"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Q2", c.getString(0))
            assertEquals(5000L, c.getLong(1))
        }
        helper.close()
    }

    @Test
    fun migration_onEmptyTableSucceeds() {
        val helper = openV56()
        val db = helper.writableDatabase

        try {
            MIGRATION_56_57.migrate(db)
        } catch (e: Exception) {
            fail("migration must succeed on empty tasks: ${e.message}")
        }

        db.query("PRAGMA table_info(`tasks`)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
            assertTrue("user_overrode_quadrant" in cols)
        }
        helper.close()
    }

    @Test
    fun migration_allowsManualOverrideFlagPostMigration() {
        val helper = openV56()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO `tasks` (title, eisenhower_quadrant, updated_at) VALUES " +
                "('Task', 'Q1', 1000)"
        )

        MIGRATION_56_57.migrate(db)

        // Simulate what TaskDao.setManualQuadrant does — stamp the column to 1.
        db.execSQL(
            "UPDATE tasks SET user_overrode_quadrant = 1 WHERE id = 1"
        )
        db.query("SELECT user_overrode_quadrant FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        helper.close()
    }
}
