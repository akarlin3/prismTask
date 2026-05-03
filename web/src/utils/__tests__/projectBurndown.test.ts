import { describe, it, expect } from 'vitest';
import { computeProjectBurndown } from '@/utils/projectBurndown';
import type { Task } from '@/types/task';
import type { Project } from '@/types/project';

function isoAt(y: number, m: number, d: number, h: number = 12): string {
  return new Date(y, m - 1, d, h).toISOString();
}

function task(partial: Partial<Task> & { id: string; project_id: string }): Task {
  return {
    id: partial.id,
    project_id: partial.project_id,
    user_id: 'uid-1',
    parent_id: null,
    title: partial.title ?? 't',
    description: null,
    notes: null,
    status: partial.status ?? 'todo',
    priority: 3,
    due_date: null,
    due_time: null,
    planned_date: null,
    completed_at: partial.completed_at ?? null,
    urgency_score: 0,
    recurrence_json: null,
    eisenhower_quadrant: null,
    eisenhower_updated_at: null,
    estimated_duration: null,
    actual_duration: null,
    sort_order: 0,
    depth: 0,
    created_at: partial.created_at ?? isoAt(2026, 4, 1),
    updated_at: partial.created_at ?? isoAt(2026, 4, 1),
    subtasks: [],
    tags: [],
    // PrismTask-timeline-class scope (PR-4): the burndown tests author
    // a per-task `progress_percent` via the partial; without explicit
    // propagation here it gets dropped and every fractional case
    // collapses to 0.
    progress_percent: partial.progress_percent ?? null,
    phase_id: partial.phase_id ?? null,
  };
}

const PROJECT: Pick<Project, 'id' | 'title' | 'due_date'> = {
  id: 'p1',
  title: 'P1',
  due_date: null,
};

