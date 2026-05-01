package com.averycorp.prismtask.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Smoke tests for [SyncTestHarness] itself — not for real sync scenarios.
 * These guard the harness primitives against regressions so PR2's scenario
 * tests have a stable foundation:
 *
 *  - Sign-in produces a stable UID shared across the two device clients.
 *  - Writes through device B land in Firestore and are visible to device A.
 *  - Device A's offline toggle does not affect device B's writes.
 *  - [SyncTestHarness.waitFor] converges and times out correctly.
 *  - [SyncTestHarness.cleanupFirestoreUser] actually empties the user's
 *    subcollections between tests.
 *
 * All tests are skipped on default debug builds (via `assumeTrue`) — they
 * require the Firebase Emulator Suite from
 * `.github/workflows/android-integration.yml` (PR #635).
 */
@RunWith(AndroidJUnit4::class)
class SyncTestHarnessSmokeTest {
    private lateinit var harness: SyncTestHarness

    @Before
    fun setUp() {
        assumeTrue(
            "SyncTestHarness smoke tests require USE_FIREBASE_EMULATOR=true — " +
                "skipped on default debug builds.",
            BuildConfig.USE_FIREBASE_EMULATOR
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        harness = SyncTestHarness.createAndInit(context)
        runBlocking {
            harness.signInBothDevicesAsSharedUser()
            // Clean any residue from prior runs sharing the same fixed
            // test user. Idempotent when nothing is there.
            harness.cleanupFirestoreUser()
        }
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            runBlocking {
                runCatching { harness.cleanupFirestoreUser() }
            }
            harness.signOutBothDevices()
            // We deliberately do NOT terminate device B's Firestore here:
            // each `terminate()` voids the per-app Firestore singleton, so
            // the next test's `createAndInit` resolves a fresh client which
            // registers its own ConnectivityManager callback. Android caps
            // those at ~100 per UID, and a long suite would otherwise trip
            // `ConnectivityManager$TooManyRequestsException` mid-test (the
            // 396/422 failure that motivated this comment). The harness
            // caches the device-B client process-wide instead — see
            // SyncTestHarness.getOrCacheDeviceBFirestore.
        }
    }

    @Test
    fun harness_signsInBothDevicesAsSameUid() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            // Both devices authed as the same fixed user → same UID.
            val uidA = harness.deviceAAuth.currentUser?.uid
            val uidB = harness.deviceBAuth.currentUser?.uid
            assertNotNull("Device A must have a UID after sign-in", uidA)
            assertNotNull("Device B must have a UID after sign-in", uidB)
            assertEquals("Devices must share the same UID", uidA, uidB)
            assertEquals("harness.userId must match FirebaseAuth UID", uidA, harness.userId)
        }
    }

    @Test
    fun harness_writeAsDeviceBLandsInFirestoreVisibleToDeviceA() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            val docId = "smoke-write-${System.currentTimeMillis()}"
            harness.writeAsDeviceB(
                subcollection = "tasks",
                docId = docId,
                fields = mapOf(
                    "title" to "probe-from-device-b",
                    "ts" to System.currentTimeMillis()
                )
            )
            // Read through device A's client — proves both devices see the
            // same Firestore emulator backend.
            val snap = harness.firestoreDoc("tasks", docId)
            assertTrue("Doc should exist in Firestore", snap.exists())
            assertEquals("probe-from-device-b", snap.getString("title"))
            assertNotNull("ts field should round-trip", snap.getLong("ts"))
        }
    }

    @Test
    fun harness_deviceAOfflineToggleDoesNotBlockDeviceBWrites() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            harness.setDeviceAOffline()
            try {
                val docId = "offline-orthogonality-${System.currentTimeMillis()}"
                harness.writeAsDeviceB(
                    subcollection = "tasks",
                    docId = docId,
                    fields = mapOf("title" to "written-while-A-offline")
                )
                // Device B's write must have landed despite device A being
                // offline — that's what the separate FirebaseApp buys us.
                // We read through device B (device A can't see it until
                // back online, by design).
                val snap = harness.deviceBFirestore
                    .collection("users")
                    .document(harness.userId)
                    .collection("tasks")
                    .document(docId)
                    .get()
                    .await()
                assertTrue("Device B write must succeed with A offline", snap.exists())
                assertEquals("written-while-A-offline", snap.getString("title"))
            } finally {
                harness.setDeviceAOnline()
            }
        }
    }

    @Test
    fun harness_waitForReturnsAsSoonAsPredicateIsTrue() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            var counter = 0
            val startedAt = System.nanoTime()
            harness.waitFor(
                timeout = 5.seconds,
                interval = 50.milliseconds,
                message = "counter reaches 3"
            ) {
                counter++
                counter >= 3
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue(
                "waitFor should return well under its 5 s timeout (took ${elapsedMs}ms)",
                elapsedMs < 2_000
            )
            assertTrue("Predicate must have been polled at least 3 times", counter >= 3)
        }
    }

    @Test
    fun harness_waitForFailsOnTimeoutWhenPredicateNeverTrue() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            var caught = false
            try {
                harness.waitFor(
                    timeout = 400.milliseconds,
                    interval = 50.milliseconds,
                    message = "unreachable"
                ) { false }
            } catch (e: AssertionError) {
                caught = true
                assertTrue(
                    "Timeout message should name the condition, got: ${e.message}",
                    e.message?.contains("unreachable") == true
                )
            }
            if (!caught) fail("waitFor should have raised AssertionError on timeout")
        }
    }

    @Test
    fun harness_cleanupFirestoreUserRemovesAllWrittenDocs() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            harness.writeAsDeviceB("tasks", "a", mapOf("title" to "a"))
            harness.writeAsDeviceB("tasks", "b", mapOf("title" to "b"))
            harness.writeAsDeviceB("habits", "h1", mapOf("name" to "h1"))
            assertEquals(2, harness.firestoreCount("tasks"))
            assertEquals(1, harness.firestoreCount("habits"))

            harness.cleanupFirestoreUser()

            assertEquals(0, harness.firestoreCount("tasks"))
            assertEquals(0, harness.firestoreCount("habits"))
        }
    }

    companion object {
        // Generous per-test ceiling. The @Before sign-in + cleanup is the
        // slowest part against a cold emulator (~2-3 s); individual assertions
        // should run in hundreds of ms.
        private val TEST_TIMEOUT = 45.seconds
    }
}
