package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_58_59
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v58 → v59 — the schema + DEFAULT slot backfill part
 * of the v1.5 medication slot system (A2 #6 PR1). Covers:
 *
 *  - three new tables + their indexes exist after migration
 *  - a DEFAULT slot is seeded once (idempotent on re-run)
 *  - every pre-existing medication row is linked to the DEFAULT slot via
 *    the junction table
 *
 * Note: this work originally targeted v57 → v58 but rebased to 58 → 59
 * after the NLP batch ops (#692) shipped a `batch_undo_log` migration at
 * the 57 → 58 slot first.
 */
@RunWith(AndroidJUnit4::class)
class Migration58To59Test {

    private fun openV58(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(58) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Minimal v58 `medications` shape — MIGRATION_58_59 links
                    // to it via FK and needs `id` + `name`. No other columns
                    // are referenced by the migration SQL.
                    db.execSQL(
                        "CREATE TABLE `medications` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`tier` TEXT NOT NULL DEFAULT 'essential'" +
                            ")"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun migration_createsThreeNewTables() {
        val helper = openV58()
        val db = helper.writableDatabase

        MIGRATION_58_59.migrate(db)

        val tables = tablesIn(db)
        assertTrue("medication_slots created", "medication_slots" in tables)
        assertTrue("medication_slot_overrides created", "medication_slot_overrides" in tables)
        assertTrue("medication_medication_slots created", "medication_medication_slots" in tables)
        helper.close()
    }

    @Test
    fun migration_seedsExactlyOneDefaultSlot() {
        val helper = openV58()
        val db = helper.writableDatabase

        MIGRATION_58_59.migrate(db)

        db.query(
            "SELECT name, ideal_time, drift_minutes, is_active FROM medication_slots"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Default", c.getString(0))
            assertEquals("09:00", c.getString(1))
            assertEquals(180, c.getInt(2))
            assertEquals(1, c.getInt(3))
            assertFalse("exactly one DEFAULT row", c.moveToNext())
        }
        helper.close()
    }

    @Test
    fun migration_linksEveryExistingMedicationToDefaultSlot() {
        val helper = openV58()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO `medications` (name, tier) VALUES ('Lamotrigine', 'prescription'), " +
                "('Vitamin D', 'essential'), ('Fish Oil', 'complete')"
        )

        MIGRATION_58_59.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_medication_slots").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("one junction row per existing med", 3, c.getInt(0))
        }
        // Confirm every med links to the DEFAULT slot.
        db.query(
            "SELECT m.name FROM medications m " +
                "LEFT JOIN medication_medication_slots x ON x.medication_id = m.id " +
                "WHERE x.slot_id IS NULL"
        ).use { c ->
            assertFalse("no medication should be missing its junction row", c.moveToFirst())
        }
        helper.close()
    }

    @Test
    fun migration_onEmptyMedicationsTableStillSeedsDefaultSlot() {
        val helper = openV58()
        val db = helper.writableDatabase

        MIGRATION_58_59.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_slots").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "DEFAULT slot seeds even when medications table is empty — UI needs a starting point",
                1,
                c.getInt(0)
            )
        }
        db.query("SELECT COUNT(*) FROM medication_medication_slots").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun migration_isIdempotentOnReRun() {
        val helper = openV58()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name, tier) VALUES ('Adderall', 'prescription')")
        MIGRATION_58_59.migrate(db)
        // Re-running must NOT double-insert the DEFAULT slot or double-link.
        MIGRATION_58_59.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_slots").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("DEFAULT slot seeded exactly once across reruns", 1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM medication_medication_slots").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("junction row not duplicated", 1, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun migration_createsUniqueCloudIdIndexes() {
        val helper = openV58()
        val db = helper.writableDatabase

        MIGRATION_58_59.migrate(db)

        val indexes = indexesFor(db, "medication_slots") +
            indexesFor(db, "medication_slot_overrides")
        assertTrue(
            "medication_slots.cloud_id unique index present; got $indexes",
            indexes.any { it.contains("medication_slots_cloud_id") }
        )
        assertTrue(
            "medication_slot_overrides.cloud_id unique index present; got $indexes",
            indexes.any { it.contains("medication_slot_overrides_cloud_id") }
        )
        helper.close()
    }

    /**
     * Junction table CASCADE on parent medication delete: removing a
     * medication row must wipe its `medication_medication_slots`
     * entries so we never leak orphan junction rows when a user
     * deletes a med post-migration. Pin the FK CASCADE behavior
     * explicitly because Room migrations don't run with foreign-key
     * pragmas on by default.
     */
    @Test
    fun junction_cascadesOnParentMedicationDelete() {
        val helper = openV58()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('Aspirin')")
        MIGRATION_58_59.migrate(db)
        db.execSQL("PRAGMA foreign_keys = ON")

        db.query(
            "SELECT COUNT(*) FROM medication_medication_slots WHERE medication_id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("DEFAULT slot link present after migration", 1, c.getInt(0))
        }

        db.execSQL("DELETE FROM `medications` WHERE id = 1")

        db.query(
            "SELECT COUNT(*) FROM medication_medication_slots WHERE medication_id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("FK CASCADE must wipe junction rows with parent", 0, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun migration_enforcesUniquenessOnOverridePair() {
        val helper = openV58()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('X')")
        MIGRATION_58_59.migrate(db)

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO medication_slot_overrides " +
                "(medication_id, slot_id, created_at, updated_at) VALUES (1, 1, $now, $now)"
        )
        // Second insert on the same (medication_id, slot_id) pair must fail.
        var threw = false
        try {
            db.execSQL(
                "INSERT INTO medication_slot_overrides " +
                    "(medication_id, slot_id, created_at, updated_at) VALUES (1, 1, $now, $now)"
            )
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("unique(medication_id, slot_id) enforced", threw)
        helper.close()
    }

    private fun tablesIn(db: androidx.sqlite.db.SupportSQLiteDatabase): Set<String> {
        val out = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    private fun indexesFor(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<String> {
        val out = mutableListOf<String>()
        db.query("PRAGMA index_list(`$table`)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) out.add(c.getString(nameIdx))
        }
        return out
    }
}
