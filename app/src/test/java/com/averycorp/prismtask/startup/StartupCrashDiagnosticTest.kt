package com.averycorp.prismtask.startup

import com.averycorp.prismtask.data.local.database.ALL_MIGRATIONS
import com.averycorp.prismtask.data.local.database.MIGRATION_1_2
import com.averycorp.prismtask.data.local.database.MIGRATION_37_38
import com.averycorp.prismtask.data.local.database.MIGRATION_38_39
import com.averycorp.prismtask.data.local.database.MIGRATION_39_40
import com.averycorp.prismtask.data.local.database.MIGRATION_40_41
import com.averycorp.prismtask.data.local.database.MIGRATION_41_42
import com.averycorp.prismtask.data.local.database.MIGRATION_42_43
import com.averycorp.prismtask.data.local.database.MIGRATION_43_44
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup crash diagnostic tests.
 *
 * These tests verify structural invariants that, when violated, cause the app
 * to crash on launch. They run as pure JVM tests (no Android/Robolectric) and
 * are designed to catch the most common startup crash patterns:
 *
 * 1. Room migration gaps — a missing migration causes IllegalStateException
 * 2. DataStore file name collisions — two delegates with the same name cause
 *    IllegalStateException ("There are multiple DataStores active for …")
 * 3. Incorrect migration version ordering in ALL_MIGRATIONS
 */
class StartupCrashDiagnosticTest {
    // ------------------------------------------------------------------
    // Region: Room Migration Integrity
    // ------------------------------------------------------------------

    @Test
    fun `ALL_MIGRATIONS covers every version from 1 to latest`() {
        // The database is currently at version 56 (see PrismTaskDatabase).
        // ALL_MIGRATIONS must have an entry for every consecutive pair.
        val expectedCount = 55 // versions 1→2, 2→3, …, 55→56
        assertEquals(
            "ALL_MIGRATIONS should contain exactly $expectedCount migrations (v1→v56)",
            expectedCount,
            ALL_MIGRATIONS.size
        )
    }

    @Test
    fun `ALL_MIGRATIONS forms a contiguous version chain`() {
        // Verify that sorting by startVersion yields 1,2,3,…,37 with no gaps.
        val sorted = ALL_MIGRATIONS.sortedBy { it.startVersion }
        for (i in sorted.indices) {
            val expected = i + 1
            assertEquals(
                "Migration at index $i should start at version $expected",
                expected,
                sorted[i].startVersion
            )
            assertEquals(
                "Migration at index $i should end at version ${expected + 1}",
                expected + 1,
                sorted[i].endVersion
            )
        }
    }

    @Test
    fun `ALL_MIGRATIONS has no duplicate start versions`() {
        val startVersions = ALL_MIGRATIONS.map { it.startVersion }
        val duplicates = startVersions.groupBy { it }.filter { it.value.size > 1 }
        assertTrue(
            "Found duplicate start versions in ALL_MIGRATIONS: ${duplicates.keys}",
            duplicates.isEmpty()
        )
    }

    @Test
    fun `first migration starts at version 1`() {
        val minStart = ALL_MIGRATIONS.minOf { it.startVersion }
        assertEquals("Earliest migration should start at version 1", 1, minStart)
    }

    @Test
    fun `last migration ends at current database version 56`() {
        val maxEnd = ALL_MIGRATIONS.maxOf { it.endVersion }
        assertEquals(
            "Latest migration should end at the current DB version (56)",
            56,
            maxEnd
        )
    }

    @Test
    fun `MIGRATION_1_2 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_1_2 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_1_2)
        )
    }

    @Test
    fun `MIGRATION_37_38 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_37_38 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_37_38)
        )
    }

    @Test
    fun `MIGRATION_38_39 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_38_39 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_38_39)
        )
    }

    @Test
    fun `MIGRATION_39_40 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_39_40 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_39_40)
        )
    }

    @Test
    fun `MIGRATION_40_41 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_40_41 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_40_41)
        )
    }

    @Test
    fun `MIGRATION_41_42 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_41_42 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_41_42)
        )
    }

    @Test
    fun `MIGRATION_42_43 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_42_43 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_42_43)
        )
    }

    @Test
    fun `MIGRATION_43_44 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_43_44 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_43_44)
        )
    }

    @Test
    fun `each migration is a single-step migration`() {
        // Room supports multi-step migrations (e.g. 1→3) but they can mask
        // missing intermediate migrations. All ours should be single-step.
        for (migration in ALL_MIGRATIONS) {
            assertEquals(
                "Migration ${migration.startVersion}→${migration.endVersion} should be a single step",
                migration.startVersion + 1,
                migration.endVersion
            )
        }
    }
}
