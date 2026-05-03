package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMapperTaskDependencyTest {

    @Test
    fun taskDependency_roundTripsBothEndpointsAndCreatedAt() {
        val edge = TaskDependencyEntity(
            id = 12,
            cloudId = null,
            blockerTaskId = 100L,
            blockedTaskId = 200L,
            createdAt = 1_700_000_000_000L
        )
        val map = SyncMapper.taskDependencyToMap(
            edge,
            blockerTaskCloudId = "task-cloud-blocker",
            blockedTaskCloudId = "task-cloud-blocked"
        )
        assertEquals("task-cloud-blocker", map["blockerTaskCloudId"])
        assertEquals("task-cloud-blocked", map["blockedTaskCloudId"])

        val decoded = SyncMapper.mapToTaskDependency(
            data = map,
            blockerTaskLocalId = edge.blockerTaskId,
            blockedTaskLocalId = edge.blockedTaskId,
            localId = edge.id,
            cloudId = "edge-cloud-12"
        )
        assertEquals(12L, decoded.id)
        assertEquals("edge-cloud-12", decoded.cloudId)
        assertEquals(100L, decoded.blockerTaskId)
        assertEquals(200L, decoded.blockedTaskId)
        assertEquals(1_700_000_000_000L, decoded.createdAt)
    }
}
