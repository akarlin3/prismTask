package com.averycorp.prismtask.domain.anchor

import com.averycorp.prismtask.domain.model.ExternalAnchor
import com.averycorp.prismtask.domain.model.NumericOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalAnchorJsonAdapterTest {

    @Test
    fun `calendar deadline round-trip preserves epoch`() {
        val original = ExternalAnchor.CalendarDeadline(epochMs = 1_750_000_000_000L)
        val encoded = ExternalAnchorJsonAdapter.encode(original)
        val decoded = ExternalAnchorJsonAdapter.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `numeric threshold round-trip preserves all fields`() {
        val original = ExternalAnchor.NumericThreshold(
            metric = "weekly_active_users",
            op = NumericOp.GTE,
            value = 10_000.0
        )
        val decoded = ExternalAnchorJsonAdapter.decode(
            ExternalAnchorJsonAdapter.encode(original)
        )
        assertEquals(original, decoded)
    }

    @Test
    fun `boolean gate round-trip preserves expected state`() {
        val original = ExternalAnchor.BooleanGate(
            gateKey = "phase_f_kickoff",
            expectedState = true
        )
        val decoded = ExternalAnchorJsonAdapter.decode(
            ExternalAnchorJsonAdapter.encode(original)
        )
        assertEquals(original, decoded)
    }

    @Test
    fun `decode returns null on malformed json`() {
        assertNull(ExternalAnchorJsonAdapter.decode("{ not even close to json"))
    }

    @Test
    fun `decode returns null on unknown type discriminator`() {
        assertNull(
            ExternalAnchorJsonAdapter.decode("""{"type":"galactic.harmonic","epochMs":0}""")
        )
    }
}