describe('computeProjectBurndown', () => {
  it('returns totals + completed counts', () => {
    const tasks: Task[] = [
      task({ id: 't1', project_id: 'p1', status: 'done', completed_at: isoAt(2026, 4, 3) }),
      task({ id: 't2', project_id: 'p1', status: 'todo' }),
      task({ id: 't3', project_id: 'p1', status: 'cancelled' }),
      task({ id: 't4', project_id: 'other', status: 'done' }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-05',
    });
    expect(result.project_name).toBe('P1');
    expect(result.total_tasks).toBe(2); // cancelled excluded, other-project excluded
    expect(result.completed_tasks).toBe(1);
  });

  it('builds one burndown entry per day with expected counts', () => {
    const tasks: Task[] = [
      task({ id: 'a', project_id: 'p1', created_at: isoAt(2026, 4, 1), status: 'done', completed_at: isoAt(2026, 4, 3) }),
      task({ id: 'b', project_id: 'p1', created_at: isoAt(2026, 4, 2), status: 'todo' }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-04',
    });
    expect(result.burndown).toHaveLength(4);
    expect(result.burndown[0]).toMatchObject({
      date: '2026-04-01',
      remaining: 1,
      completed_cumulative: 0,
      added: 1,
    });
    expect(result.burndown[1]).toMatchObject({
      date: '2026-04-02',
      remaining: 2,
      completed_cumulative: 0,
      added: 1,
    });
    expect(result.burndown[2]).toMatchObject({
      date: '2026-04-03',
      remaining: 1,
      completed_cumulative: 1,
      added: 0,
    });
    expect(result.burndown[3]).toMatchObject({
      date: '2026-04-04',
      remaining: 1,
      completed_cumulative: 1,
      added: 0,
    });
  });

  it('marks project on track when no due date and remaining work outstanding', () => {
    const tasks: Task[] = [
      task({ id: 'a', project_id: 'p1', created_at: isoAt(2026, 4, 1), status: 'done', completed_at: isoAt(2026, 4, 2) }),
      task({ id: 'b', project_id: 'p1', created_at: isoAt(2026, 4, 1), status: 'todo' }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-05',
    });
    expect(result.velocity).toBeGreaterThan(0);
    expect(result.projected_completion).not.toBeNull();
    expect(result.is_on_track).toBe(true);
  });

  it('marks at risk when projected completion overruns the due date', () => {
    const proj: Pick<Project, 'id' | 'title' | 'due_date'> = {
      id: 'p1',
      title: 'P1',
      due_date: '2026-04-06',
    };
    // 1 of 10 done over a 5-day range → velocity 0.2/day → ETA 45 days out.
    const tasks: Task[] = Array.from({ length: 10 }, (_, i) =>
      task({
        id: `t${i}`,
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: i === 0 ? 'done' : 'todo',
        completed_at: i === 0 ? isoAt(2026, 4, 5) : null,
      }),
    );
    const result = computeProjectBurndown({
      project: proj,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-05',
    });
    expect(result.is_on_track).toBe(false);
    expect(result.projected_completion).not.toBeNull();
  });

  it('returns projected_completion = end when all tasks are already done', () => {
    const tasks: Task[] = [
      task({
        id: 'a',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'done',
        completed_at: isoAt(2026, 4, 2),
      }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-05',
    });
    expect(result.projected_completion).toBe('2026-04-05');
    expect(result.is_on_track).toBe(true);
  });

  // PrismTask-timeline-class scope, PR-4 (audit P9 option a).

  it('counts a fractional task as its progress percent / 100', () => {
    const tasks: Task[] = [
      task({
        id: 'a',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'in_progress',
        progress_percent: 60,
      }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-03',
    });
    // 60% of one task → 0.6 completed units, rounded to 0.6.
    expect(result.completed_tasks).toBe(0.6);
  });

  it('open fractional tasks contribute on the report last day only', () => {
    const tasks: Task[] = [
      task({
        id: 'a',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'in_progress',
        progress_percent: 40,
      }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-03',
    });
    // Days 1-2 carry the unit as "still 1 outstanding"; only the last
    // day reflects the 40% open progress.
    expect(result.burndown[0]).toMatchObject({
      date: '2026-04-01',
      completed_cumulative: 0,
    });
    expect(result.burndown[1]).toMatchObject({
      date: '2026-04-02',
      completed_cumulative: 0,
    });
    expect(result.burndown[2]).toMatchObject({
      date: '2026-04-03',
      completed_cumulative: 0.4,
    });
  });

  it('completed fractional tasks contribute their fractional value, not 1.0', () => {
    // A task that was marked DONE but carries progress_percent = 80
    // (e.g. user shipped at 80% and called it good enough) contributes
    // 0.8 — the explicit fractional value beats the binary status.
    const tasks: Task[] = [
      task({
        id: 'a',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'done',
        completed_at: isoAt(2026, 4, 2),
        progress_percent: 80,
      }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-03',
    });
    expect(result.completed_tasks).toBe(0.8);
    expect(result.burndown[1]).toMatchObject({
      date: '2026-04-02',
      completed_cumulative: 0.8,
    });
  });

  it('clamps out-of-range progress_percent to [0, 100]', () => {
    const tasks: Task[] = [
      task({
        id: 'low',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'in_progress',
        progress_percent: -50,
      }),
      task({
        id: 'high',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'in_progress',
        progress_percent: 250,
      }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-03',
    });
    // -50 clamps to 0, 250 clamps to 100 → 0 + 1 = 1.0 unit.
    expect(result.completed_tasks).toBe(1);
  });

  it('mixed binary + fractional tasks compose without breaking pre-PR-4 callers', () => {
    const tasks: Task[] = [
      // Legacy binary done task — progress_percent absent; treated as 1.
      task({
        id: 'legacy',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'done',
        completed_at: isoAt(2026, 4, 2),
      }),
      // Fractional in-progress — counted at 0.5 on the last day.
      task({
        id: 'fractional',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'in_progress',
        progress_percent: 50,
      }),
      // Plain todo — still 0.
      task({
        id: 'todo',
        project_id: 'p1',
        created_at: isoAt(2026, 4, 1),
        status: 'todo',
      }),
    ];
    const result = computeProjectBurndown({
      project: PROJECT,
      tasks,
      startIso: '2026-04-01',
      endIso: '2026-04-03',
    });
    expect(result.total_tasks).toBe(3);
    // 1.0 + 0.5 + 0 = 1.5
    expect(result.completed_tasks).toBe(1.5);
  });
});
