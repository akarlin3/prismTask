import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { calculateStreaks, buildCompletionGrid } from '@/utils/streaks';
import { format, subDays } from 'date-fns';

function toDateStr(d: Date): string {
  return format(d, 'yyyy-MM-dd');
}

describe('streaks utils', () => {
  const fixedNow = new Date('2026-04-12T12:00:00Z');

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(fixedNow);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('calculateStreaks - daily frequency', () => {
    it('returns zero streaks with no completions', () => {
      const result = calculateStreaks([], 'daily', null, 1);
      expect(result.currentStreak).toBe(0);
      expect(result.longestStreak).toBe(0);
      expect(result.totalCompletions).toBe(0);
    });

    it('computes current streak from consecutive days ending today', () => {
      const completions = [
        { date: '2026-04-12', count: 1 }, // today (Sunday)
        { date: '2026-04-11', count: 1 }, // yesterday (Saturday)
        { date: '2026-04-10', count: 1 }, // Friday
        { date: '2026-04-09', count: 1 }, // Thursday
      ];
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.currentStreak).toBe(4);
    });

    it('current streak starts from yesterday if today is not completed', () => {
      const completions = [
        { date: '2026-04-11', count: 1 },
        { date: '2026-04-10', count: 1 },
        { date: '2026-04-09', count: 1 },
      ];
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.currentStreak).toBe(3);
    });

    it('streak breaks on a missed day', () => {
      const completions = [
        { date: '2026-04-12', count: 1 },
        { date: '2026-04-11', count: 1 },
        // April 10 missed
        { date: '2026-04-09', count: 1 },
        { date: '2026-04-08', count: 1 },
      ];
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.currentStreak).toBe(2); // Only April 11-12
    });

    it('computes longest streak correctly', () => {
      // Old 5-day streak, then gap, then current 2-day
      const completions = [
        { date: '2026-04-12', count: 1 },
        { date: '2026-04-11', count: 1 },
        // gap
        { date: '2026-04-05', count: 1 },
        { date: '2026-04-04', count: 1 },
        { date: '2026-04-03', count: 1 },
        { date: '2026-04-02', count: 1 },
        { date: '2026-04-01', count: 1 },
      ];
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.currentStreak).toBe(2);
      expect(result.longestStreak).toBe(5);
    });

    it('respects active days filter', () => {
      // Only active on weekdays (Mon=1, Tue=2, Wed=3, Thu=4, Fri=5 in ISO)
      const activeDays = [1, 2, 3, 4, 5];
      const completions = [
        { date: '2026-04-10', count: 1 }, // Friday
        { date: '2026-04-09', count: 1 }, // Thursday
        { date: '2026-04-08', count: 1 }, // Wednesday
        // Sat/Sun are not active, so streak should continue through the weekend
      ];
      const result = calculateStreaks(completions, 'daily', activeDays, 1);
      // Today is Sunday (not active), yesterday is Saturday (not active)
      // Most recent active day is Friday April 10 which is completed
      expect(result.currentStreak).toBe(3);
    });

    it('handles target count > 1', () => {
      const completions = [
        { date: '2026-04-12', count: 2 },
        { date: '2026-04-11', count: 3 },
        { date: '2026-04-10', count: 1 }, // Only 1, target is 2 => break
      ];
      const result = calculateStreaks(completions, 'daily', null, 2);
      expect(result.currentStreak).toBe(2); // Only Apr 11-12 meet target
    });

    it('calculates totalCompletions correctly', () => {
      const completions = [
        { date: '2026-04-12', count: 2 },
        { date: '2026-04-11', count: 1 },
        { date: '2026-04-10', count: 3 },
      ];
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.totalCompletions).toBe(6);
    });

    it('calculates completion rates for 7 days', () => {
      // Complete all 7 days
      const completions = [];
      for (let i = 0; i < 7; i++) {
        completions.push({
          date: toDateStr(subDays(fixedNow, i)),
          count: 1,
        });
      }
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.completionRate7Day).toBe(1); // 100%
    });

    it('calculates partial completion rates', () => {
      // Complete only 3 of last 7 days
      const completions = [
        { date: '2026-04-12', count: 1 },
        { date: '2026-04-10', count: 1 },
        { date: '2026-04-08', count: 1 },
      ];
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.completionRate7Day).toBeCloseTo(3 / 7, 2);
    });

    it('identifies best and worst day', () => {
      // Completions on every day of the week so best/worst are deterministic
      const completions = [
        { date: '2026-04-12', count: 1 }, // Sunday
        { date: '2026-04-11', count: 2 }, // Saturday
        { date: '2026-04-10', count: 3 }, // Friday
        { date: '2026-04-09', count: 4 }, // Thursday
        { date: '2026-04-08', count: 7 }, // Wednesday - best
        { date: '2026-04-07', count: 5 }, // Tuesday
        { date: '2026-04-06', count: 6 }, // Monday
      ];
      const result = calculateStreaks(completions, 'daily', null, 1);
      expect(result.bestDay).toBe('Wednesday');
      // Sunday has the lowest count (1)
      expect(result.worstDay).toBe('Sunday');
    });
  });

  describe('calculateStreaks - weekly frequency', () => {
    it('returns zero streaks with no completions', () => {
      const result = calculateStreaks([], 'weekly', null, 3);
      expect(result.currentStreak).toBe(0);
      expect(result.longestStreak).toBe(0);
    });

    it('counts consecutive weeks meeting the target', () => {
      // Week of April 6 (Mon) - April 12 (Sun)
      // Week of March 30 (Mon) - April 5 (Sun)
      const completions = [
        { date: '2026-04-12', count: 2 },
        { date: '2026-04-10', count: 1 }, // This week: 3 total
        { date: '2026-04-03', count: 2 },
        { date: '2026-04-01', count: 1 }, // Previous week: 3 total
      ];
      const result = calculateStreaks(completions, 'weekly', null, 3);
      expect(result.currentStreak).toBe(2);
    });

    it('breaks streak when weekly target not met', () => {
      const completions = [
        { date: '2026-04-12', count: 3 }, // This week: 3 (meets target)
        // Previous week: no completions
        { date: '2026-03-27', count: 3 }, // Two weeks ago: 3 (meets target)
      ];
      const result = calculateStreaks(completions, 'weekly', null, 3);
      expect(result.currentStreak).toBe(1);
    });

    it('completionRate7Day is 1 when current week meets target', () => {
      const completions = [
        { date: '2026-04-12', count: 3 },
      ];
      const result = calculateStreaks(completions, 'weekly', null, 3);
      expect(result.completionRate7Day).toBe(1);
    });

    it('completionRate7Day is 0 when current week does not meet target', () => {
      const completions = [
        { date: '2026-04-12', count: 1 },
      ];
      const result = calculateStreaks(completions, 'weekly', null, 3);
      expect(result.completionRate7Day).toBe(0);
    });
  });

  describe('buildCompletionGrid', () => {
    it('builds a map with correct number of entries', () => {
      const grid = buildCompletionGrid([], 7);
      expect(grid.size).toBe(7);
    });

    it('initializes all entries to 0', () => {
      const grid = buildCompletionGrid([], 5);
      for (const value of grid.values()) {
        expect(value).toBe(0);
      }
    });

    it('includes today as the most recent entry', () => {
      const grid = buildCompletionGrid([], 3);
      const today = toDateStr(fixedNow);
      expect(grid.has(today)).toBe(true);
    });

    it('populates completion counts from data', () => {
      const today = toDateStr(fixedNow);
      const yesterday = toDateStr(subDays(fixedNow, 1));
      const completions = [
        { date: today, count: 3 },
        { date: yesterday, count: 2 },
      ];
      const grid = buildCompletionGrid(completions, 7);
      expect(grid.get(today)).toBe(3);
      expect(grid.get(yesterday)).toBe(2);
    });

    it('aggregates multiple entries for the same date', () => {
      const today = toDateStr(fixedNow);
      const completions = [
        { date: today, count: 2 },
        { date: today, count: 3 },
      ];
      const grid = buildCompletionGrid(completions, 7);
      expect(grid.get(today)).toBe(5);
    });

    it('ignores completions outside the date range', () => {
      const oldDate = '2025-01-01';
      const completions = [{ date: oldDate, count: 5 }];
      const grid = buildCompletionGrid(completions, 7);
      expect(grid.has(oldDate)).toBe(false);
    });

    it('covers the correct date range', () => {
      const grid = buildCompletionGrid([], 3);
      const keys = Array.from(grid.keys());
      expect(keys).toEqual([
        toDateStr(subDays(fixedNow, 2)),
        toDateStr(subDays(fixedNow, 1)),
        toDateStr(fixedNow),
      ]);
    });
  });
});
