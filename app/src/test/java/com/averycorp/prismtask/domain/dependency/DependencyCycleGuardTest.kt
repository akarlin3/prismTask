package com.averycorp.prismtask.domain.dependency

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyCycleGuardTest {

    private fun graph(edges: Map<Long, List<Long>>): suspend (Long) -> List<Long> =
        { id -> edges[id].orEmpty() }

    @Test
    fun `rejects self-edge`() = runTest {
        val cycle = DependencyCycleGuard.wouldCreateCycle(
            blockerId = 1L,
            blockedId = 1L,
            getBlockerIds = graph(emptyMap())
        )
        assertTrue(cycle)
    }

    @Test
    fun `accepts edge into empty graph`() = runTest {
        val cycle = DependencyCycleGuard.wouldCreateCycle(
            blockerId = 1L,
            blockedId = 2L,
            getBlockerIds = graph(emptyMap())
        )
        assertFalse(cycle)
    }

    @Test
    fun `detects direct two-node cycle`() = runTest {
        // 2 already blocks 1 (1's blocker set is [2]). Adding 1 → 2
        // would close 1 → 2 → 1.
        val cycle = DependencyCycleGuard.wouldCreateCycle(
            blockerId = 1L,
            blockedId = 2L,
            getBlockerIds = graph(mapOf(1L to listOf(2L)))
        )
        assertTrue(cycle)
    }

    @Test
    fun `detects transitive cycle through three nodes`() = runTest {
        // 3 blocks 2, 2 blocks 1. Adding 1 → 3 closes 1 → 3 → 2 → 1.
        val cycle = DependencyCycleGuard.wouldCreateCycle(
            blockerId = 1L,
            blockedId = 3L,
            getBlockerIds = graph(
                mapOf(
                    1L to listOf(2L),
                    2L to listOf(3L)
                )
            )
        )
        assertTrue(cycle)
    }

    @Test
    fun `accepts non-cycle edge`() = runTest {
        // 4 has no blockers; 1 → 4 is just a new leaf — no cycle.
        val cycle = DependencyCycleGuard.wouldCreateCycle(
            blockerId = 1L,
            blockedId = 4L,
            getBlockerIds = graph(
                mapOf(
                    1L to listOf(2L),
                    2L to listOf(3L)
                )
            )
        )
        assertFalse(cycle)
    }

    @Test
    fun `terminates on diamond without infinite loop`() = runTest {
        // Diamond: 4 → 2, 4 → 3, 2 → 1, 3 → 1. Adding 1 → 5 must NOT
        // visit each node twice.
        val visits = HashMap<Long, Int>()
        val cycle = DependencyCycleGuard.wouldCreateCycle(
            blockerId = 1L,
            blockedId = 5L,
            getBlockerIds = { id ->
                visits[id] = (visits[id] ?: 0) + 1
                when (id) {
                    1L -> listOf(2L, 3L)
                    2L -> listOf(4L)
                    3L -> listOf(4L)
                    else -> emptyList()
                }
            }
        )
        assertFalse(cycle)
        // Node 4 must be expanded exactly once.
        assertTrue("node 4 expanded ${visits[4L]} times", (visits[4L] ?: 0) <= 1)
    }
}
