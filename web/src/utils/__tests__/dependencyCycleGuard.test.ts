import { describe, it, expect } from 'vitest';
import { wouldCreateCycle, MAX_DEPTH } from '@/utils/dependencyCycleGuard';

describe('wouldCreateCycle', () => {
  it('returns false for an empty graph', () => {
    expect(wouldCreateCycle([], 'a', 'b')).toBe(false);
  });

  it('rejects a self-edge as a trivial cycle', () => {
    expect(wouldCreateCycle([], 'a', 'a')).toBe(true);
  });

  it('rejects a direct 2-cycle (b → a then proposing a → b)', () => {
    const edges = [{ blocker_task_id: 'b', blocked_task_id: 'a' }];
    expect(wouldCreateCycle(edges, 'a', 'b')).toBe(true);
  });

  it('accepts an unrelated edge', () => {
    const edges = [{ blocker_task_id: 'b', blocked_task_id: 'c' }];
    expect(wouldCreateCycle(edges, 'a', 'b')).toBe(false);
  });

  it('rejects a 3-step transitive cycle', () => {
    // existing: b → c, c → d. Proposing d → b would close b → c → d → b.
    const edges = [
      { blocker_task_id: 'b', blocked_task_id: 'c' },
      { blocker_task_id: 'c', blocked_task_id: 'd' },
    ];
    expect(wouldCreateCycle(edges, 'd', 'b')).toBe(true);
  });

  it('does not reject a fan-out (one blocker, many blocked)', () => {
    const edges = [
      { blocker_task_id: 'a', blocked_task_id: 'b' },
      { blocker_task_id: 'a', blocked_task_id: 'c' },
    ];
    expect(wouldCreateCycle(edges, 'a', 'd')).toBe(false);
  });

  it('does not reject a fan-in (many blockers, one blocked)', () => {
    const edges = [
      { blocker_task_id: 'a', blocked_task_id: 'd' },
      { blocker_task_id: 'b', blocked_task_id: 'd' },
    ];
    expect(wouldCreateCycle(edges, 'c', 'd')).toBe(false);
  });

  it('handles disconnected components without false positives', () => {
    const edges = [
      { blocker_task_id: 'x', blocked_task_id: 'y' },
      { blocker_task_id: 'y', blocked_task_id: 'z' },
    ];
    expect(wouldCreateCycle(edges, 'a', 'b')).toBe(false);
  });

  it('does not stall on a pre-existing cycle in the input graph', () => {
    // b → c, c → b is already a cycle — the walk must terminate via
    // MAX_DEPTH or visited set rather than loop forever.
    const edges = [
      { blocker_task_id: 'b', blocked_task_id: 'c' },
      { blocker_task_id: 'c', blocked_task_id: 'b' },
    ];
    // Proposing a → d where neither node touches the cycle should be
    // accepted — and crucially must return.
    expect(wouldCreateCycle(edges, 'a', 'd')).toBe(false);
  });

  it('exposes MAX_DEPTH as a public constant', () => {
    expect(MAX_DEPTH).toBe(10_000);
  });
});
