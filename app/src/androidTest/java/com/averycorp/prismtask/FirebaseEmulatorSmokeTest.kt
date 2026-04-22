package com.averycorp.prismtask

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that verifies the Firebase Emulator Suite integration wired
 * in `.github/workflows/android-integration.yml` and via the
 * `USE_FIREBASE_EMULATOR` env-var hook in `app/build.gradle.kts` actually
 * reaches the running emulator end-to-end: sign in with the Auth emulator,
 * write a doc, read it back, assert content.
 *
 * Skipped (via JUnit `Assume.assumeTrue`) on builds where
 * `BuildConfig.USE_FIREBASE_EMULATOR` is false — i.e. any normal debug
 * build. The integration-CI job sets the env var at build time so the
 * BuildConfig flag flips to true only for that run; local developers
 * wanting to exercise this test should either flip the flag in
 * `build.gradle.kts` (see `docs/FIREBASE_EMULATOR.md`) or set
 * `USE_FIREBASE_EMULATOR=true` in the shell before invoking gradle.
 *
 * The broader async sync tests (healer, backfiller) remain as
 * in-process harnesses with `SimulatedFirestore` — those don't need a
 * real Firestore to exercise their logic, so they run in every build.
 * This test's job is specifically to prove the real-SDK round trip
 * works, protecting us against a silent regression where Firebase /
 * emulator client behavior diverges from our assumptions.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorSmokeTest {
    @Before
    fun setUp() {
        assumeTrue(
            "Firebase emulator smoke test requires USE_FIREBASE_EMULATOR=true — " +
                "skipped on default debug builds.",
            BuildConfig.USE_FIREBASE_EMULATOR
        )
        // Auth manager and other SDK instances may have been initialized by
        // prior tests in the same process. The `useEmulator` calls below
        // throw if the SDK already ran a sign-in / query, so guard with
        // a try/catch and log — if a prior test already routed the SDK
        // at the emulator we're fine.
        FirebaseApp.initializeApp(
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        )
        try {
            FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings
                    .Builder()
                    .setPersistenceEnabled(false)
                    .build()
        } catch (_: IllegalStateException) {
            // Already routed by a prior test — fine.
        }
        try {
            FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, AUTH_PORT)
        } catch (_: IllegalStateException) {
            // Already routed — fine.
        }
    }

    @Test
    fun emulator_roundTrip_signInWriteReadDelete() = runTest {
        withTimeout(TEST_TIMEOUT_MS) {
            val auth = FirebaseAuth.getInstance()
            // Auth emulator accepts any email/password and creates the
            // account on first use. Deterministic email keeps test reruns
            // from piling up distinct accounts.
            val email = "smoke-${System.currentTimeMillis()}@prismtask.test"
            val password = "smoke-password"
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
            } catch (_: Exception) {
                auth.signInWithEmailAndPassword(email, password).await()
            }
            val uid = auth.currentUser?.uid
            assertNotNull("Sign-in must produce a non-null UID", uid)

            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("users")
                .document(uid!!)
                .collection("smoke_test")
                .document("round-trip")

            val payload = mapOf(
                "probe" to "hello-emulator",
                "ts" to System.currentTimeMillis()
            )
            doc.set(payload).await()

            val snapshot = doc.get().await()
            assertEquals("hello-emulator", snapshot.getString("probe"))
            assertNotNull(snapshot.getLong("ts"))

            // Clean up so repeated runs don't accumulate.
            doc.delete().await()
            auth.signOut()
        }
    }

    companion object {
        // Android emulator (AVD) reaches the host loopback via 10.0.2.2;
        // PrismTaskApplication detects AVD via Build.FINGERPRINT at app
        // startup, but this test doesn't go through that path, so hardcode
        // the AVD-facing address directly.
        private const val EMULATOR_HOST = "10.0.2.2"
        private const val FIRESTORE_PORT = 8080
        private const val AUTH_PORT = 9099
        private const val TEST_TIMEOUT_MS = 30_000L
    }
}
