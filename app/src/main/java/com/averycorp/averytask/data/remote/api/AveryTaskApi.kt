package com.averycorp.averytask.data.remote.api

import com.averycorp.averytask.data.remote.sync.SyncPullResponse
import com.averycorp.averytask.data.remote.sync.SyncPushRequest
import com.averycorp.averytask.data.remote.sync.SyncPushResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Retrofit interface for the AveryTask FastAPI backend.
 *
 * Base URL is provided by Hilt via [ApiClient] from `BuildConfig.API_BASE_URL`.
 */
interface AveryTaskApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    @POST("api/v1/tasks/parse")
    suspend fun parseTask(@Body request: ParseRequest): ParsedTaskResponse

    @GET("api/v1/app/version")
    suspend fun getVersion(): VersionResponse

    @POST("api/v1/sync/push")
    suspend fun syncPush(@Body request: SyncPushRequest): SyncPushResponse

    @GET("api/v1/sync/pull")
    suspend fun syncPull(@Query("since") since: String? = null): SyncPullResponse

    @GET("api/v1/export/json")
    suspend fun exportJson(): ResponseBody

    @Multipart
    @POST("api/v1/import/json")
    suspend fun importJson(
        @Part file: MultipartBody.Part,
        @Query("mode") mode: String = "merge"
    ): ImportResponse
}
