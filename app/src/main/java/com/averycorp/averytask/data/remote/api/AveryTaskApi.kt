package com.averycorp.averytask.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
