package com.averycorp.prismtask.sync

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Harness for two-device sync scenarios running against a real Firebase
 * Emulator Suite (Auth + Firestore), on a single Android emulator.
 *
 * **Two-process-in-one-emulator model.** Rather than coordinating two
 * AVDs on one GitHub runner (infeasible on `ubuntu-latest` memory budget),
 * we simulate "two devices" with two [FirebaseApp] instances inside the
 * same instrumentation process:
 *
 *  - **Device A** = the default [FirebaseApp] that the production
 *    `SyncService` and Hilt graph see — its `FirebaseFirestore.getInstance()`
 *    is already routed at the emulator by `PrismTaskApplication.configureFirebaseEmulator()`
 *    when `BuildConfig.USE_FIREBASE_EMULATOR == true`.
 *  - **Device B** = a named [FirebaseApp] (`"deviceB"`) owned by the
 *    harness. Its Firestore/Auth clients are routed at the same emulator
 *    but are independent of device A's cache and network state. Tests
 *    use it to stage "remote writes from another device" without going
 *    through the app's repositories.
 *
 * Both devices sign in as the same fixed test user so their writes land
 * under the same `users/{uid}/*` subtree — matching production's
 * "same Google account across two phones" topology.
 *
 * The harness does **not** own Room or Hilt; a scenario test base class
 * wraps Hilt injection of `PrismTaskDatabase`, `SyncService`, and
 * `AuthManager` around this harness.
 *
 * Usage:
 * ```
 * val harness = SyncTestHarness.createAndInit(context)
 * harness.signInBothDevicesAsSharedUser()
 * harness.writeAsDeviceB("tasks", "doc1", mapOf("title" to "from B"))
 * harness.waitFor { harness.firestoreCount("tasks") == 1 }
 * harness.cleanupFirestoreUser()
 * ```
 */
