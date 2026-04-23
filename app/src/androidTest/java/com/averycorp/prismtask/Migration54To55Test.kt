package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_54_55
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v54 → v55 (v1.4.37 config-entity sync:
 * `cloud_id TEXT` + `updated_at INTEGER NOT NULL DEFAULT 0` + unique
 * index on `cloud_id` across 7 tables).
 *
 * Pattern mirrors [Migration53To54Test]. The project ships with
 * `exportSchema = false` so Room's `MigrationTestHelper` isn't wired up;
 * this test seeds a stripped-down v54 schema via [SupportSQLiteOpenHelper]
 * and invokes the migration directly.
 *
 * Covers for each of the 7 config tables:
 *  - `cloud_id` column added (TEXT, nullable)
 *  - `updated_at` column added (INTEGER NOT NULL DEFAULT 0)
 *  - unique index `index_<table>_cloud_id` created
 *  - pre-existing rows get defaults (cloud_id=null, updated_at=0)
 *  - unique-index rejects post-migration duplicate cloud_ids
 *  - empty tables migrate successfully
 */
@RunWith(AndroidJUnit4::class)
class Migration54To55Test {

    private val tables = listOf(
        "reminder_profiles",
        "custom_sounds",
        "saved_filters",
        "nlp_shortcuts",
        "habit_templates",
        "project_templates",
        "boundary_rules"
    )

    private fun openV54(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(54) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Stripped-down v54 shape: just the primary key is enough
                    // for MIGRATION_54_55 to ADD COLUMN + CREATE UNIQUE INDEX
                    // on each table.
                    for (table in listOf(
                        "reminder_profiles",
                        "custom_sounds",
                        "saved_filters",
                        "nlp_shortcuts",
                        "habit_templates",
                        "project_templates",
                        "boundary_rules"
                    )) {
                        db.execSQL(
                            "CREATE TABLE `$table` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`name` TEXT NOT NULL DEFAULT ''" +
                                ")"
                        )
                    }
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
    fun migration_addsCloudIdAndUpdatedAtColumnsToEachTable() {
        val helper = openV54()
        val db = helper.writableDatabase

        MIGRATION_54_55.migrate(db)

        for (table in tables) {
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val cols = mutableListOf<String>()
                while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
                assertTrue(
                    "$table should have cloud_id after migration; got $cols",
                    "cloud_id" in cols
                )
                assertTrue(
                    "$table should have updated_at after migration; got $cols",
                    "updated_at" in cols
                )
            }
        }
        helper.close()
    }

    @Test
    fun migration_createsUniqueIndexOnCloudIdForEachTable() {
        val helper = openV54()
        val db = helper.writableDatabase

        MIGRATION_54_55.migrate(db)

        for (table in tables) {
            val expected = "index_${table}_cloud_id"
            db.query(
                "SELECT `unique` FROM pragma_index_list(?) WHERE name = ?",
                arrayOf<Any>(table, expected)
            ).use { c ->
                assertTrue("expected index $expected on $table", c.moveToNext())
                assertEquals("$expected must be unique", 1, c.getInt(0))
            }
        }
        helper.close()
    }

    @Test
    fun migration_backfillsPreExistingRowsWithDefaults() {
        val helper = openV54()
        val db = helper.writableDatabase

        // Seed one row per table so we can observe the defaulted columns
        // after the ADD COLUMN statements.
        for (table in tables) {
            db.execSQL("INSERT INTO `$table` (name) VALUES ('seed-$table')")
        }

        MIGRATION_54_55.migrate(db)

        for (table in tables) {
            db.query("SELECT cloud_id, updated_at FROM `$table`").use { c ->
                assertTrue("row in $table should survive migration", c.moveToFirst())
                assertTrue("$table.cloud_id defaults to NULL", c.isNull(0))
                assertEquals("$table.updated_at defaults to 0", 0L, c.getLong(1))
            }
        }
        helper.close()
    }

    @Test
    fun uniqueIndex_rejectsDuplicateCloudIdPostMigration() {
        val helper = openV54()
        val db = helper.writableDatabase

        MIGRATION_54_55.migrate(db)

        for (table in tables) {
            db.execSQL(
                "INSERT INTO `$table` (name, cloud_id, updated_at) " +
                    "VALUES ('a', 'cid-1', 1000)"
            )
            var threw = false
            try {
                db.execSQL(
                    "INSERT INTO `$table` (name, cloud_id, updated_at) " +
                        "VALUES ('b', 'cid-1', 2000)"
                )
            } catch (_: Exception) {
                threw = true
            }
            assertTrue(
                "unique index on $table.cloud_id must reject duplicate 'cid-1'",
                threw
            )
            // Multiple NULLs remain allowed (SQLite semantics).
            db.execSQL(
                "INSERT INTO `$table` (name, cloud_id, updated_at) " +
                    "VALUES ('c', NULL, 3000)"
            )
            db.execSQL(
                "INSERT INTO `$table` (name, cloud_id, updated_at) " +
                    "VALUES ('d', NULL, 4000)"
            )
        }
        helper.close()
    }

    @Test
    fun migration_onEmptyTablesSucceedsWithoutError() {
        val helper = openV54()
        val db = helper.writableDatabase

        try {
            MIGRATION_54_55.migrate(db)
        } catch (e: Exception) {
            fail("migration must succeed on empty tables: ${e.message}")
        }
        helper.close()
    }
}
