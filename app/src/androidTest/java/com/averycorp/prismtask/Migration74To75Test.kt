package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_74_75
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v74 → v75 — PrismTask-timeline-class scope, PR-3.
 * Adds the `external_anchors` table with FK CASCADE on project and
 * SET_NULL on phase.
 */
@RunWith(AndroidJUnit4::class)
class Migration74To75Test {

    private fun openV74(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(74) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                    db.execSQL(
                        "CREATE TABLE `projects` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE `project_phases` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`project_id` INTEGER NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`order_index` INTEGER NOT NULL DEFAULT 0, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON DELETE CASCADE" +
                            ")"
                    )
                    db.execSQL("INSERT INTO `projects` (id, name) VALUES (1, 'PrismTask')")
                    db.execSQL(
                        "INSERT INTO `project_phases` (id, project_id, title, created_at, updated_at) " +
                            "VALUES (10, 1, 'Phase F', 0, 0)"
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
    fun migration_createsExternalAnchorsTable() {
        val helper = openV74()
        val db = helper.writableDatabase

        val pre = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) pre.add(c.getString(0))
        }
        assertFalse("external_anchors absent pre-migration", "external_anchors" in pre)

        MIGRATION_74_75.migrate(db)

        val post = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) post.add(c.getString(0))
        }
        assertTrue("external_anchors created", "external_anchors" in post)

        val indexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'external_anchors'"
        ).use { c -> while (c.moveToNext()) indexes.add(c.getString(0)) }
        assertTrue("cloud_id unique", "index_external_anchors_cloud_id" in indexes)
        assertTrue("project_id index", "index_external_anchors_project_id" in indexes)
        assertTrue("phase_id index", "index_external_anchors_phase_id" in indexes)

        helper.close()
    }

    @Test
    fun migration_phaseDeleteSetsPhaseIdNullProjectDeleteCascades() {
        val helper = openV74()
        val db = helper.writableDatabase
        MIGRATION_74_75.migrate(db)
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL(
            "INSERT INTO `external_anchors` " +
                "(id, project_id, phase_id, label, anchor_json, created_at, updated_at) " +
                "VALUES (100, 1, 10, 'Anchor', '{\"type\":\"calendar_deadline\",\"epochMs\":0}', 1, 1)"
        )

        // Phase delete -> SET NULL.
        db.execSQL("DELETE FROM `project_phases` WHERE id = 10")
        var phaseFk: Int? = -1
        db.query("SELECT phase_id FROM external_anchors WHERE id = 100").use { c ->
            assertTrue(c.moveToFirst())
            phaseFk = if (c.isNull(0)) null else c.getInt(0)
        }
        assertNull("phase_id reset to NULL", phaseFk)

        // Project delete -> CASCADE.
        db.execSQL("DELETE FROM `projects` WHERE id = 1")
        var count = -1
        db.query("SELECT COUNT(*) FROM external_anchors").use { c ->
            c.moveToFirst()
            count = c.getInt(0)
        }
        assertEquals("CASCADE removed orphan anchor", 0, count)

        helper.close()
    }
}
