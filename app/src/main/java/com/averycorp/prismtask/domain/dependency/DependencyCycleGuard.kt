package com.averycorp.prismtask.domain.dependency

/**
 * Cycle-prevention DFS for the task-dependency graph. Mirrors the
 * `lineage: Set<Long>` walk in
 * [com.averycorp.prismtask.domain.automation.AutomationEngine.handleEvent]
 * — when an automation rule's lineage already contains the rule we're
 * about to fire, we abort. Same shape here: if `blocked → … → blocker`
 * already exists in the graph, adding `blocker → blocked` would
 * close a cycle and we refuse the write.
 *
 * Repository signature: provide a [getBlockerIds] lookup (the set of
 * tasks that block a given task), then call [wouldCreateCycle] before
 * inserting a new edge.
 */
object DependencyCycleGuard {
    /**
     * Returns true iff inserting an edge `blockerId → blockedId` would
     * close a cycle. Self-edges (`blockerId == blockedId`) are
     * always rejected.
     *
     * The walk runs upstream from [blockerId]: we DFS through the
     * blocker's transitive blockers, looking for [blockedId]. If we
     * find it, the new edge would create a cycle.
     */
    suspend fun wouldCreateCycle(
        blockerId: Long,
        blockedId: Long,
        getBlockerIds: suspend (Long) -> List<Long>
    ): Boolean {
        if (blockerId == blockedId) return true
        val visited = HashSet<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(blockerId)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!visited.add(current)) continue
            if (current == blockedId) return true
            for (blocker in getBlockerIds(current)) {
                if (blocker !in visited) stack.addLast(blocker)
            }
        }
        return false
    }
}
