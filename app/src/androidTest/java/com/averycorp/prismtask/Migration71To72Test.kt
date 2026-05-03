package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_71_72
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v71 → v72 (additive `cognitive_load TEXT`
 * column on `tasks` for the start-friction Easy / Medium / Hard
 * dimension — see `docs/COGNITIVE_LOAD.md`).
 *
 * Stripped-down v71 schema only includes the columns we need to verify
 * the migration; full schema isn't required because we're testing the
 * single ALTER TABLE.
 *
 * Covers:
 *  - Existing rows survive the migration with their original column data.
 *  - `cognitive_load` defaults to NULL on pre-existing rows (no
 *    retroactive auto-classification).
 *  - New writes can populate `cognitive_load` post-migration with each
 *    enum value.
 */
@RunWith(AndroidJUnit4::class)
class Migration71To72Test {

    private fun openV71(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(71) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE `tasks` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `cloud_id` TEXT,
                            `title` TEXT NOT NULL,
                            `description` TEXT,
                            `due_date` INTEGER,
                            `priority` INTEGER NOT NULL DEFAULT 0,
                            `is_completed` INTEGER NOT NULL DEFAULT 0,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `life_category` TEXT,
                            `task_mode` TEXT
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
    fun migrate_addsCognitiveLoadColumnWithNullDefault() {
        val helper = openV71()
        val db = helper.writableDatabase

        // Two existing rows: one carrying both prior axes, one plain. Neither
        // had a cognitive_load column on v71, so both should get NULL.
        db.execSQL(
            "INSERT INTO tasks " +
                "(id, title, life_category, task_mode, created_at, updated_at) " +
                "VALUES (1, 'existing tagged task', 'WORK', 'PLAY', 100, 100)"
        )
        db.execSQL(
            "INSERT INTO tasks " +
                "(id, title, created_at, updated_at) " +
                "VALUES (2, 'plain task', 200, 200)"
        )

        MIGRATION_71_72.migrate(db)

        db.query(
            "SELECT id, title, life_category, task_mode, cognitive_load FROM tasks ORDER BY id"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getLong(0))
            assertEquals("existing tagged task", c.getString(1))
            assertEquals("WORK", c.getString(2))
            assertEquals("PLAY", c.getString(3))
            assertTrue(
                "cognitive_load must default to NULL on pre-existing rows " +
                    "(no retroactive auto-classification)",
                c.isNull(4)
            )

            assertTrue(c.moveToNext())
            assertEquals(2, c.getLong(0))
            assertNull(c.getString(2))
            assertNull(c.getString(3))
            assertTrue(c.isNull(4))
        }
        helper.close()
    }

    @Test
    fun migrate_allowsWritingEachCognitiveLoadValue() {
        val helper = openV71()
        val db = helper.writableDatabase
        MIGRATION_71_72.migrate(db)

        for ((id, load) in listOf(
            10L to "EASY",
            11L to "MEDIUM",
            12L to "HARD",
            13L to "UNCATEGORIZED"
        )) {
            db.execSQL(
                "INSERT INTO tasks (id, title, cognitive_load, created_at, updated_at) " +
                    "VALUES ($id, 'task $id', '$load', $id, $id)"
            )
        }

        db.query("SELECT id, cognitive_load FROM tasks WHERE id >= 10 ORDER BY id").use { c ->
            val seen = mutableMapOf<Long, String?>()
            while (c.moveToNext()) seen[c.getLong(0)] = c.getString(1)
            assertEquals("EASY", seen[10L])
            assertEquals("MEDIUM", seen[11L])
            assertEquals("HARD", seen[12L])
            assertEquals("UNCATEGORIZED", seen[13L])
        }
        helper.close()
    }
}
