import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { computeUrgencyScore } from '@/utils/urgency';
import type { Task } from '@/types/task';

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: '1',
    project_id: '1',
    user_id: '1',
    parent_id: null,
    title: 'Test task',
    description: null,
    notes: null,
    status: 'todo',
    priority: 3,
    due_date: null,
    due_time: null,
    planned_date: null,
    completed_at: null,
    urgency_score: 0,
    recurrence_json: null,
    eisenhower_quadrant: null,
    eisenhower_updated_at: null,
    estimated_duration: null,
    actual_duration: null,
    sort_order: 0,
    depth: 0,
    created_at: '2026-04-12T00:00:00Z',
    updated_at: '2026-04-12T00:00:00Z',
    ...overrides,
  };
}

describe('urgency utils', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-04-12T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('computeUrgencyScore', () => {
    it('returns a score between 0 and 1', () => {
      const task = makeTask();
      const score = computeUrgencyScore(task);
      expect(score).toBeGreaterThanOrEqual(0);
      expect(score).toBeLessThanOrEqual(1);
    });

    it('gives higher score for overdue tasks', () => {
      const overdueTask = makeTask({ due_date: '2026-04-10', priority: 3 });
      const futureTask = makeTask({ due_date: '2026-04-20', priority: 3 });
      expect(computeUrgencyScore(overdueTask)).toBeGreaterThan(
        computeUrgencyScore(futureTask),
      );
    });

    it('gives higher score for tasks due soon vs tasks due far away', () => {
      const soonTask = makeTask({ due_date: '2026-04-14', priority: 3 });
      const farTask = makeTask({ due_date: '2026-04-19', priority: 3 });
      // Task due in 2 days should score higher than task due in 7 days
      expect(computeUrgencyScore(soonTask)).toBeGreaterThan(
        computeUrgencyScore(farTask),
      );
    });

    it('gives higher score for higher priority (lower number = higher priority)', () => {
      const urgentTask = makeTask({ priority: 1 });
      const lowTask = makeTask({ priority: 4 });
      expect(computeUrgencyScore(urgentTask)).toBeGreaterThan(
        computeUrgencyScore(lowTask),
      );
    });

    it('gives higher score for tasks with more subtask progress', () => {
      const progressTask = makeTask({
        subtasks: [
          makeTask({ status: 'done' }),
          makeTask({ status: 'done' }),
          makeTask({ status: 'todo' }),
        ],
      });
      const noProgressTask = makeTask({
        subtasks: [
          makeTask({ status: 'todo' }),
          makeTask({ status: 'todo' }),
          makeTask({ status: 'todo' }),
        ],
      });
      expect(computeUrgencyScore(progressTask)).toBeGreaterThan(
        computeUrgencyScore(noProgressTask),
      );
    });

    it('gives higher score for older tasks', () => {
      const oldTask = makeTask({ created_at: '2026-03-12T00:00:00Z' });
      const newTask = makeTask({ created_at: '2026-04-12T00:00:00Z' });
      expect(computeUrgencyScore(oldTask)).toBeGreaterThan(
        computeUrgencyScore(newTask),
      );
    });

    it('clamps score to maximum of 1', () => {
      // An overdue, urgent task with old age and subtask progress
      const maxTask = makeTask({
        priority: 1,
        due_date: '2026-04-01',
        created_at: '2026-02-01T00:00:00Z',
        subtasks: [
          makeTask({ status: 'done' }),
          makeTask({ status: 'done' }),
        ],
      });
      const score = computeUrgencyScore(maxTask);
      expect(score).toBeLessThanOrEqual(1);
    });

    it('clamps score to minimum of 0', () => {
      const task = makeTask();
      const score = computeUrgencyScore(task);
      expect(score).toBeGreaterThanOrEqual(0);
    });

    it('works with custom weights', () => {
      const task = makeTask({
        priority: 1,
        due_date: '2026-04-10', // overdue
      });

      // Only due date matters
      const dueDateOnlyWeights = {
        dueDateWeight: 1.0,
        priorityWeight: 0,
        ageWeight: 0,
        subtaskWeight: 0,
      };
      const dueDateScore = computeUrgencyScore(task, dueDateOnlyWeights);
      expect(dueDateScore).toBe(1.0); // Overdue = max

      // Only priority matters
      const priorityOnlyWeights = {
        dueDateWeight: 0,
        priorityWeight: 1.0,
        ageWeight: 0,
        subtaskWeight: 0,
      };
      const priorityScore = computeUrgencyScore(task, priorityOnlyWeights);
      // Priority 1 => (5-1)/4 = 1.0
      expect(priorityScore).toBe(1.0);
    });

    it('default weights sum components correctly', () => {
      // An overdue, urgent task created today with no subtasks
      const task = makeTask({
        priority: 1,
        due_date: '2026-04-10', // overdue
        created_at: '2026-04-12T00:00:00Z', // today = 0 age
      });

      const score = computeUrgencyScore(task);
      // dueDate: 0.4 * 1.0 = 0.4 (overdue)
      // priority: 0.3 * (5-1)/4 = 0.3 * 1.0 = 0.3
      // age: 0.2 * 0/30 = 0 (created today)
      // subtasks: 0 (no subtasks)
      expect(score).toBeCloseTo(0.7, 5);
    });

    it('handles task with no due date', () => {
      const task = makeTask({ due_date: null, priority: 3 });
      const score = computeUrgencyScore(task);
      // No due date contribution, only priority and age
      expect(score).toBeGreaterThanOrEqual(0);
      expect(score).toBeLessThanOrEqual(1);
    });

    it('handles task with due date more than 7 days away', () => {
      const task = makeTask({
        due_date: '2026-04-30',
        priority: 3,
        created_at: '2026-04-12T00:00:00Z',
      });
      const score = computeUrgencyScore(task);
      // dueDate: 0 (>7 days away)
      // priority: 0.3 * (5-3)/4 = 0.3 * 0.5 = 0.15
      // age: 0
      // subtasks: 0
      expect(score).toBeCloseTo(0.15, 5);
    });
  });
});
