import type { MoodEnergyLog } from '@/api/firestore/moodEnergyLogs';

/**
 * Pure aggregation helpers for mood / energy analytics. No Firestore
 * access here — the caller passes a range of logs and we produce the
 * per-day series + headline stats. Mirrors the Android
 * `MoodCorrelationEngine` outputs at a shape level (average,
 * best/worst days) without the correlation-with-tasks logic, which
 * is a Phase G follow-up.
 */

export interface MoodDailyPoint {
  date_iso: string;
  avg_mood: number;
  avg_energy: number;
  count: number;
}

export interface MoodStats {
  total_logs: number;
  overall_avg_mood: number;
  overall_avg_energy: number;
  best_day: { date_iso: string; mood: number } | null;
  worst_day: { date_iso: string; mood: number } | null;
  /** "up" / "down" / "stable" based on the first vs. last third of the range. */
  mood_trend: 'up' | 'down' | 'stable';
}

export function rollupByDay(logs: MoodEnergyLog[]): MoodDailyPoint[] {
  const bucket = new Map<
    string,
    { moodSum: number; energySum: number; count: number }
  >();
  for (const log of logs) {
    const b = bucket.get(log.date_iso) ?? { moodSum: 0, energySum: 0, count: 0 };
    b.moodSum += log.mood;
    b.energySum += log.energy;
    b.count += 1;
    bucket.set(log.date_iso, b);
  }
  return Array.from(bucket.entries())
    .map(([date_iso, b]) => ({
      date_iso,
      avg_mood: b.count === 0 ? 0 : b.moodSum / b.count,
      avg_energy: b.count === 0 ? 0 : b.energySum / b.count,
      count: b.count,
    }))
    .sort((a, b) => a.date_iso.localeCompare(b.date_iso));
}

export function computeStats(logs: MoodEnergyLog[]): MoodStats {
  if (logs.length === 0) {
    return {
      total_logs: 0,
      overall_avg_mood: 0,
      overall_avg_energy: 0,
      best_day: null,
      worst_day: null,
      mood_trend: 'stable',
    };
  }

  const moodSum = logs.reduce((acc, l) => acc + l.mood, 0);
  const energySum = logs.reduce((acc, l) => acc + l.energy, 0);
  const overallAvgMood = moodSum / logs.length;
  const overallAvgEnergy = energySum / logs.length;

  const byDay = rollupByDay(logs);
  const best = byDay.reduce<MoodDailyPoint | null>(
    (acc, d) => (acc === null || d.avg_mood > acc.avg_mood ? d : acc),
    null,
  );
  const worst = byDay.reduce<MoodDailyPoint | null>(
    (acc, d) => (acc === null || d.avg_mood < acc.avg_mood ? d : acc),
    null,
  );

  // Trend: compare avg mood of the first third vs. the last third of
  // the day range. Requires at least 3 days of data to have signal.
  let trend: 'up' | 'down' | 'stable' = 'stable';
  if (byDay.length >= 3) {
    const third = Math.max(1, Math.floor(byDay.length / 3));
    const early =
      byDay.slice(0, third).reduce((a, d) => a + d.avg_mood, 0) / third;
    const late =
      byDay.slice(-third).reduce((a, d) => a + d.avg_mood, 0) / third;
    const delta = late - early;
    if (delta >= 0.3) trend = 'up';
    else if (delta <= -0.3) trend = 'down';
  }

  return {
    total_logs: logs.length,
    overall_avg_mood: overallAvgMood,
    overall_avg_energy: overallAvgEnergy,
    best_day: best
      ? { date_iso: best.date_iso, mood: best.avg_mood }
      : null,
    worst_day: worst
      ? { date_iso: worst.date_iso, mood: worst.avg_mood }
      : null,
    mood_trend: trend,
  };
}
