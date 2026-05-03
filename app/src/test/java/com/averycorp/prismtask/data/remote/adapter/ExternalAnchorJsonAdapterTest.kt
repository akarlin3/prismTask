package com.averycorp.prismtask.data.remote.adapter

import com.averycorp.prismtask.domain.model.ComparisonOp
import com.averycorp.prismtask.domain.model.ExternalAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAnchorJsonAdapterTest {

    @Test
    fun calendarDeadline_roundTripsExactly() {
        val anchor = ExternalAnchor.CalendarDeadline(epochMs = 1_700_000_000_000L)
        val decoded = ExternalAnchorJsonAdapter.decode(ExternalAnchorJsonAdapter.encode(anchor))
        assertEquals(anchor, decoded)
    }

    @Test
    fun numericThreshold_roundTripsAllOps() {
        for (op in ComparisonOp.values()) {
            val anchor = ExternalAnchor.NumericThreshold("credit_balance", op, 12.5)
            val decoded =
                ExternalAnchorJsonAdapter.decode(ExternalAnchorJsonAdapter.encode(anchor))
            assertEquals(anchor, decoded)
        }
    }

    @Test
    fun booleanGate_roundTripsBothExpectedStates() {
        val on = ExternalAnchor.BooleanGate("phase_f_kickoff", expectedState = true)
        val off = ExternalAnchor.BooleanGate("beta_opt_in", expectedState = false)
        assertEquals(on, ExternalAnchorJsonAdapter.decode(ExternalAnchorJsonAdapter.encode(on)))
        assertEquals(off, ExternalAnchorJsonAdapter.decode(ExternalAnchorJsonAdapter.encode(off)))
    }

    @Test
    fun encodedJson_carriesTypeDiscriminator() {
        val anchor = ExternalAnchor.CalendarDeadline(epochMs = 0L)
        val json = ExternalAnchorJsonAdapter.encode(anchor)
        assertTrue("type discriminator present", json.contains("\"type\":\"calendar_deadline\""))
    }

    @Test
    fun decode_returnsNullForBlankOrMalformedInput() {
        assertNull(ExternalAnchorJsonAdapter.decode(null))
        assertNull(ExternalAnchorJsonAdapter.decode(""))
        assertNull(ExternalAnchorJsonAdapter.decode("not json"))
    }

    @Test
    fun decode_returnsNullForUnknownType() {
        // Forward-compat: an anchor variant added in a future release
        // pulled into a current client must not crash the decode path.
        val unknown = "{\"type\":\"future_kind\",\"value\":42}"
        assertNull(ExternalAnchorJsonAdapter.decode(unknown))
    }

    @Test
    fun decode_returnsNullForNumericThresholdWithUnknownOp() {
        val malformed = "{\"type\":\"numeric_threshold\",\"metric\":\"x\",\"op\":\"~~\",\"value\":1}"
        assertNull(ExternalAnchorJsonAdapter.decode(malformed))
    }
}
