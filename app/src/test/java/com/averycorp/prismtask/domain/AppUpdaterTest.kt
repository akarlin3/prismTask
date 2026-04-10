package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.remote.UpdateStatus
import com.averycorp.prismtask.data.remote.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdaterTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_initialStatus_isIdle() {
        // AppUpdater requires Context, so we test the enum and status logic directly
        assertEquals(UpdateStatus.IDLE, UpdateStatus.valueOf("IDLE"))
    }

    @Test
    fun test_allStatusValues_exist() {
        val expected = listOf(
            "IDLE", "CHECKING", "UPDATE_AVAILABLE", "NO_UPDATE",
            "DOWNLOADING", "READY_TO_INSTALL", "ERROR"
        )
        val actual = UpdateStatus.entries.map { it.name }
        assertEquals(expected, actual)
    }

    @Test
    fun test_statusEnum_checking() {
        assertEquals(UpdateStatus.CHECKING, UpdateStatus.valueOf("CHECKING"))
    }

    @Test
    fun test_statusEnum_updateAvailable() {
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, UpdateStatus.valueOf("UPDATE_AVAILABLE"))
    }

    @Test
    fun test_statusEnum_noUpdate() {
        assertEquals(UpdateStatus.NO_UPDATE, UpdateStatus.valueOf("NO_UPDATE"))
    }

    @Test
    fun test_statusEnum_downloading() {
        assertEquals(UpdateStatus.DOWNLOADING, UpdateStatus.valueOf("DOWNLOADING"))
    }

    @Test
    fun test_statusEnum_readyToInstall() {
        assertEquals(UpdateStatus.READY_TO_INSTALL, UpdateStatus.valueOf("READY_TO_INSTALL"))
    }

    @Test
    fun test_statusEnum_error() {
        assertEquals(UpdateStatus.ERROR, UpdateStatus.valueOf("ERROR"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_invalidStatusValue_throws() {
        UpdateStatus.valueOf("INVALID")
    }

    @Test
    fun test_statusEnum_ordinalOrder() {
        // Verify the ordinal positions match the expected UI flow
        assertTrue(UpdateStatus.IDLE.ordinal < UpdateStatus.CHECKING.ordinal)
        assertTrue(UpdateStatus.CHECKING.ordinal < UpdateStatus.UPDATE_AVAILABLE.ordinal)
        assertTrue(UpdateStatus.CHECKING.ordinal < UpdateStatus.NO_UPDATE.ordinal)
    }

    @Test
    fun test_statusCount_isSeven() {
        assertEquals(7, UpdateStatus.entries.size)
    }

    // --- Version parsing / comparison ---

    @Test
    fun test_parseVersion_stripsVPrefix() {
        val parsed = AppUpdater.parseVersion("v0.7.13")
        assertNotNull(parsed)
        assertArrayEquals(intArrayOf(0, 7, 13), parsed)
    }

    @Test
    fun test_parseVersion_withoutPrefix() {
        val parsed = AppUpdater.parseVersion("1.2.3")
        assertNotNull(parsed)
        assertArrayEquals(intArrayOf(1, 2, 3), parsed)
    }

    @Test
    fun test_parseVersion_buildSuffixIgnored() {
        // The "-build.N" suffix exists only on CI-rebuild tags, never in
        // BuildConfig.VERSION_NAME, so we strip it and compare the base.
        val parsed = AppUpdater.parseVersion("v0.7.13-build.2")
        assertNotNull(parsed)
        assertArrayEquals(intArrayOf(0, 7, 13), parsed)
    }

    @Test
    fun test_parseVersion_partialVersion() {
        val parsed = AppUpdater.parseVersion("v1.2")
        assertNotNull(parsed)
        assertArrayEquals(intArrayOf(1, 2, 0), parsed)
    }

    @Test
    fun test_parseVersion_nullOrBlank() {
        assertNull(AppUpdater.parseVersion(null))
        assertNull(AppUpdater.parseVersion(""))
        assertNull(AppUpdater.parseVersion("   "))
    }

    @Test
    fun test_parseVersion_garbageReturnsNull() {
        assertNull(AppUpdater.parseVersion("not-a-version"))
        assertNull(AppUpdater.parseVersion("v.a.b.c"))
    }

    @Test
    fun test_isRemoteNewer_patchBump() {
        assertTrue(AppUpdater.isRemoteNewer("v0.7.14", "0.7.13"))
    }

    @Test
    fun test_isRemoteNewer_minorBump() {
        assertTrue(AppUpdater.isRemoteNewer("v0.8.0", "0.7.13"))
    }

    @Test
    fun test_isRemoteNewer_majorBump() {
        assertTrue(AppUpdater.isRemoteNewer("v1.0.0", "0.99.99"))
    }

    @Test
    fun test_isRemoteNewer_sameVersionReturnsFalse() {
        assertFalse(AppUpdater.isRemoteNewer("v0.7.13", "0.7.13"))
    }

    @Test
    fun test_isRemoteNewer_olderRemoteReturnsFalse() {
        assertFalse(AppUpdater.isRemoteNewer("v0.7.12", "0.7.13"))
    }

    @Test
    fun test_isRemoteNewer_rebuildOfSameVersionReturnsFalse() {
        // A CI rebuild (v0.7.13-build.2) of the already-installed version
        // must NOT trigger an update prompt, or the user would be stuck
        // in a forever-update loop.
        assertFalse(AppUpdater.isRemoteNewer("v0.7.13-build.2", "0.7.13"))
    }

    @Test
    fun test_isRemoteNewer_unparseableReturnsFalse() {
        // Defensive: if we can't parse, never claim an update is available.
        assertFalse(AppUpdater.isRemoteNewer(null, "0.7.13"))
        assertFalse(AppUpdater.isRemoteNewer("garbage", "0.7.13"))
        assertFalse(AppUpdater.isRemoteNewer("v0.7.14", null))
    }
}
