package com.averycorp.prismtask.data.remote

import androidx.datastore.preferences.core.edit
import com.averycorp.prismtask.data.remote.sync.PreferenceSyncSerialization
import com.averycorp.prismtask.data.remote.sync.PreferenceSyncSpec
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.averycorp.prismtask.data.remote.sync.SyncDeviceIdProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic Firestore sync for any number of DataStore preference files.
 *
 * Each registered [PreferenceSyncSpec] is pushed to and pulled from
 * `/users/{uid}/prefs/{docName}`. Conflict resolution is last-write-wins at
 * the document level, with an internal `__pref_device_id` field used to
 * suppress self-echoes.
 *
 * This replaces what would otherwise be one hand-written sync service per
 * preference file. The bespoke [ThemePreferencesSyncService] and
 * [SortPreferencesSyncService] remain because they do extra work
 * ([SortPreferencesSyncService] translates project cloud IDs;
 * [ThemePreferencesSyncService] predates this service) — new preference
 * files plug in here instead.
 *
 * Lifecycle (mirrors the existing per-file services):
 * - [startPushObserver] — called from MainActivity.onCreate(); registers the
 *   per-spec debounced push observer. No-ops until signed in.
 * - [ensurePullListener] — called from MainActivity.onCreate(); registers
 *   Firestore pull listeners for every spec when the user is already signed
 *   in at cold start.
 * - [startAfterSignIn] — called from AuthViewModel after interactive sign-in;
 *   registers pull listeners AND force-pushes local state for every spec.
 * - [stopAfterSignOut] — removes listeners. Local DataStore is not cleared.
 */
@Singleton
class GenericPreferenceSyncService @Inject constructor(
    private val specs: Set<@JvmSuppressWildcards PreferenceSyncSpec>,
    private val authManager: AuthManager,
    private val deviceIdProvider: SyncDeviceIdProvider,
    private val logger: PrismSyncLogger
) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Firestore listeners keyed by docName. Non-null iff the pull listener is active. */
    private val listeners = ConcurrentHashMap<String, ListenerRegistration>()

    /** Fingerprint of the last-pushed (or just-pulled) state per docName. */
    private val lastSyncedFingerprint = ConcurrentHashMap<String, Int>()

    /** Registers the reactive push observer for every registered spec. */
    fun startPushObserver() {
        specs.forEach { spec ->
            scope.launch {
                spec.dataStore.data
                    .debounce(500L)
                    .collect {
                        if (authManager.userId == null) return@collect
                        pushIfChanged(spec)
                    }
            }
        }
    }

    /** Registers Firestore pull listeners for every spec when already signed in. */
    fun ensurePullListener() {
        if (authManager.userId == null) return
        specs.forEach { spec -> ensureListener(spec) }
    }

    /** Called from AuthViewModel after a successful sign-in. */
    fun startAfterSignIn() {
        specs.forEach { spec -> ensureListener(spec) }
        scope.launch {
            specs.forEach { spec -> pushNow(spec, force = true) }
        }
    }

    /** Called from AuthViewModel on sign-out. Does NOT clear local DataStore. */
    fun stopAfterSignOut() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
        lastSyncedFingerprint.clear()
    }

    private fun ensureListener(spec: PreferenceSyncSpec) {
        if (listeners.containsKey(spec.firestoreDocName)) return
        val uid = authManager.userId ?: return
        val ref = firestore.collection("users").document(uid)
            .collection("prefs").document(spec.firestoreDocName)
        val reg = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logger.warn(
                    operation = "[PrefsSync] listener.error",
                    detail = "doc=${spec.firestoreDocName}",
                    throwable = error
                )
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            scope.launch { applyRemote(spec, data) }
        }
        listeners[spec.firestoreDocName] = reg
    }

    private suspend fun applyRemote(spec: PreferenceSyncSpec, data: Map<String, Any>) {
        val remoteDevice = data[PreferenceSyncSerialization.META_DEVICE_ID] as? String
        val thisDevice = deviceIdProvider.get()
        if (remoteDevice == thisDevice) return // self-echo

        var appliedCount = 0
        spec.dataStore.edit { mutablePrefs ->
            appliedCount = PreferenceSyncSerialization.applyRemote(
                out = mutablePrefs,
                remote = data,
                excludeKeys = spec.excludeKeys
            )
        }
        if (appliedCount > 0) {
            // Record post-pull fingerprint so the upcoming push observer emission
            // (triggered by our own DataStore edit above) doesn't treat it as a
            // local user change and immediately bounce the same payload back.
            val post = spec.dataStore.data.first()
            lastSyncedFingerprint[spec.firestoreDocName] =
                PreferenceSyncSerialization.fingerprint(post, spec.excludeKeys)
            logger.info(
                operation = "[PrefsSync] pull",
                detail = "doc=${spec.firestoreDocName} | applied=$appliedCount"
            )
        }
    }

    private suspend fun pushIfChanged(spec: PreferenceSyncSpec) {
        val prefs = spec.dataStore.data.first()
        val fp = PreferenceSyncSerialization.fingerprint(prefs, spec.excludeKeys)
        if (lastSyncedFingerprint[spec.firestoreDocName] == fp) return
        pushNow(spec, force = false)
    }

    private suspend fun pushNow(spec: PreferenceSyncSpec, force: Boolean) {
        val uid = authManager.userId ?: return
        val prefs = spec.dataStore.data.first()
        val fp = PreferenceSyncSerialization.fingerprint(prefs, spec.excludeKeys)
        if (!force && lastSyncedFingerprint[spec.firestoreDocName] == fp) return

        val payload = PreferenceSyncSerialization.buildPayload(
            prefs = prefs,
            excludeKeys = spec.excludeKeys,
            deviceId = deviceIdProvider.get(),
            nowMs = System.currentTimeMillis()
        ) ?: return

        val ref = firestore.collection("users").document(uid)
            .collection("prefs").document(spec.firestoreDocName)
        try {
            // Merge semantics: concurrent offline writes from a sibling device
            // to other keys in the same doc survive instead of getting
            // clobbered. Trade-off — local key deletions don't propagate,
            // which is tolerable because PrismTask preference code almost
            // always writes a default value rather than calling prefs.remove().
            ref.set(payload, SetOptions.merge()).await()
            lastSyncedFingerprint[spec.firestoreDocName] = fp
            logger.info(
                operation = "[PrefsSync] push",
                detail = "doc=${spec.firestoreDocName} | keys=${payload.size - 3}"
            )
        } catch (e: Exception) {
            logger.error(
                operation = "[PrefsSync] push",
                detail = "doc=${spec.firestoreDocName} | status=failed",
                throwable = e
            )
        }
    }
}
