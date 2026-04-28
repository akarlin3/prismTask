package com.averycorp.prismtask.sync.fuzz

import com.averycorp.prismtask.sync.scenarios.SyncScenarioTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Base for sync fuzz scenarios. Extends [SyncScenarioTestBase] so the
 * Hilt graph + `SyncTestHarness` lifecycle stays identical to hand-rolled
 * scenarios — a fuzz test is just a hand-rolled scenario whose op sequence
 * comes from [SyncFuzzGenerator] instead of being literal.
 *
 * Subclasses provide:
 *  - a seeded [SyncFuzzGenerator]
 *  - an `applyOp(op: SyncFuzzOp)` lambda that maps a generated op onto
 *    whatever concrete entity API the scenario targets (e.g.
 *    `taskRepository.addTask`, `medicationRepository.insert`)
 *  - an `assertInvariants()` lambda invoked between every op
 *
 * Per memory `feedback_firestore_doc_iteration_order.md`, invariants must
 * assert convergence shape (row count, FK integrity, presence/absence of
 * specific keys) — never `cloud_id` ordering, since the SDK's doc iteration
 * order flips between CI runs.
 */
abstract class SyncFuzzScenarioBase : SyncScenarioTestBase() {
    /**
     * Run [ops] sequentially, calling [applyOp] for each, then
     * [assertInvariants] after each. Fails fast on the first invariant
     * violation, surfacing the seed and op index so the failure can be
     * replayed locally.
     *
     * The caller is responsible for invoking `syncService.pushLocalChanges()`
     * / `pullRemoteChanges()` inside [applyOp] when it's relevant — fuzzing
     * unconditionally on every op would 30-40x the test runtime against the
     * Firebase Emulator. Most scenarios push/pull every K ops or only at
     * sequence end, depending on what surface they're stressing.
     */
    protected suspend fun runFuzzSequence(
        seed: Long,
        ops: List<SyncFuzzOp>,
        applyOp: suspend (SyncFuzzOp) -> Unit,
        assertInvariants: suspend (opIndex: Int, op: SyncFuzzOp) -> Unit
    ) {
        ops.forEachIndexed { index, op ->
            try {
                applyOp(op)
                assertInvariants(index, op)
            } catch (t: Throwable) {
                throw AssertionError(
                    "Fuzz sequence failed at op[$index] = $op (seed=$seed). " +
                        "Replay locally by pinning seed=$seed in the failing scenario.",
                    t
                )
            }
        }
    }

    /**
     * Convenience invariant: assert local Room row count for [entityType]
     * matches [expected], with a contextual error message that surfaces
     * which fuzz step the assertion fired on.
     */
    protected fun assertRowCount(
        actual: Int,
        expected: Int,
        opIndex: Int,
        op: SyncFuzzOp,
        entityType: String
    ) {
        assertEquals(
            "Expected $expected $entityType rows after op[$opIndex] = $op; got $actual",
            expected,
            actual
        )
    }

    /**
     * Convenience invariant: assert no orphan rows exist for a
     * parent-child FK relationship. Subclasses pass in actual lists from
     * their DAO calls and the parent-key extractor.
     */
    protected fun <C, P> assertNoOrphans(
        children: List<C>,
        parents: List<P>,
        childParentKey: (C) -> Long?,
        parentKey: (P) -> Long,
        opIndex: Int,
        op: SyncFuzzOp,
        entityType: String
    ) {
        val parentIds = parents.mapTo(mutableSetOf(), parentKey)
        val orphans = children.filter { c ->
            childParentKey(c).let { it != null && it !in parentIds }
        }
        assertTrue(
            "Found ${orphans.size} orphan $entityType rows after op[$opIndex] = $op " +
                "(parent ids: $parentIds). First orphan: ${orphans.firstOrNull()}",
            orphans.isEmpty()
        )
    }
}
