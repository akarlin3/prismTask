package com.averycorp.prismtask.data.calendar

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarInfo(
    val id: String,
    val name: String,
    val color: String,
    val isPrimary: Boolean
)

@Singleton
class CalendarManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isCalendarConnected = MutableStateFlow(false)
    val isCalendarConnected: StateFlow<Boolean> = _isCalendarConnected.asStateFlow()

    private val _connectedAccountEmail = MutableStateFlow<String?>(null)
    val connectedAccountEmail: StateFlow<String?> = _connectedAccountEmail.asStateFlow()

    private var cachedCalendars: List<CalendarInfo>? = null
    private var cacheTimestamp: Long = 0
    private val cacheDurationMs = 30 * 60 * 1000L // 30 minutes

    init {
        // Check if calendar is already connected from existing Google account
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && account.grantedScopes.any {
                it.scopeUri == CalendarScopes.CALENDAR
            }) {
            _isCalendarConnected.value = true
            _connectedAccountEmail.value = account.email
        }
    }

    /**
     * Connects to Google Calendar by checking if the calendar scope is already
     * granted on the signed-in Google account. If the scope is present, the
     * connection succeeds. If not, returns a failure indicating that the caller
     * needs to trigger re-consent with the calendar scope.
     */
    suspend fun connectCalendar(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext Result.failure(Exception("Not signed in to Google. Please sign in first."))

            val hasCalendarScope = account.grantedScopes.any {
                it.scopeUri == CalendarScopes.CALENDAR
            }

            if (!hasCalendarScope) {
                return@withContext Result.failure(
                    CalendarScopeRequiredException("Calendar scope not granted. Re-consent required.")
                )
            }

            _isCalendarConnected.value = true
            _connectedAccountEmail.value = account.email

            // Pre-fetch calendars on connect
            refreshCalendarCache()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect calendar", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnects from Google Calendar by clearing local state.
     * Does not revoke the Google account — just disables calendar features.
     */
    suspend fun disconnectCalendar() {
        _isCalendarConnected.value = false
        _connectedAccountEmail.value = null
        cachedCalendars = null
        cacheTimestamp = 0
    }

    /**
     * Returns a configured Google Calendar API service instance using the
     * authenticated user's credential. Returns null if not signed in.
     */
    fun getCalendarService(): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(CalendarScopes.CALENDAR)
        )
        credential.selectedAccount = account.account
        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("AveryTask")
            .build()
    }

    /**
     * Queries the user's calendar list from Google Calendar API.
     * Results are cached for 30 minutes.
     */
    suspend fun getUserCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedCalendars
        if (cached != null && (now - cacheTimestamp) < cacheDurationMs) {
            return@withContext cached
        }

        try {
            val service = getCalendarService()
                ?: return@withContext emptyList()

            val calendarList = service.calendarList().list()
                .setMinAccessRole("writer")
                .execute()

            val calendars = calendarList.items?.map { entry ->
                CalendarInfo(
                    id = entry.id,
                    name = entry.summary ?: entry.id,
                    color = entry.backgroundColor ?: "#4285F4",
                    isPrimary = entry.isPrimary == true
                )
            }?.sortedByDescending { it.isPrimary } ?: emptyList()

            cachedCalendars = calendars
            cacheTimestamp = now
            calendars
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch calendars", e)
            cachedCalendars ?: emptyList()
        }
    }

    /**
     * Refreshes the calendar cache by clearing the timestamp and re-fetching.
     */
    private suspend fun refreshCalendarCache() {
        cacheTimestamp = 0
        cachedCalendars = null
        getUserCalendars()
    }

    /**
     * Checks if the current Google account has the calendar scope granted.
     */
    fun hasCalendarScope(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return account.grantedScopes.any { it.scopeUri == CalendarScopes.CALENDAR }
    }

    companion object {
        private const val TAG = "CalendarManager"
    }
}

/**
 * Thrown when the calendar OAuth scope is not granted and the caller
 * needs to trigger re-consent.
 */
class CalendarScopeRequiredException(message: String) : Exception(message)
