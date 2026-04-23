import { describe, it, expect } from 'vitest';
import { computeStats, rollupByDay } from '@/utils/moodAnalytics';
import type { MoodEnergyLog } from '@/api/firestore/moodEnergyLogs';

function log(
  date_iso: string,
  mood: number,
  energy: number,
  overrides: Partial<MoodEnergyLog> = {},
): MoodEnergyLog {
  return {
    id: `${date_iso}-${overrides.time_of_day ?? 'morning'}`,
    date_iso,
    mood,
    energy,
    notes: '',
    time_of_day: overrides.time_of_day ?? 'morning',
    created_at: 0,
    updated_at: 0,
    ...overrides,
  };
}

describe('moodAnalytics', () => {
  it('rolls up multiple logs per day into averages', () => {
    const logs: MoodEnergyLog[] = [
      log('2026-04-10', 3, 2, { time_of_day: 'morning' }),
      log('2026-04-10', 5, 4, { time_of_day: 'evening' }),
      log('2026-04-11', 2, 3, { time_of_day: 'morning' }),
    ];
    const days = rollupByDay(logs);
    expect(days).toHaveLength(2);
    expect(days[0]).toMatchObject({
      date_iso: '2026-04-10',
      avg_mood: 4,
      avg_energy: 3,
      count: 2,
    });
    expect(days[1]).toMatchObject({
      date_iso: '2026-04-11',
      avg_mood: 2,
      avg_energy: 3,
      count: 1,
    });
  });

  it('computes overall averages + best/worst', () => {
    const logs: MoodEnergyLog[] = [
      log('2026-04-10', 4, 3),
      log('2026-04-11', 2, 2),
      log('2026-04-12', 5, 4),
    ];
    const stats = computeStats(logs);
    expect(stats.total_logs).toBe(3);
    expect(stats.overall_avg_mood).toBeCloseTo((4 + 2 + 5) / 3, 5);
    expect(stats.best_day?.date_iso).toBe('2026-04-12');
    expect(stats.worst_day?.date_iso).toBe('2026-04-11');
  });

  it('classifies trend as up when recent days rise ≥0.3 over early days', () => {
    const logs: MoodEnergyLog[] = [
      log('2026-04-01', 2, 3),
      log('2026-04-02', 2, 3),
      log('2026-04-03', 2, 3),
      log('2026-04-04', 3, 3),
      log('2026-04-05', 4, 3),
      log('2026-04-06', 5, 3),
    ];
    expect(computeStats(logs).mood_trend).toBe('up');
  });

  it('classifies trend as down when recent days fall ≥0.3', () => {
    const logs: MoodEnergyLog[] = [
      log('2026-04-01', 5, 3),
      log('2026-04-02', 5, 3),
      log('2026-04-03', 5, 3),
      log('2026-04-04', 3, 3),
      log('2026-04-05', 2, 3),
      log('2026-04-06', 1, 3),
    ];
    expect(computeStats(logs).mood_trend).toBe('down');
  });

  it('classifies trend as stable when the delta is small or data is thin', () => {
    expect(computeStats([]).mood_trend).toBe('stable');
    const logs: MoodEnergyLog[] = [
      log('2026-04-01', 3, 3),
      log('2026-04-02', 3, 3),
    ];
    expect(computeStats(logs).mood_trend).toBe('stable');
  });

  it('returns zeroed stats for an empty log list', () => {
    const stats = computeStats([]);
    expect(stats.total_logs).toBe(0);
    expect(stats.overall_avg_mood).toBe(0);
    expect(stats.best_day).toBeNull();
    expect(stats.worst_day).toBeNull();
  });
});
