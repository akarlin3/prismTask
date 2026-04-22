package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.AuthTokenPreferences
import com.averycorp.prismtask.data.remote.api.EisenhowerClassifyTextRequest
import com.averycorp.prismtask.data.remote.api.EisenhowerClassifyTextResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.domain.model.EisenhowerQuadrant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class EisenhowerClassifierTest {
    private lateinit var api: PrismTaskApi
    private lateinit var authTokenPreferences: AuthTokenPreferences
    private lateinit var classifier: EisenhowerClassifier

    // dueDate is a fixed ms-since-epoch value; exact instant doesn't matter for these tests.
    private val sampleTask = TaskEntity(
        id = 1L,
        title = "Ship the launch post",
        description = "Blog + social",
        dueDate = 1_800_000_000_000L,
        priority = 3
    )

    @Before
    fun setUp() {
        api = mockk()
        authTokenPreferences = mockk()
        classifier = EisenhowerClassifier(api, authTokenPreferences)
    }

    @Test
    fun classify_returnsSuccessWhenApiRespondsWithValidQuadrant() = runBlocking {
        coEvery { authTokenPreferences.getAccessToken() } returns "valid-token"
        coEvery { api.classifyEisenhowerText(any()) } returns
            EisenhowerClassifyTextResponse(quadrant = "Q1", reason = "Due within 48h")

        val result = classifier.classify(sampleTask)

        assertTrue(result.isSuccess)
        val classification = result.getOrNull()!!
        assertEquals(EisenhowerQuadrant.URGENT_IMPORTANT, classification.quadrant)
        assertEquals("Due within 48h", classification.reason)
    }

    @Test
    fun classify_returnsFailureWhenNoAuthToken() = runBlocking {
        coEvery { authTokenPreferences.getAccessToken() } returns null

        val result = classifier.classify(sampleTask)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.classifyEisenhowerText(any()) }
    }

    @Test
    fun classify_returnsFailureWhenTokenBlank() = runBlocking {
        coEvery { authTokenPreferences.getAccessToken() } returns "   "

        val result = classifier.classify(sampleTask)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.classifyEisenhowerText(any()) }
    }

    @Test
    fun classify_returnsFailureWhenApiThrows() = runBlocking {
        coEvery { authTokenPreferences.getAccessToken() } returns "valid-token"
        coEvery { api.classifyEisenhowerText(any()) } throws IOException("network down")

        val result = classifier.classify(sampleTask)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun classify_returnsFailureOnUnknownQuadrantCode() = runBlocking {
        // Server contract says Q1..Q4 only. A stray value ("X", "Q9", empty
        // string) must not silently overwrite the task's existing quadrant.
        coEvery { authTokenPreferences.getAccessToken() } returns "valid-token"
        coEvery { api.classifyEisenhowerText(any()) } returns
            EisenhowerClassifyTextResponse(quadrant = "Q9", reason = "bogus")

        val result = classifier.classify(sampleTask)

        assertTrue(result.isFailure)
    }

    @Test
    fun classify_returnsFailureOnEmptyQuadrantCode() = runBlocking {
        coEvery { authTokenPreferences.getAccessToken() } returns "valid-token"
        coEvery { api.classifyEisenhowerText(any()) } returns
            EisenhowerClassifyTextResponse(quadrant = "", reason = "empty")

        val result = classifier.classify(sampleTask)

        assertTrue(result.isFailure)
    }

    @Test
    fun classify_sendsTaskFieldsInRequest() = runBlocking {
        coEvery { authTokenPreferences.getAccessToken() } returns "valid-token"
        val captured = slot<EisenhowerClassifyTextRequest>()
        coEvery { api.classifyEisenhowerText(capture(captured)) } returns
            EisenhowerClassifyTextResponse(quadrant = "Q2", reason = "ok")

        classifier.classify(sampleTask)

        assertEquals("Ship the launch post", captured.captured.title)
        assertEquals("Blog + social", captured.captured.description)
        assertEquals(3, captured.captured.priority)
        // Due-date wire format is ISO yyyy-MM-dd. We only assert presence +
        // format here — exact value depends on the test JVM's default timezone
        // because TaskEntity.dueDate is epoch millis.
        assertTrue(captured.captured.dueDate!!.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun classify_sendsNullDueDateWhenTaskHasNone() = runBlocking {
        coEvery { authTokenPreferences.getAccessToken() } returns "valid-token"
        val captured = slot<EisenhowerClassifyTextRequest>()
        coEvery { api.classifyEisenhowerText(capture(captured)) } returns
            EisenhowerClassifyTextResponse(quadrant = "Q2", reason = "ok")

        classifier.classify(sampleTask.copy(dueDate = null))

        assertNull(captured.captured.dueDate)
    }

    @Test
    fun classify_rateLimitReturnsFailureNotCrash() = runBlocking {
        // 429 etc. bubble up from Retrofit as HttpException wrapped by
        // try/catch — simulate with a plain RuntimeException to confirm the
        // classifier wraps rather than propagates.
        coEvery { authTokenPreferences.getAccessToken() } returns "valid-token"
        coEvery { api.classifyEisenhowerText(any()) } throws RuntimeException("429 Too Many Requests")

        val result = classifier.classify(sampleTask)

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }
}
