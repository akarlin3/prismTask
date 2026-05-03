package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.ExternalAnchorEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMapperExternalAnchorTest {

    @Test
    fun externalAnchor_roundTripsAllFields() {
        val anchor = ExternalAnchorEntity(
            id = 14,
            cloudId = null,
            projectId = 5,
            phaseId = 7,
            label = "Phase F kickoff",
            anchorJson = "{\"type\":\"calendar_deadline\",\"epochMs\":1700000000000}",
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.externalAnchorToMap(
            anchor,
            projectCloudId = "proj-cloud-5",
            phaseCloudId = "phase-cloud-7"
        )
        assertEquals("proj-cloud-5", map["projectCloudId"])
        assertEquals("phase-cloud-7", map["phaseCloudId"])

        val decoded = SyncMapper.mapToExternalAnchor(
            data = map,
            projectLocalId = anchor.projectId,
            phaseLocalId = anchor.phaseId,
            localId = anchor.id,
            cloudId = "anchor-cloud-14"
        )
        assertEquals("anchor-cloud-14", decoded.cloudId)
        assertEquals(anchor.copy(cloudId = "anchor-cloud-14"), decoded)
    }

    @Test
    fun externalAnchor_handlesNullPhase() {
        val anchor = ExternalAnchorEntity(
            id = 1,
            projectId = 5,
            phaseId = null,
            label = "Project-level anchor",
            anchorJson = "{\"type\":\"boolean_gate\",\"gateKey\":\"k\",\"expectedState\":true}",
            createdAt = 1L,
            updatedAt = 1L
        )
        val map = SyncMapper.externalAnchorToMap(anchor, projectCloudId = "p")
        assertEquals(null, map["phaseCloudId"])

        val decoded = SyncMapper.mapToExternalAnchor(
            data = map,
            projectLocalId = anchor.projectId,
            phaseLocalId = null,
            localId = anchor.id
        )
        assertEquals(null, decoded.phaseId)
    }
}
