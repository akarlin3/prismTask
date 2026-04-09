package com.averycorp.averytask.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies the persistence contract of [SortPreferences] using a real
 * [DataStore] backed by a temporary file. Uses [PreferenceDataStoreFactory]
 * so the tests run as pure JVM unit tests without needing an Android Context
 * or Robolectric.
 */
class SortPreferencesTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sortPreferences: SortPreferences

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        // Let DataStore itself create the file — passing a pre-created file via
        // tmpFolder.newFile() would leave an empty file that DataStore then
        // tries to parse as proto and rejects.
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmpFolder.root, "sort_prefs_test.preferences_pb") }
        )
        sortPreferences = SortPreferences(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun getSortMode_returnsDefault_whenNoPreferenceExists() = runTest {
        assertEquals(
            SortPreferences.SortModes.DEFAULT,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.TASK_LIST)
        )
        assertEquals(
            SortPreferences.SortModes.DEFAULT,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.TODAY)
        )
        assertEquals(
            SortPreferences.SortModes.DEFAULT,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.ARCHIVE)
        )
        // And the "has it been set" helper should report null.
        assertNull(sortPreferences.getSortModeOrNull(SortPreferences.ScreenKeys.TASK_LIST))
    }

    @Test
    fun setSortMode_persistsAcrossMultipleKeysIndependently() = runTest {
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.TASK_LIST, SortPreferences.SortModes.PRIORITY)
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.TODAY, SortPreferences.SortModes.URGENCY)
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.WEEK_VIEW, SortPreferences.SortModes.ALPHABETICAL)
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.MONTH_VIEW, SortPreferences.SortModes.DATE_CREATED)
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.TIMELINE, SortPreferences.SortModes.CUSTOM)
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.ARCHIVE, SortPreferences.SortModes.DUE_DATE)

        assertEquals(
            SortPreferences.SortModes.PRIORITY,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.TASK_LIST)
        )
        assertEquals(
            SortPreferences.SortModes.URGENCY,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.TODAY)
        )
        assertEquals(
            SortPreferences.SortModes.ALPHABETICAL,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.WEEK_VIEW)
        )
        assertEquals(
            SortPreferences.SortModes.DATE_CREATED,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.MONTH_VIEW)
        )
        assertEquals(
            SortPreferences.SortModes.CUSTOM,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.TIMELINE)
        )
        assertEquals(
            SortPreferences.SortModes.DUE_DATE,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.ARCHIVE)
        )
    }

    @Test
    fun setSortMode_overwritesPreviousValueForSameKey() = runTest {
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.TASK_LIST, SortPreferences.SortModes.URGENCY)
        sortPreferences.setSortMode(SortPreferences.ScreenKeys.TASK_LIST, SortPreferences.SortModes.ALPHABETICAL)

        assertEquals(
            SortPreferences.SortModes.ALPHABETICAL,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.TASK_LIST)
        )
    }

    @Test
    fun projectKey_isStableAndIsolatesPerProject() = runTest {
        val projectA = SortPreferences.ScreenKeys.project(1L)
        val projectB = SortPreferences.ScreenKeys.project(42L)

        assertEquals("sort_project_1", projectA)
        assertEquals("sort_project_42", projectB)
        assertNotEquals(projectA, projectB)

        sortPreferences.setSortMode(projectA, SortPreferences.SortModes.PRIORITY)
        sortPreferences.setSortMode(projectB, SortPreferences.SortModes.ALPHABETICAL)

        assertEquals(
            SortPreferences.SortModes.PRIORITY,
            sortPreferences.getSortMode(projectA)
        )
        assertEquals(
            SortPreferences.SortModes.ALPHABETICAL,
            sortPreferences.getSortMode(projectB)
        )
        // Untouched project still yields the default.
        assertEquals(
            SortPreferences.SortModes.DEFAULT,
            sortPreferences.getSortMode(SortPreferences.ScreenKeys.project(999L))
        )
    }

    @Test
    fun observeSortMode_emitsUpdatesReactively() = runTest {
        // Initial value = default.
        assertEquals(
            SortPreferences.SortModes.DEFAULT,
            sortPreferences.observeSortMode(SortPreferences.ScreenKeys.TASK_LIST).first()
        )

        sortPreferences.setSortMode(SortPreferences.ScreenKeys.TASK_LIST, SortPreferences.SortModes.URGENCY)

        assertEquals(
            SortPreferences.SortModes.URGENCY,
            sortPreferences.observeSortMode(SortPreferences.ScreenKeys.TASK_LIST).first()
        )
    }

    @Test
    fun sortDirection_defaultIsTypeAware() = runTest {
        // Descending by default for ranking modes.
        assertEquals(
            SortDirection.DESCENDING,
            sortPreferences.getSortDirection(
                SortPreferences.ScreenKeys.TASK_LIST,
                SortPreferences.SortModes.PRIORITY
            )
        )
        assertEquals(
            SortDirection.DESCENDING,
            sortPreferences.getSortDirection(
                SortPreferences.ScreenKeys.TASK_LIST,
                SortPreferences.SortModes.URGENCY
            )
        )
        // Ascending for ordered modes.
        assertEquals(
            SortDirection.ASCENDING,
            sortPreferences.getSortDirection(
                SortPreferences.ScreenKeys.TASK_LIST,
                SortPreferences.SortModes.DUE_DATE
            )
        )
        assertEquals(
            SortDirection.ASCENDING,
            sortPreferences.getSortDirection(
                SortPreferences.ScreenKeys.TASK_LIST,
                SortPreferences.SortModes.ALPHABETICAL
            )
        )

        // Explicit override wins over the type-aware default.
        sortPreferences.setSortDirection(
            SortPreferences.ScreenKeys.TASK_LIST,
            SortDirection.ASCENDING
        )
        assertEquals(
            SortDirection.ASCENDING,
            sortPreferences.getSortDirection(
                SortPreferences.ScreenKeys.TASK_LIST,
                SortPreferences.SortModes.PRIORITY
            )
        )
    }
}
