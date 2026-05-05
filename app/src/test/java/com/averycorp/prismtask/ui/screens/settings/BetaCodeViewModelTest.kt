package com.averycorp.prismtask.ui.screens.settings

import com.averycorp.prismtask.data.repository.BetaCodeRepository
import com.averycorp.prismtask.data.repository.BetaRedeemOutcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BetaCodeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: BetaCodeRepository
    private lateinit var viewModel: BetaCodeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        viewModel = BetaCodeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle and code is empty`() {
        assertEquals(BetaCodeUiState.Idle, viewModel.state.value)
        assertEquals("", viewModel.code.value)
    }

    @Test
    fun `onCodeChanged updates the code`() {
        viewModel.onCodeChanged("EARLY-BIRD")
        assertEquals("EARLY-BIRD", viewModel.code.value)
    }

    @Test
    fun `onCodeChanged after error returns to Idle`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.UnknownCode
        viewModel.onCodeChanged("BAD")
        viewModel.redeem()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is BetaCodeUiState.Error)

        viewModel.onCodeChanged("BAD2")
        assertEquals(BetaCodeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `redeem with empty code is a no-op`() = runTest(dispatcher) {
        viewModel.onCodeChanged("   ")
        viewModel.redeem()
        advanceUntilIdle()
        assertEquals(BetaCodeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `redeem success transitions to Success with proUntil`() = runTest(dispatcher) {
        coEvery { repository.redeem("CODE") } returns BetaRedeemOutcome.Granted("2026-06-15T00:00:00Z")
        viewModel.onCodeChanged("CODE")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value
        assertTrue(s is BetaCodeUiState.Success)
        assertEquals("2026-06-15T00:00:00Z", (s as BetaCodeUiState.Success).proUntil)
    }

    @Test
    fun `redeem perpetual success carries null proUntil`() = runTest(dispatcher) {
        coEvery { repository.redeem("CODE") } returns BetaRedeemOutcome.Granted(null)
        viewModel.onCodeChanged("CODE")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value
        assertTrue(s is BetaCodeUiState.Success)
        assertEquals(null, (s as BetaCodeUiState.Success).proUntil)
    }

    @Test
    fun `unknown-code failure produces user-friendly error`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.UnknownCode
        viewModel.onCodeChanged("CODE")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value
        assertTrue(s is BetaCodeUiState.Error)
        assertTrue((s as BetaCodeUiState.Error).message.contains("couldn't find", ignoreCase = true))
    }

    @Test
    fun `revoked failure produces revoked-specific copy`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.Revoked
        viewModel.onCodeChanged("X")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value as BetaCodeUiState.Error
        assertTrue(s.message.contains("revoked", ignoreCase = true))
    }

    @Test
    fun `expired failure produces expiry-specific copy`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.Expired
        viewModel.onCodeChanged("X")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value as BetaCodeUiState.Error
        assertTrue(s.message.contains("no longer valid", ignoreCase = true))
    }

    @Test
    fun `already-redeemed failure produces account-specific copy`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.AlreadyRedeemed
        viewModel.onCodeChanged("X")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value as BetaCodeUiState.Error
        assertTrue(s.message.contains("already redeemed", ignoreCase = true))
    }

    @Test
    fun `cap-reached failure produces redemption-limit copy`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.CapReached
        viewModel.onCodeChanged("X")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value as BetaCodeUiState.Error
        assertTrue(s.message.contains("redemption limit", ignoreCase = true))
    }

    @Test
    fun `not-signed-in failure prompts the user to sign in`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.NotSignedIn
        viewModel.onCodeChanged("X")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value as BetaCodeUiState.Error
        assertTrue(s.message.contains("sign in", ignoreCase = true))
    }

    @Test
    fun `network failure surfaces a connection-prompt copy`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.Network(RuntimeException("boom"))
        viewModel.onCodeChanged("X")
        viewModel.redeem()
        advanceUntilIdle()
        val s = viewModel.state.value as BetaCodeUiState.Error
        assertTrue(s.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `reset returns to Idle`() = runTest(dispatcher) {
        coEvery { repository.redeem(any()) } returns BetaRedeemOutcome.Failure.UnknownCode
        viewModel.onCodeChanged("X")
        viewModel.redeem()
        advanceUntilIdle()
        viewModel.reset()
        assertEquals(BetaCodeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `redeem trims whitespace before sending`() = runTest(dispatcher) {
        coEvery { repository.redeem("CODE") } returns BetaRedeemOutcome.Granted(null)
        viewModel.onCodeChanged("  CODE  ")
        viewModel.redeem()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is BetaCodeUiState.Success)
    }
}
