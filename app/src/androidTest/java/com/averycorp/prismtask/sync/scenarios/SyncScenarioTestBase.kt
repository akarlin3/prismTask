package com.averycorp.prismtask.sync.scenarios

import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.sync.SyncTestHarness
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Base for sync-scenario instrumentation tests (PR2 of 3). Pairs
 * SyncTestHarness (Firebase emulator harness) with a Hilt-injected
 * PrismTaskDatabase + SyncService + repositories so tests can drive the
 * real production sync pipeline end-to-end against a running Firebase
 * Emulator Suite.
 *
 * Lifecycle:
 *  - @Before: inject the Hilt graph, build harness, sign both devices
 *    in as the shared test user, clear any residual Firestore docs
 *    from prior runs (the emulator persists state across tests in the
 *    same workflow run).
 *  - Test body: use `taskRepository` / `habitRepository` / `projectRepository`
 *    for local mutations (they also mark rows dirty via syncTracker);
 *    call `syncService.pushLocalChanges()` / `pullRemoteChanges()`
 *    explicitly — subclasses should NOT rely on real-time listeners,
 *    which are timing-sensitive and flaky in CI.
 *  - @After: cleanup Firestore + clear Room so the next test in the
 *    process starts from a known state.
 *
 * Gated by `assumeTrue(BuildConfig.USE_FIREBASE_EMULATOR)` — these
 * tests only run under `.github/workflows/android-integration.yml`.
 */
@HiltAndroidTest
abstract class SyncScenarioTestBase {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: PrismTaskDatabase

    @Inject
    lateinit var syncService: SyncService

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var projectRepository: ProjectRepository

    protected lateinit var harness: SyncTestHarness

    @Before
    fun scenarioSetUp() {
        assumeTrue(
            "Sync scenario tests require USE_FIREBASE_EMULATOR=true — " +
                "skipped on default debug builds.",
            BuildConfig.USE_FIREBASE_EMULATOR
        )
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        harness = SyncTestHarness.createAndInit(context)
        runBlocking {
            harness.signInBothDevicesAsSharedUser()
            // Cleanup any residue from previous scenarios in the same
            // emulator session; idempotent when nothing is there.
            harness.cleanupFirestoreUser()
        }
        // Clear the in-memory Room DB on each test start. `@Inject` gives
        // a fresh DB per test process, but within a single process multiple
        // scenarios share it.
        database.clearAllTables()
    }

    @After
    fun scenarioTearDown() {
        if (::harness.isInitialized) {
            runBlocking {
                runCatching { harness.cleanupFirestoreUser() }
            }
            harness.signOutBothDevices()
        }
        // Clear DB too — prevents cross-test bleed when the same process
        // runs multiple @HiltAndroidTest classes.
        runCatching { database.clearAllTables() }
    }

    /**
     * Helper: assert device A has an authenticated user. Most
     * scenarios assume this; fail loudly rather than silently no-op
     * inside SyncService.
     */
    protected fun requireSignedIn() {
        checkNotNull(authManager.userId) {
            "Test expected a signed-in user but AuthManager.userId is null. " +
                "signInBothDevicesAsSharedUser() must have run in @Before."
        }
    }
}
