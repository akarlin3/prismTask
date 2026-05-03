package com.averycorp.prismtask.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RiskLevelTest {

    @Test
    fun fromStorage_handlesKnownValues() {
        assertEquals(RiskLevel.LOW, RiskLevel.fromStorage("LOW"))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromStorage("MEDIUM"))
        assertEquals(RiskLevel.HIGH, RiskLevel.fromStorage("HIGH"))
    }

    @Test
    fun fromStorage_defaultsToMediumForUnknownOrNull() {
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromStorage(null))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromStorage(""))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromStorage("low"))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromStorage("CRITICAL"))
    }
}
