package com.averycorp.prismtask.ui.components.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.preferences.AuthTokenPreferences
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.remote.sync.BackendSyncService
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.averycorp.prismtask.data.remote.sync.SyncErrorSample
import com.averycorp.prismtask.data.remote.sync.SyncLogEntry
import com.averycorp.prismtask.data.remote.sync.SyncStateRepository
import com.averycorp.prismtask.ui.components.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncIndicatorViewModel
@Inject
constructor(
    internal val syncStateRepository: SyncStateRepository,
    private val syncService: SyncService,
    private val backendSyncService: BackendSyncService,
    internal val logger: PrismSyncLogger,
    private val authTokenPreferences: AuthTokenPreferences
) : ViewModel() {

    private val showRecentSuccess: StateFlow<Boolean> = syncStateRepository.lastSuccessAt
        .filterNotNull()
        .flatMapLatest {
            flow<Boolean> {
                emit(true)
                kotlinx.coroutines.delay(FADE_WINDOW_MS)
                emit(false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<SyncState> = combine(
        syncStateRepository.isSignedIn,
        syncStateRepository.isOnline,
        syncStateRepository.isSyncing,
        syncStateRepository.pendingCount,
        syncStateRepository.recentErrors,
        syncStateRepository.lastSuccessAt,
        showRecentSuccess
    ) { values ->
        val signedIn = values[0] as Boolean
        val online = values[1] as Boolean
        val syncing = values[2] as Boolean
        val pending = values[3] as Int
        val errors = values[4] as List<SyncErrorSample>
        val lastSuccess = values[5] as Long?
        val showSuccess = values[6] as Boolean

        val latestError = errors.firstOrNull()

        when {
            !signedIn -> SyncState.NotSignedIn
            syncing -> SyncState.Syncing
            latestError != null &&
                (lastSuccess == null || latestError.timestampMs > lastSuccess) ->
                SyncState.Error(latestError.message)
            !online && pending > 0 -> SyncState.Offline(pending)
            pending > 0 -> SyncState.Pending(pending)
            showSuccess -> SyncState.Synced
            else -> SyncState.NotSignedIn // silent fallback — renders nothing
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SyncState.NotSignedIn)

    val recentErrors: StateFlow<List<SyncErrorSample>> = syncStateRepository.recentErrors

    val pendingCount: StateFlow<Int> = syncStateRepository.pendingCount

    val lastSyncAt: StateFlow<Long> = syncStateRepository.lastSyncAt

    val listenersActive: StateFlow<Boolean> = syncStateRepository.listenersActive

    val listenerSnapshots: StateFlow<Map<String, Long>> = syncStateRepository.listenerSnapshots

    val logEntries: StateFlow<List<SyncLogEntry>> = logger.entries

    private val _pendingEntries = MutableStateFlow<List<SyncMetadataEntity>>(emptyList())
    val pendingEntries: StateFlow<List<SyncMetadataEntity>> = _pendingEntries.asStateFlow()

    private val _backendTokenExpiresAt = MutableStateFlow<Long?>(null)
    val backendTokenExpiresAt: StateFlow<Long?> = _backendTokenExpiresAt.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        viewModelScope.launch {
            syncStateRepository.pendingEntries.collect { _pendingEntries.value = it }
        }
        viewModelScope.launch {
            authTokenPreferences.accessTokenFlow.collect { token ->
                _backendTokenExpiresAt.value = token?.let(::decodeJwtExpMillis)
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            runCatching { syncService.fullSync(trigger = "force") }
            runCatching { backendSyncService.fullSync(trigger = "force") }
        }
    }

    fun clearOfflineQueue() {
        viewModelScope.launch {
            syncStateRepository.clearOfflineQueue()
            _actionMessage.value = "Offline queue cleared."
        }
    }

    fun resetSyncState() {
        viewModelScope.launch {
            syncStateRepository.resetSyncState()
            _actionMessage.value = "Sync state reset."
        }
    }

    fun dismissErrors() {
        syncStateRepository.clearErrors()
    }

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    companion object {
        const val FADE_WINDOW_MS: Long = 3_000L
    }
}

/**
 * Decodes the `exp` claim (seconds since epoch) from a base64url-encoded JWT
 * payload and returns it as a millisecond timestamp. Returns null if the token
 * isn't a parseable JWT — the debug panel just shows "unknown" in that case.
 */
internal fun decodeJwtExpMillis(token: String): Long? {
    val parts = token.split('.')
    if (parts.size < 2) return null
    return try {
        val payload = parts[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val bytes = android.util.Base64.decode(
            padded,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val json = String(bytes)
        val match = Regex("\"exp\"\\s*:\\s*(\\d+)").find(json) ?: return null
        match.groupValues[1].toLong() * 1000L
    } catch (_: Exception) {
        null
    }
}
