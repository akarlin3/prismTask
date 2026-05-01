package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.remote.api.HabitCorrelationsResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class HabitCorrelationsRepositoryTest {

    private val api: PrismTaskApi = mockk()
    private val repository = HabitCorrelationsRepository(api)

    @Test
    fun `success wraps the parsed response`() = runTest {
        val payload = HabitCorrelationsResponse(
            correlations = emptyList(),
            topInsight = "Looking good",
            recommendation = "Keep it up"
        )
        coEvery { api.getHabitCorrelations() } returns payload

        val outcome = repository.fetch()
        assertTrue(outcome is HabitCorrelationsOutcome.Success)
        assertEquals(payload, (outcome as HabitCorrelationsOutcome.Success).response)
    }

    @Test
    fun `451 maps to AiFeaturesDisabled`() = runTest {
        coEvery { api.getHabitCorrelations() } throws httpException(451)

        val outcome = repository.fetch()
        assertEquals(HabitCorrelationsOutcome.AiFeaturesDisabled, outcome)
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        coEvery { api.getHabitCorrelations() } throws httpException(429)

        val outcome = repository.fetch()
        assertEquals(HabitCorrelationsOutcome.RateLimited, outcome)
    }

    @Test
    fun `402 maps to NotPro`() = runTest {
        coEvery { api.getHabitCorrelations() } throws httpException(402)

        val outcome = repository.fetch()
        assertEquals(HabitCorrelationsOutcome.NotPro, outcome)
    }

    @Test
    fun `403 also maps to NotPro`() = runTest {
        coEvery { api.getHabitCorrelations() } throws httpException(403)

        val outcome = repository.fetch()
        assertEquals(HabitCorrelationsOutcome.NotPro, outcome)
    }

    @Test
    fun `500 maps to BackendUnavailable`() = runTest {
        coEvery { api.getHabitCorrelations() } throws httpException(500)

        val outcome = repository.fetch()
        assertTrue(outcome is HabitCorrelationsOutcome.BackendUnavailable)
    }

    @Test
    fun `IO failure maps to BackendUnavailable`() = runTest {
        coEvery { api.getHabitCorrelations() } throws IOException("offline")

        val outcome = repository.fetch()
        assertTrue(outcome is HabitCorrelationsOutcome.BackendUnavailable)
    }

    private fun httpException(code: Int): HttpException {
        val errorBody = "".toResponseBody("application/json".toMediaType())
        val response = Response.error<Any>(code, errorBody)
        return HttpException(response)
    }
}
