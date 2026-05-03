package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_72_73
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v72 → v73 — PrismTask-timeline-class scope, PR-2.
 * Adds `task_dependencies` table with FK CASCADE on both endpoints.
 */
@RunWith(AndroidJUnit4::class)
class Migration72To73Test {

    private fun openV72(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(72) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                    db.execSQL(
                        "CREATE TABLE `tasks` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL" +
                            ")"
                    )
                    db.execSQL("INSERT INTO `tasks` (id, title) VALUES (1, 'A')")
                    db.execSQL("INSERT INTO `tasks` (id, title) VALUES (2, 'B')")
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
    fun migration_createsTaskDependenciesTable() {
        val helper = openV72()
        val db = helper.writableDatabase

        val pre = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) pre.add(c.getString(0))
        }
        assertFalse("task_dependencies absent pre-migration", "task_dependencies" in pre)

        MIGRATION_72_73.migrate(db)

        val post = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) post.add(c.getString(0))
        }
        assertTrue("task_dependencies created", "task_dependencies" in post)

        val indexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'task_dependencies'"
        ).use { c -> while (c.moveToNext()) indexes.add(c.getString(0)) }
        assertTrue(
            "(blocker, blocked) unique index",
            "index_task_dependencies_blocker_task_id_blocked_task_id" in indexes
        )
        assertTrue(
            "blocked_task_id index",
            "index_task_dependencies_blocked_task_id" in indexes
        )
        assertTrue(
            "cloud_id unique index",
            "index_task_dependencies_cloud_id" in indexes
        )

        helper.close()
    }

    @Test
    fun migration_uniquePairConstraintEnforced() {
        val helper = openV72()
        val db = helper.writableDatabase
        MIGRATION_72_73.migrate(db)

        db.execSQL(
            "INSERT INTO `task_dependencies` " +
                "(blocker_task_id, blocked_task_id, created_at) VALUES (1, 2, 100)"
        )
        // Second identical insert should fail the unique index.
        var threw = false
        try {
            db.execSQL(
                "INSERT INTO `task_dependencies` " +
                    "(blocker_task_id, blocked_task_id, created_at) VALUES (1, 2, 200)"
            )
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("unique pair index rejected duplicate", threw)

        var count = -1
        db.query("SELECT COUNT(*) FROM task_dependencies").use { c ->
            c.moveToFirst(); count = c.getInt(0)
        }
        assertEquals(1, count)
        helper.close()
    }

    @Test
    fun migration_cascadesEdgesWhenEitherEndpointDeleted() {
        val helper = openV72()
        val db = helper.writableDatabase
        MIGRATION_72_73.migrate(db)
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL(
            "INSERT INTO `task_dependencies` " +
                "(blocker_task_id, blocked_task_id, created_at) VALUES (1, 2, 100)"
        )
        db.execSQL("DELETE FROM `tasks` WHERE id = 1")

        var count = -1
        db.query("SELECT COUNT(*) FROM task_dependencies").use { c ->
            c.moveToFirst(); count = c.getInt(0)
        }
        assertEquals("CASCADE removed orphaned edge", 0, count)
        helper.close()
    }
}
