package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_55_56
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v55 → v56 (v1.4.38 content-entity sync).
 *
 * Adds `cloud_id TEXT` + a unique index on `cloud_id` to 9 content tables,
 * and — for the 7 of them that don't already carry `updated_at` — also
 * adds `updated_at INTEGER NOT NULL DEFAULT 0`. The two tables that
 * already had `updated_at` from earlier migrations (`medication_refills`,
 * `daily_essential_slot_completions`) keep their existing column and
 * must NOT be re-added (would throw a duplicate-column SQLite error).
 *
 * Pattern mirrors [Migration53To54Test] / [Migration54To55Test].
 *
 * Covers:
 *  - all 9 tables get `cloud_id` + unique index `index_<table>_cloud_id`
 *  - the 7 tables without prior `updated_at` receive it (default 0)
 *  - the 2 tables with prior `updated_at` keep theirs (no duplicate-column)
 *  - pre-existing rows survive with sensible defaults
 *  - unique index rejects duplicate cloud_id post-migration
 *  - empty tables migrate successfully
 */
@RunWith(AndroidJUnit4::class)
class Migration55To56Test {

    private val allNineTables = listOf(
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
    private val alreadyHaveUpdatedAt = setOf(
        "medication_refills",
        "daily_essential_slot_completions"
    )
    private val newlyGetUpdatedAt = allNineTables - alreadyHaveUpdatedAt

    private fun openV55(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(55) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // 7 tables WITHOUT updated_at at v55.
                    for (table in listOf(
                        "check_in_logs",
                        "mood_energy_logs",
                        "focus_release_logs",
                        "weekly_reviews",
                        "assignments",
                        "attachments",
                        "study_logs"
                    )) {
                        db.execSQL(
                            "CREATE TABLE `$table` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`name` TEXT NOT NULL DEFAULT ''" +
                                ")"
                        )
                    }
                    // 2 tables that already carried updated_at from prior migrations.
                    db.execSQL(
                        "CREATE TABLE `medication_refills` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL DEFAULT '', " +
                            "`updated_at` INTEGER NOT NULL DEFAULT 0" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE `daily_essential_slot_completions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL DEFAULT '', " +
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
    fun migration_addsCloudIdToAllNineTables() {
        val helper = openV55()
        val db = helper.writableDatabase

        MIGRATION_55_56.migrate(db)

        for (table in allNineTables) {
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val cols = mutableListOf<String>()
                while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
                assertTrue(
                    "$table should have cloud_id after migration; got $cols",
                    "cloud_id" in cols
                )
            }
        }
        helper.close()
    }

    @Test
    fun migration_addsUpdatedAtOnlyToTablesThatLackedIt() {
        val helper = openV55()
        val db = helper.writableDatabase

        MIGRATION_55_56.migrate(db)

        // All 9 end up with updated_at; the two pre-existing ones just didn't
        // get re-added — this test pins both groups so a future migration
        // that mis-classifies a table surfaces here.
        for (table in allNineTables) {
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val cols = mutableListOf<String>()
                while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
                assertTrue(
                    "$table should have updated_at after migration; got $cols",
                    "updated_at" in cols
                )
            }
        }
        helper.close()
    }

    @Test
    fun migration_createsUniqueIndexOnCloudIdForAllNineTables() {
        val helper = openV55()
        val db = helper.writableDatabase

        MIGRATION_55_56.migrate(db)

        for (table in allNineTables) {
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
    fun migration_doesNotReAddUpdatedAtToTablesThatAlreadyHaveIt() {
        val helper = openV55()
        val db = helper.writableDatabase

        // Seed pre-migration updated_at values to show they survive unchanged.
        db.execSQL(
            "INSERT INTO `medication_refills` (name, updated_at) VALUES ('refill', 7777)"
        )
        db.execSQL(
            "INSERT INTO `daily_essential_slot_completions` (name, updated_at) VALUES ('slot', 8888)"
        )

        MIGRATION_55_56.migrate(db)

        db.query("SELECT updated_at FROM medication_refills").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "pre-existing updated_at must not be reset by migration",
                7777L,
                c.getLong(0)
            )
        }
        db.query("SELECT updated_at FROM daily_essential_slot_completions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(8888L, c.getLong(0))
        }
        helper.close()
    }

    @Test
    fun migration_preExistingRowsInNewlyMigratedTablesGetDefaults() {
        val helper = openV55()
        val db = helper.writableDatabase

        for (table in newlyGetUpdatedAt) {
            db.execSQL("INSERT INTO `$table` (name) VALUES ('seed-$table')")
        }

        MIGRATION_55_56.migrate(db)

        for (table in newlyGetUpdatedAt) {
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
        val helper = openV55()
        val db = helper.writableDatabase

        MIGRATION_55_56.migrate(db)

        for (table in allNineTables) {
            db.execSQL("INSERT INTO `$table` (name, cloud_id) VALUES ('a', 'cid-1')")
            var threw = false
            try {
                db.execSQL("INSERT INTO `$table` (name, cloud_id) VALUES ('b', 'cid-1')")
            } catch (_: Exception) {
                threw = true
            }
            assertTrue(
                "unique index on $table.cloud_id must reject duplicate 'cid-1'",
                threw
            )
        }
        helper.close()
    }

    @Test
    fun migration_onEmptyTablesSucceedsWithoutError() {
        val helper = openV55()
        val db = helper.writableDatabase

        try {
            MIGRATION_55_56.migrate(db)
        } catch (e: Exception) {
            fail("migration must succeed on empty tables: ${e.message}")
        }
        helper.close()
    }
}
