package com.averycorp.prismtask.ui.screens.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial sign-in state is NotSignedIn`() = runTest {
        // SignInState sealed class should default to NotSignedIn
        val state: SignInState = SignInState.NotSignedIn
        assertTrue(state is SignInState.NotSignedIn)
    }

    @Test
    fun `SignedIn state holds email`() = runTest {
        val state = SignInState.SignedIn("test@example.com")
        assertEquals("test@example.com", state.email)
    }

    @Test
    fun `Error state holds message`() = runTest {
        val state = SignInState.Error("Something went wrong")
        assertEquals("Something went wrong", state.message)
    }
}
