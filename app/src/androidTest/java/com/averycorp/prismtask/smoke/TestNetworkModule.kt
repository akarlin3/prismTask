package com.averycorp.prismtask.smoke

import com.averycorp.prismtask.data.remote.api.BugReportMirrorResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.UserInfoResponse
import com.averycorp.prismtask.data.remote.sync.SyncPullResponse
import com.averycorp.prismtask.data.remote.sync.SyncPushRequest
import com.averycorp.prismtask.data.remote.sync.SyncPushResponse
import com.averycorp.prismtask.di.NetworkModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Singleton

/**
 * Replaces the production [NetworkModule] with an in-memory fake
 * [PrismTaskApi] so tests never hit the real backend. The fake is
 * programmable via [FakePrismTaskApi] to simulate offline failures,
 * successful pushes, and cancellation mid-flight.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object TestNetworkModule {
    @Provides
    @Singleton
    fun provideFakeApi(): FakePrismTaskApi = FakePrismTaskApi()

    @Provides
    @Singleton
    fun providePrismTaskApi(fake: FakePrismTaskApi): PrismTaskApi = fake

    // CalendarBackendApi is bound by the production NetworkModule; since we
    // replace that module, tests that @Inject a CalendarBackendApi (directly
    // or transitively via CalendarSyncRepository) fail to resolve unless we
    // provide a stub here. Real calendar traffic is never exercised in the
    // instrumentation suite, so a mockk(relaxed) is sufficient.
    @Provides
    @Singleton
    fun provideCalendarBackendApi(): com.averycorp.prismtask.data.remote.api.CalendarBackendApi =
        io.mockk.mockk(relaxed = true)
}

/**
 * Configurable fake [PrismTaskApi] used by the offline/sync smoke tests.
 *
 * Key knobs:
 *  - [networkEnabled]: when false, all suspend calls throw to simulate
 *    being offline (no connectivity, DNS fail, etc.).
 *  - [pushedOperations] / [pulledSinceParams]: record calls for assertions.
 *  - [onBeforePush]: optional hook that runs before each `syncPush`. Used
 *    by the kill-during-sync test to cancel the coroutine mid-flight.
 */
class FakePrismTaskApi : PrismTaskApi {
    @Volatile var networkEnabled: Boolean = true

    val pushedOperations: MutableList<SyncPushRequest> = mutableListOf()
    val pulledSinceParams: MutableList<String?> = mutableListOf()

    @Volatile var onBeforePush: (suspend () -> Unit)? = null

    @Volatile var onBeforePull: (suspend () -> Unit)? = null

    private fun requireOnline() {
        if (!networkEnabled) {
            throw java.io.IOException("Simulated offline: network is disabled")
        }
    }

    override suspend fun getMe(): UserInfoResponse {
        requireOnline()
        return UserInfoResponse(
            id = 1,
            email = "test@example.com",
            name = "Test",
            tier = "FREE",
            isAdmin = false,
            effectiveTier = "FREE"
        )
    }

    override suspend fun register(
        request: com.averycorp.prismtask.data.remote.api.RegisterRequest
    ) = error("Not used in offline tests")

    override suspend fun login(
        request: com.averycorp.prismtask.data.remote.api.LoginRequest
    ) = error("Not used in offline tests")

    override suspend fun firebaseLogin(
        request: com.averycorp.prismtask.data.remote.api.FirebaseTokenRequest
    ) = error("Not used in offline tests")

    override suspend fun refresh(
        request: com.averycorp.prismtask.data.remote.api.RefreshRequest
    ) = error("Not used in offline tests")

    override suspend fun parseTask(
        request: com.averycorp.prismtask.data.remote.api.ParseRequest
    ) = error("Not used in offline tests")

    override suspend fun getVersion() = error("Not used in offline tests")

    override suspend fun syncPush(request: SyncPushRequest): SyncPushResponse {
        onBeforePush?.invoke()
        requireOnline()
        pushedOperations += request
        return SyncPushResponse(
            processed = request.operations.size,
            errors = emptyList(),
            serverTimestamp = null
        )
    }

    override suspend fun syncPull(since: String?): SyncPullResponse {
        onBeforePull?.invoke()
        requireOnline()
        pulledSinceParams += since
        return SyncPullResponse(changes = emptyList(), serverTimestamp = null)
    }

    override suspend fun categorizeEisenhower(
        request: com.averycorp.prismtask.data.remote.api.EisenhowerRequest
    ) = error("Not used in offline tests")

    override suspend fun planPomodoro(
        request: com.averycorp.prismtask.data.remote.api.PomodoroRequest
    ) = error("Not used in offline tests")

    override suspend fun getDailyBriefing(
        request: com.averycorp.prismtask.data.remote.api.DailyBriefingRequest
    ) = error("Not used in offline tests")

    override suspend fun getWeeklyPlan(
        request: com.averycorp.prismtask.data.remote.api.WeeklyPlanRequest
    ) = error("Not used in offline tests")

    override suspend fun getTimeBlock(
        request: com.averycorp.prismtask.data.remote.api.TimeBlockRequest
    ) = error("Not used in offline tests")

    override suspend fun aiChat(
        request: com.averycorp.prismtask.data.remote.api.ChatRequest
    ) = error("Not used in offline tests")

    override suspend fun parseImport(
        request: com.averycorp.prismtask.data.remote.api.ParseImportRequest
    ) = error("Not used in offline tests")

    override suspend fun parseChecklist(
        request: com.averycorp.prismtask.data.remote.api.ParseChecklistRequest
    ) = error("Not used in offline tests")

    override suspend fun getEveningSummary(
        request: com.averycorp.prismtask.data.remote.api.EveningSummaryRequest
    ) = error("Not used in offline tests")

    override suspend fun getReengagementNudge(
        request: com.averycorp.prismtask.data.remote.api.ReengagementRequest
    ) = error("Not used in offline tests")

    override suspend fun getCoaching(
        request: com.averycorp.prismtask.data.remote.api.CoachingRequest
    ) = error("Not used in offline tests")

    override suspend fun exportJson(): ResponseBody = "".toResponseBody()

    override suspend fun importJson(
        file: MultipartBody.Part,
        mode: String
    ) = error("Not used in offline tests")

    override suspend fun submitBugReport(
        body: Map<String, Any?>
    ): BugReportMirrorResponse = error("Not used in offline tests")

    override suspend fun getWeeklyReview(
        request: com.averycorp.prismtask.data.remote.api.WeeklyReviewRequest
    ): com.averycorp.prismtask.data.remote.api.WeeklyReviewResponse =
        error("Not used in offline tests")

    override suspend fun listBugReports(
        statusFilter: String?,
        severity: String?,
        page: Int,
        limit: Int
    ): List<com.averycorp.prismtask.data.remote.api.AdminBugReportResponse> =
        error("Not used in offline tests")

    override suspend fun updateBugReportStatus(
        reportId: String,
        body: com.averycorp.prismtask.data.remote.api.BugReportStatusUpdateRequest
    ): com.averycorp.prismtask.data.remote.api.AdminBugReportResponse =
        error("Not used in offline tests")

    override suspend fun parseSyllabus(
        file: MultipartBody.Part
    ): com.averycorp.prismtask.data.remote.api.SyllabusParseResponse =
        error("Not used in offline tests")

    override suspend fun confirmSyllabus(
        request: com.averycorp.prismtask.data.remote.api.SyllabusConfirmRequest
    ): com.averycorp.prismtask.data.remote.api.SyllabusConfirmResponse =
        error("Not used in offline tests")
}
