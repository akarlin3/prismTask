package com.averycorp.averytask.data.remote.api

import com.averycorp.averytask.BuildConfig
import com.averycorp.averytask.data.preferences.AuthTokenPreferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module that provides the singletons needed to talk to the FastAPI
 * backend: OkHttp client (with logging + auth + 401-refresh), Retrofit, and
 * the [AveryTaskApi] interface.
 *
 * Base URL comes from `BuildConfig.API_BASE_URL`:
 *   - debug:   http://10.0.2.2:8000     (emulator → host loopback)
 *   - release: https://averytask-production.up.railway.app
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiClient {

    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        return interceptor
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenPreferences: AuthTokenPreferences
    ): AuthInterceptor = AuthInterceptor(tokenPreferences)

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenPreferences: AuthTokenPreferences,
        gson: Gson
    ): TokenAuthenticator = TokenAuthenticator(tokenPreferences, gson)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(BuildConfig.API_BASE_URL))
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideAveryTaskApi(retrofit: Retrofit): AveryTaskApi =
        retrofit.create(AveryTaskApi::class.java)

    private fun normalizeBaseUrl(url: String): String =
        if (url.endsWith("/")) url else "$url/"
}

/**
 * Attaches the cached JWT to every outgoing request as a Bearer token.
 *
 * Skips auth endpoints (`/auth/login`, `/auth/register`, `/auth/refresh`) so
 * that obtaining or refreshing a token never sends a stale one.
 */
class AuthInterceptor(
    private val tokenPreferences: AuthTokenPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (isAuthEndpoint(original)) {
            return chain.proceed(original)
        }

        val token = tokenPreferences.getAccessTokenBlocking()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }

    private fun isAuthEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/auth/login") ||
                path.endsWith("/auth/register") ||
                path.endsWith("/auth/refresh")
    }
}

/**
 * OkHttp [Authenticator] that handles 401 responses by attempting to refresh
 * the access token using the stored refresh token, then retrying the original
 * request once.
 *
 * Implemented as an Authenticator (rather than a plain Interceptor) so OkHttp
 * handles the request replay automatically and avoids retry loops.
 */
class TokenAuthenticator(
    private val tokenPreferences: AuthTokenPreferences,
    private val gson: Gson
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops: only attempt refresh once.
        if (responseCount(response) >= 2) return null

        val refreshToken = tokenPreferences.getRefreshTokenBlocking()
        if (refreshToken.isNullOrBlank()) return null

        val newTokens = synchronized(this) {
            // Re-check in case another thread already refreshed.
            val currentAccess = tokenPreferences.getAccessTokenBlocking()
            val authHeader = response.request.header("Authorization")
            if (currentAccess != null && authHeader != "Bearer $currentAccess") {
                // Tokens were refreshed by another thread; reuse them.
                TokenResponse(
                    accessToken = currentAccess,
                    refreshToken = refreshToken,
                    tokenType = "bearer"
                )
            } else {
                refreshTokens(response, refreshToken)
            }
        } ?: return null

        tokenPreferences.setTokensBlocking(newTokens.accessToken, newTokens.refreshToken)

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    private fun refreshTokens(response: Response, refreshToken: String): TokenResponse? {
        return try {
            val refreshUrl = response.request.url.newBuilder()
                .encodedPath("/api/v1/auth/refresh")
                .query(null)
                .build()

            val body = gson.toJson(RefreshRequest(refreshToken))
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(refreshUrl)
                .post(body)
                .build()

            // Use a bare client (no auth interceptor / no authenticator) to
            // avoid recursing back into this Authenticator.
            val bareClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            bareClient.newCall(request).execute().use { refreshResponse ->
                if (!refreshResponse.isSuccessful) return@use null
                val responseBody = refreshResponse.body?.string() ?: return@use null
                gson.fromJson(responseBody, TokenResponse::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
