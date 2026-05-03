package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip tests for the PrismTask-timeline-class sync mappers
 * (PR-1 of the timeline-class scope). Mirrors the
 * [SyncMapperContentTest] pattern: encode → decode → assert equality
 * with the parent cloud id round-tripped via caller-supplied bindings.
 */
class SyncMapperPhasesAndRisksTest {

    @Test
    fun projectPhase_roundTripsAllFields() {
        val phase = ProjectPhaseEntity(
            id = 7,
            cloudId = null,
            projectId = 11,
            title = "Phase F kickoff",
            description = "Hard gate May 15",
            colorKey = "phase.f",
            startDate = 1_714_000_000_000L,
            endDate = 1_715_000_000_000L,
            versionAnchor = "v1.9.0",
            versionNote = "Timeline-class foundation",
            orderIndex = 3,
            completedAt = null,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.projectPhaseToMap(phase, projectCloudId = "proj-cloud-1")
        assertEquals("proj-cloud-1", map["projectCloudId"])

        val decoded = SyncMapper.mapToProjectPhase(
            data = map,
            projectLocalId = phase.projectId,
            localId = phase.id,
            cloudId = "phase-cloud-7"
        )
        assertEquals("phase-cloud-7", decoded.cloudId)
        assertEquals(phase.copy(cloudId = "phase-cloud-7"), decoded)
    }

    @Test
    fun projectRisk_roundTripsAllFields() {
        val risk = ProjectRiskEntity(
            id = 4,
            cloudId = null,
            projectId = 11,
            title = "Migration ordering",
            level = "HIGH",
            mitigation = "Coordinate with Cognitive Load session",
            resolvedAt = 1_716_000_000_000L,
            createdAt = 5L,
            updatedAt = 6L
        )
        val map = SyncMapper.projectRiskToMap(risk, projectCloudId = "proj-cloud-1")
        assertEquals("proj-cloud-1", map["projectCloudId"])

        val decoded = SyncMapper.mapToProjectRisk(
            data = map,
            projectLocalId = risk.projectId,
            localId = risk.id,
            cloudId = "risk-cloud-4"
        )
        assertEquals("risk-cloud-4", decoded.cloudId)
        assertEquals(risk.copy(cloudId = "risk-cloud-4"), decoded)
    }

    @Test
    fun projectRisk_defaultsLevelToMediumOnUnknownString() {
        val map = mapOf<String, Any?>(
            "title" to "untyped",
            // omit "level" — pull defaults to MEDIUM
            "createdAt" to 1L,
            "updatedAt" to 2L
        )
        val decoded = SyncMapper.mapToProjectRisk(map, projectLocalId = 1L)
        assertEquals("MEDIUM", decoded.level)
    }

    @Test
    fun task_phaseId_andProgressPercent_roundTrip() {
        val task = com.averycorp.prismtask.data.local.entity.TaskEntity(
            id = 9,
            title = "Phased",
            phaseId = 3L,
            progressPercent = 60,
            createdAt = 0L,
            updatedAt = 0L
        )
        val map = SyncMapper.taskToMap(task, phaseCloudId = "phase-cloud-3")
        assertEquals("phase-cloud-3", map["phaseId"])
        assertEquals(60, map["progressPercent"])

        val restored = SyncMapper.mapToTask(map, localId = 9, phaseLocalId = 3L)
        assertEquals(3L, restored.phaseId)
        assertEquals(60, restored.progressPercent)
    }

    @Test
    fun task_nullPhaseAndProgress_areLegacyDefault() {
        val task = com.averycorp.prismtask.data.local.entity.TaskEntity(
            id = 1,
            title = "Legacy",
            createdAt = 0L,
            updatedAt = 0L
        )
        val map = SyncMapper.taskToMap(task)
        // Legacy tasks emit nulls for phaseId / progressPercent so cross-device
        // sync of older rows doesn't accidentally fabricate a fractional value.
        assertEquals(null, map["phaseId"])
        assertEquals(null, map["progressPercent"])

        val restored = SyncMapper.mapToTask(map, localId = 1)
        assertEquals(null, restored.phaseId)
        assertEquals(null, restored.progressPercent)
    }
}
