package com.averycorp.prismtask.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.BuildConfig
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
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
 *  - Device A and device B are wrapped in independent FirebaseApp instances
 *    (orthogonality is verified structurally — see the long comment in
 *    `harness_deviceAAndDeviceBAreStructurallyOrthogonal` for why we no
 *    longer toggle network state to prove this).
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
    fun harness_deviceAAndDeviceBAreStructurallyOrthogonal() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            // Original assertion: "device B writes still land while device A
            // is offline." Implementation toggled device A's network via
            // `disableNetwork()`/`enableNetwork()`. Each toggle re-initialises
            // the production-side Firestore client, which calls
            // `ConnectivityManager.registerDefaultNetworkCallback` on every
            // re-init. Across the full connected-tests suite (~424 tests
            // sharing one process) the callbacks accumulated and tripped
            // Android's per-UID quota (~100), failing this test on PRs
            // #1015 and #1021 with `ConnectivityManager$TooManyRequestsException`.
            //
            // Orthogonality is a structural property of the harness — the
            // two Firestore clients are wrapped in distinct FirebaseApp
            // instances and use independent transports — so verify it
            // structurally instead of by toggling network state. The
            // separate test `harness_writeAsDeviceBLandsInFirestoreVisibleToDeviceA`
            // continues to prove device B's writes actually succeed against
            // the same emulator backend.
            assertEquals(
                "Device A's Firestore must come from the default FirebaseApp",
                FirebaseApp.DEFAULT_APP_NAME,
                harness.deviceAFirestore.app.name
            )
            assertEquals(
                "Device B's Firestore must come from the named \"deviceB\" FirebaseApp",
                "deviceB",
                harness.deviceBFirestore.app.name
            )
            assertTrue(
                "Device A and device B must be distinct Firestore client instances",
                harness.deviceAFirestore !== harness.deviceBFirestore
            )
            assertTrue(
                "Device A and device B must back distinct FirebaseApp instances",
                harness.deviceAFirestore.app !== harness.deviceBFirestore.app
            )
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
