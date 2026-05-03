package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity

/**
 * Detects whether adding a `(blocker, blocked)` edge would introduce a
 * cycle in the task-dependency graph.
 *
 * Mirrors the lineage + depth-cap pattern in
 * `AutomationEngine.kt:73-86`: a pure DFS over the existing edges
 * looking for a path from `blocked` back to `blocker`. If found, the
 * edge would close a cycle and must be rejected at write time.
 *
 * The walk carries a [MAX_DEPTH] safety cap so a malformed graph (e.g.
 * a pre-existing cycle that slipped past validation) cannot stall the
 * caller in an infinite loop.
 *
 * Self-edges are treated as trivial cycles.
 */
object DependencyCycleGuard {

    /** Hard ceiling on path length explored before the walk gives up. */
    const val MAX_DEPTH: Int = 10_000

    /**
     * @param edges Snapshot of every existing edge in the database.
     * @param blocker Proposed blocker task id.
     * @param blocked Proposed blocked task id.
     * @return `true` if adding `(blocker, blocked)` would close a cycle.
     */
    fun wouldCreateCycle(
        edges: List<TaskDependencyEntity>,
        blocker: Long,
        blocked: Long
    ): Boolean {
        if (blocker == blocked) return true
        if (edges.isEmpty()) return false

        // Index existing edges by their tail (blocker) so traversal from
        // a node hits all outgoing edges in O(degree) instead of O(|E|).
        val outgoing = HashMap<Long, MutableList<Long>>(edges.size)
        for (edge in edges) {
            outgoing.getOrPut(edge.blockerTaskId) { mutableListOf() }
                .add(edge.blockedTaskId)
        }

        // DFS from `blocked` along existing edges. If we ever reach
        // `blocker`, the proposed edge `(blocker, blocked)` would close
        // a loop: blocker → blocked → … → blocker.
        val visited = HashSet<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(blocked)
        var depth = 0
        while (stack.isNotEmpty()) {
            depth++
            if (depth > MAX_DEPTH) return false
            val current = stack.removeLast()
            if (!visited.add(current)) continue
            val children = outgoing[current] ?: continue
            for (child in children) {
                if (child == blocker) return true
                stack.addLast(child)
            }
        }
        return false
    }
}