class SyncTestHarness private constructor(
    private val context: Context,
    private val deviceBApp: FirebaseApp,
) {
    /** The Hilt-visible default Firestore — what production `SyncService` uses. */
    val deviceAFirestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Device A's auth — shares the default FirebaseApp with production code. */
    val deviceAAuth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Device B's Firestore — independent client pointed at the same emulator. */
    val deviceBFirestore: FirebaseFirestore = FirebaseFirestore.getInstance(deviceBApp)

    /** Device B's auth — independent client pointed at the same emulator. */
    val deviceBAuth: FirebaseAuth = FirebaseAuth.getInstance(deviceBApp)

    /** UID of the shared test user, populated after [signInBothDevicesAsSharedUser]. */
    lateinit var userId: String
        private set

    /**
     * Sign both devices in as the same test user. Idempotent: if the
     * test account already exists in the Auth emulator (from a prior
     * run in the same emulator session), we sign in instead of creating.
     *
     * After this returns, [userId] is populated and Firestore writes
     * from either device land under `users/{userId}/*`.
     */
    suspend fun signInBothDevicesAsSharedUser(
        email: String = FIXED_EMAIL,
        password: String = FIXED_PASSWORD,
    ) {
        signInOrCreate(deviceAAuth, email, password)
        signInOrCreate(deviceBAuth, email, password)
        val uidA = requireNotNull(deviceAAuth.currentUser?.uid) { "Device A UID is null after sign-in" }
        val uidB = requireNotNull(deviceBAuth.currentUser?.uid) { "Device B UID is null after sign-in" }
        check(uidA == uidB) { "Expected shared UID across devices; got A=$uidA B=$uidB" }
        userId = uidA
    }

    /** Sign both devices out. Safe to call even if sign-in never succeeded. */
    fun signOutBothDevices() {
        runCatching { deviceAAuth.signOut() }
        runCatching { deviceBAuth.signOut() }
    }

    /**
     * Disable network on device A's Firestore client. Writes and reads
     * continue to serve from cache; with [FirebaseFirestoreSettings.isPersistenceEnabled]
     * false (see [init]) the cache is in-memory only, so offline writes
     * queue until [setDeviceAOnline].
     *
     * Device B is unaffected — that's the whole point of the named-app split.
     */
    suspend fun setDeviceAOffline() {
        deviceAFirestore.disableNetwork().await()
    }

    /** Restore device A's network connection. */
    suspend fun setDeviceAOnline() {
        deviceAFirestore.enableNetwork().await()
    }

    /**
     * Simulate another device by writing directly through device B's
     * independent Firestore client. Bypasses the app's repositories
     * and `SyncMapper`, so callers are responsible for shaping [fields]
     * to match what `SyncService.pullCollection()` expects.
     */
    suspend fun writeAsDeviceB(
        subcollection: String,
        docId: String,
        fields: Map<String, Any?>,
    ) {
        deviceBUserCollection(subcollection).document(docId).set(fields).await()
    }

    /** Delete a doc as "the other device." */
    suspend fun deleteAsDeviceB(subcollection: String, docId: String) {
        deviceBUserCollection(subcollection).document(docId).delete().await()
    }

    /**
     * Read a single Firestore doc under `users/{userId}/{subcollection}/{docId}`,
     * via device A's client (the production view).
     */
    suspend fun firestoreDoc(subcollection: String, docId: String): DocumentSnapshot =
        deviceAUserCollection(subcollection).document(docId).get().await()

    /** Count docs in `users/{userId}/{subcollection}`. */
    suspend fun firestoreCount(subcollection: String): Int =
        deviceAUserCollection(subcollection).get().await().size()

    /** List all docs in `users/{userId}/{subcollection}`. */
    suspend fun firestoreAllDocs(subcollection: String): List<DocumentSnapshot> =
        deviceAUserCollection(subcollection).get().await().documents

    /**
     * Poll [predicate] until it returns true or [timeout] elapses.
     * Used for "eventually consistent" assertions — e.g. waiting for a
     * real-time listener to apply a remote write to local Room.
     */
    suspend fun waitFor(
        timeout: Duration = DEFAULT_SYNC_TIMEOUT,
        interval: Duration = DEFAULT_POLL_INTERVAL,
        message: String = "condition",
        predicate: suspend () -> Boolean,
    ) {
        val deadlineNanos = System.nanoTime() + timeout.inWholeNanoseconds
        while (System.nanoTime() < deadlineNanos) {
            if (predicate()) return
            delay(interval)
        }
        if (predicate()) return
        fail("waitFor($message) did not converge within $timeout")
    }

    /**
     * Delete every doc under `users/{userId}/*` for the subcollections the
     * harness knows about. Run in @After to keep repeated runs from piling
     * up state in the emulator's in-memory Firestore. Safe to call before
     * sign-in is complete (no-op).
     */
    suspend fun cleanupFirestoreUser() {
        if (!::userId.isInitialized) return
        for (subcollection in KNOWN_SUBCOLLECTIONS) {
            val snap = deviceAUserCollection(subcollection).get().await()
            for (doc in snap.documents) {
                runCatching { doc.reference.delete().await() }
            }
        }
    }

    private fun deviceAUserCollection(subcollection: String) =
        deviceAFirestore.collection("users").document(userId).collection(subcollection)

    private fun deviceBUserCollection(subcollection: String) =
        deviceBFirestore.collection("users").document(userId).collection(subcollection)

    private suspend fun signInOrCreate(auth: FirebaseAuth, email: String, password: String) {
        try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (_: Exception) {
            auth.signInWithEmailAndPassword(email, password).await()
        }
    }

    companion object {
        private const val EMULATOR_HOST = "10.0.2.2"
        private const val FIRESTORE_PORT = 8080
        private const val AUTH_PORT = 9099

        /** Fixed test account. Deterministic so concurrent test runs in the
         * same emulator session share state cleanly; teardown cleans docs
         * under the UID between tests. */
        const val FIXED_EMAIL = "sync-tests@prismtask.test"
        const val FIXED_PASSWORD = "sync-tests-password"

        /** Default timeout for [waitFor]. Firestore's "eventually consistent"
         * across emulator instances is usually <2 s; 30 s is the CI-safe
         * upper bound. */
        val DEFAULT_SYNC_TIMEOUT: Duration = 30.seconds

        /** How often [waitFor] re-checks its predicate. Balances latency
         * against emulator load under concurrent CI runs. */
        val DEFAULT_POLL_INTERVAL: Duration = 200.milliseconds

        /** Subcollections the harness cleans between tests. Covers the
         * families used by Tests 7-15 and can be extended as new scenarios
         * land without breaking existing tests. */
        val KNOWN_SUBCOLLECTIONS = listOf(
            "tasks",
            "habits",
            "habit_completions",
            "projects",
            "tags",
            "task_completions",
        )

        /**
         * Build the harness. Idempotent across tests in the same JVM: the
         * named `"deviceB"` FirebaseApp is created on first call and reused
         * thereafter (FirebaseApp.initializeApp throws on duplicate names).
         *
         * Callers should gate this behind `Assume.assumeTrue(BuildConfig.USE_FIREBASE_EMULATOR)`
         * — on default debug builds the default app doesn't point at the
         * emulator and the harness would sign writes into production
         * Firestore.
         */
        fun createAndInit(context: Context): SyncTestHarness {
            // Ensure the default FirebaseApp is initialized. Production
            // `PrismTaskApplication.onCreate` already does this in
            // instrumentation tests, but invoking it from harness code
            // keeps this runnable from unit-style test entry points too.
            FirebaseApp.initializeApp(context)

            val deviceBApp = getOrCreateDeviceBApp(context)

            return SyncTestHarness(context, deviceBApp)
        }

        private fun getOrCreateDeviceBApp(context: Context): FirebaseApp {
            return try {
                FirebaseApp.getInstance("deviceB")
            } catch (_: IllegalStateException) {
                val options = FirebaseApp.getInstance().options
                val app = FirebaseApp.initializeApp(context, options, "deviceB")
                // useEmulator / firestoreSettings can only be called before
                // the first request and only once per FirebaseApp — so we
                // do it here, at creation time, and never again.
                FirebaseFirestore.getInstance(app).apply {
                    useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
                    firestoreSettings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(false)
                        .build()
                }
                FirebaseAuth.getInstance(app).useEmulator(EMULATOR_HOST, AUTH_PORT)
                app
            }
        }
    }
}

