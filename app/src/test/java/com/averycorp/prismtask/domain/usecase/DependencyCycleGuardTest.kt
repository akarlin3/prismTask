package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyCycleGuardTest {

    private fun edge(blocker: Long, blocked: Long, id: Long = 0L) =
        TaskDependencyEntity(id = id, blockerTaskId = blocker, blockedTaskId = blocked)

    @Test
    fun emptyGraph_neverCycles() {
        assertFalse(DependencyCycleGuard.wouldCreateCycle(emptyList(), 1, 2))
    }

    @Test
    fun selfEdgeIsAlwaysACycle() {
        assertTrue(DependencyCycleGuard.wouldCreateCycle(emptyList(), 5, 5))
        assertTrue(DependencyCycleGuard.wouldCreateCycle(listOf(edge(1, 2)), 7, 7))
    }

    @Test
    fun directInverseEdgeIsACycle() {
        // Existing: 1 -> 2. Proposed: 2 -> 1 closes the loop.
        val edges = listOf(edge(1, 2))
        assertTrue(DependencyCycleGuard.wouldCreateCycle(edges, blocker = 2, blocked = 1))
    }

    @Test
    fun transitiveCycleIsDetected() {
        // 1 -> 2 -> 3, proposed 3 -> 1 closes a 3-node loop.
        val edges = listOf(edge(1, 2), edge(2, 3))
        assertTrue(DependencyCycleGuard.wouldCreateCycle(edges, blocker = 3, blocked = 1))
    }

    @Test
    fun parallelEdgesAreNotCycles() {
        // 1 blocks both 2 and 3; adding 2 -> 3 keeps things acyclic.
        val edges = listOf(edge(1, 2), edge(1, 3))
        assertFalse(DependencyCycleGuard.wouldCreateCycle(edges, blocker = 2, blocked = 3))
    }

    @Test
    fun longLinearChainIsAcyclic() {
        // 1 -> 2 -> 3 -> 4 -> 5, proposed 5 -> 6 stays linear.
        val edges = listOf(edge(1, 2), edge(2, 3), edge(3, 4), edge(4, 5))
        assertFalse(DependencyCycleGuard.wouldCreateCycle(edges, blocker = 5, blocked = 6))
    }

    @Test
    fun disconnectedComponentDoesNotMatter() {
        // Two disjoint chains: 1 -> 2 -> 3, and 10 -> 11. Proposing
        // 3 -> 10 bridges them but introduces no cycle.
        val edges = listOf(edge(1, 2), edge(2, 3), edge(10, 11))
        assertFalse(DependencyCycleGuard.wouldCreateCycle(edges, blocker = 3, blocked = 10))
    }

    @Test
    fun preexistingCycleStillReportsAccurately() {
        // Pathological: graph already has 1 -> 2 -> 1 (would've been
        // rejected, but let's ensure the walk terminates and reports
        // any new edge involving these nodes as a cycle).
        val edges = listOf(edge(1, 2), edge(2, 1))
        assertTrue(DependencyCycleGuard.wouldCreateCycle(edges, blocker = 1, blocked = 2))
    }
}
