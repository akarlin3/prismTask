package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_57_58
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v57 → v58 (NLP batch ops — `batch_undo_log`).
 * Pattern mirrors [Migration53To54Test]. The project ships with
 * `exportSchema = false` so Room's `MigrationTestHelper` isn't wired up;
 * this test seeds an empty v57-shaped DB via [SupportSQLiteOpenHelper],
 * invokes the migration, and asserts the post-state.
 */
@RunWith(AndroidJUnit4::class)
class Migration57To58Test {

    private fun openV57(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(57) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // batch_undo_log is a brand-new table with no source — no
                    // pre-existing schema needs to be seeded.
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
    fun migration_createsBatchUndoLogTable() {
        val helper = openV57()
        val db = helper.writableDatabase

        MIGRATION_57_58.migrate(db)

        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = 'batch_undo_log'"
        ).use { c ->
            assertTrue("batch_undo_log table must exist post-migration", c.moveToNext())
            assertEquals("batch_undo_log", c.getString(0))
        }
        helper.close()
    }

    @Test
    fun migration_createsAllThreeIndexes() {
        val helper = openV57()
        val db = helper.writableDatabase

        MIGRATION_57_58.migrate(db)

        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' " +
                "AND name LIKE 'index_batch_undo_log_%' ORDER BY name"
        ).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(0)
            assertTrue(
                "missing batch_id index, got $names",
                "index_batch_undo_log_batch_id" in names
            )
            assertTrue(
                "missing created_at index, got $names",
                "index_batch_undo_log_created_at" in names
            )
            assertTrue(
                "missing (expires_at, undone_at) compound index, got $names",
                "index_batch_undo_log_expires_at_undone_at" in names
            )
        }
        helper.close()
    }

    @Test
    fun schema_columnsMatchEntityShape() {
        val helper = openV57()
        val db = helper.writableDatabase

        MIGRATION_57_58.migrate(db)

        // PRAGMA table_info returns one row per column with
        // (cid, name, type, notnull, dflt_value, pk).
        val cols = mutableMapOf<String, Triple<String, Int, Int>>()
        db.query("PRAGMA table_info(`batch_undo_log`)").use { c ->
            while (c.moveToNext()) {
                cols[c.getString(1)] = Triple(c.getString(2), c.getInt(3), c.getInt(5))
            }
        }
        assertEquals("INTEGER", cols["id"]?.first)
        assertEquals("primary key", 1, cols["id"]?.third)
        assertEquals("TEXT", cols["batch_id"]?.first)
        assertEquals("batch_id NOT NULL", 1, cols["batch_id"]?.second)
        assertEquals("TEXT", cols["batch_command_text"]?.first)
        assertEquals("batch_command_text NOT NULL", 1, cols["batch_command_text"]?.second)
        assertEquals("TEXT", cols["entity_type"]?.first)
        assertEquals("entity_type NOT NULL", 1, cols["entity_type"]?.second)
        assertEquals("INTEGER", cols["entity_id"]?.first)
        assertEquals("entity_id nullable", 0, cols["entity_id"]?.second)
        assertEquals("TEXT", cols["entity_cloud_id"]?.first)
        assertEquals("entity_cloud_id nullable", 0, cols["entity_cloud_id"]?.second)
        assertEquals("TEXT", cols["pre_state_json"]?.first)
        assertEquals("pre_state_json NOT NULL", 1, cols["pre_state_json"]?.second)
        assertEquals("TEXT", cols["mutation_type"]?.first)
        assertEquals("INTEGER", cols["created_at"]?.first)
        assertEquals("created_at NOT NULL", 1, cols["created_at"]?.second)
        assertEquals("INTEGER", cols["undone_at"]?.first)
        assertEquals("undone_at nullable", 0, cols["undone_at"]?.second)
        assertEquals("INTEGER", cols["expires_at"]?.first)
        assertEquals("expires_at NOT NULL", 1, cols["expires_at"]?.second)
        helper.close()
    }

    @Test
    fun postMigration_canInsertAndQueryRow() {
        val helper = openV57()
        val db = helper.writableDatabase

        MIGRATION_57_58.migrate(db)

        db.execSQL(
            "INSERT INTO batch_undo_log " +
                "(batch_id, batch_command_text, entity_type, entity_id, " +
                " entity_cloud_id, pre_state_json, mutation_type, " +
                " created_at, undone_at, expires_at) VALUES " +
                "('uuid-1', 'Cancel everything Friday', 'TASK', 42, " +
                " 'cloud_42', '{\"id\":42}', 'DELETE', 1000, NULL, 87400000)"
        )

        db.query(
            "SELECT batch_id, entity_type, entity_id, mutation_type, undone_at " +
                "FROM batch_undo_log"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("uuid-1", c.getString(0))
            assertEquals("TASK", c.getString(1))
            assertEquals(42L, c.getLong(2))
            assertEquals("DELETE", c.getString(3))
            assertTrue("undone_at must be NULL on insert", c.isNull(4))
        }
        helper.close()
    }

    @Test
    fun postMigration_entityIdAndCloudIdAreNullable() {
        val helper = openV57()
        val db = helper.writableDatabase

        MIGRATION_57_58.migrate(db)

        // Hard-deleted entity → no local id, no cloud id. Must succeed.
        db.execSQL(
            "INSERT INTO batch_undo_log " +
                "(batch_id, batch_command_text, entity_type, entity_id, " +
                " entity_cloud_id, pre_state_json, mutation_type, " +
                " created_at, expires_at) VALUES " +
                "('uuid-2', 'Clear Thursday', 'PROJECT', NULL, NULL, " +
                " '{\"id\":null}', 'ARCHIVE', 1000, 87400000)"
        )

        db.query(
            "SELECT entity_id, entity_cloud_id FROM batch_undo_log " +
                "WHERE batch_id = 'uuid-2'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
            assertNull(c.getString(1))
        }
        helper.close()
    }

    @Test
    fun emptyPostMigration_tableIsEmpty() {
        val helper = openV57()
        val db = helper.writableDatabase

        MIGRATION_57_58.migrate(db)

        db.query("SELECT COUNT(*) FROM batch_undo_log").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("brand-new table with no backfill", 0, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun batchIdIsNotUnique_multipleRowsPerBatchAllowed() {
        val helper = openV57()
        val db = helper.writableDatabase

        MIGRATION_57_58.migrate(db)

        // A batch may touch dozens of entities — they all share batch_id.
        // Index on batch_id is non-unique; insert four rows under one id.
        for (i in 1..4) {
            db.execSQL(
                "INSERT INTO batch_undo_log " +
                    "(batch_id, batch_command_text, entity_type, entity_id, " +
                    " entity_cloud_id, pre_state_json, mutation_type, " +
                    " created_at, expires_at) VALUES " +
                    "('shared-uuid', 'cancel everything Friday', 'TASK', $i, " +
                    " 'cloud_$i', '{\"id\":$i}', 'DELETE', 1000, 87400000)"
            )
        }

        db.query(
            "SELECT COUNT(*) FROM batch_undo_log WHERE batch_id = 'shared-uuid'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
        }
        // Verify the index actually exists and is non-unique.
        db.query(
            "SELECT `unique` FROM pragma_index_list('batch_undo_log') " +
                "WHERE name = 'index_batch_undo_log_batch_id'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertFalse("batch_id index must be non-unique", c.getInt(0) == 1)
        }
        helper.close()
    }
}
