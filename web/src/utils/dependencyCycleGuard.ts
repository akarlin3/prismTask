/**
 * Detects whether adding a `(blocker, blocked)` edge would introduce a
 * cycle in the task-dependency graph.
 *
 * TypeScript port of Android `domain/usecase/DependencyCycleGuard.kt`
 * (DFS over outgoing edges, `MAX_DEPTH=10_000`). Used by the Roadmap
 * dependency picker to reject cycle-closing edges before write —
 * Android enforces the same guard server-side, but rejecting on web
 * gives the user immediate feedback and avoids a round-trip.
 *
 * Self-edges are treated as trivial cycles. A pre-existing cycle in
 * the input graph is bounded by [MAX_DEPTH] so the walk cannot stall.
 */

import type { TaskDependency } from '@/types/taskDependency';

export const MAX_DEPTH = 10_000;

export function wouldCreateCycle(
  edges: ReadonlyArray<Pick<TaskDependency, 'blocker_task_id' | 'blocked_task_id'>>,
  blocker: string,
  blocked: string,
): boolean {
  if (blocker === blocked) return true;
  if (edges.length === 0) return false;

  const outgoing = new Map<string, string[]>();
  for (const edge of edges) {
    const list = outgoing.get(edge.blocker_task_id);
    if (list) {
      list.push(edge.blocked_task_id);
    } else {
      outgoing.set(edge.blocker_task_id, [edge.blocked_task_id]);
    }
  }

  const visited = new Set<string>();
  const stack: string[] = [blocked];
  let depth = 0;
  while (stack.length > 0) {
    depth++;
    if (depth > MAX_DEPTH) return false;
    const current = stack.pop()!;
    if (visited.has(current)) continue;
    visited.add(current);
    const children = outgoing.get(current);
    if (!children) continue;
    for (const child of children) {
      if (child === blocker) return true;
      stack.push(child);
    }
  }
  return false;
}
