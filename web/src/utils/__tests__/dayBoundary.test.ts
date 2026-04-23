import { describe, it, expect } from 'vitest';
import {
  clampHour,
  endOfLogicalDayMs,
  logicalToday,
  startOfLogicalDayMs,
} from '@/utils/dayBoundary';

describe('dayBoundary', () => {
  it('clampHour keeps 0–23 and guards invalid input', () => {
    expect(clampHour(0)).toBe(0);
    expect(clampHour(23)).toBe(23);
    expect(clampHour(-1)).toBe(0);
    expect(clampHour(24)).toBe(23);
    expect(clampHour(Number.NaN)).toBe(0);
    expect(clampHour(5.4)).toBe(5);
    expect(clampHour(5.6)).toBe(6);
  });

  it('uses calendar midnight when SoD = 0 (default)', () => {
    // 2026-04-23 03:15 local.
    const now = new Date(2026, 3, 23, 3, 15);
    const start = startOfLogicalDayMs(now, 0);
    const expected = new Date(2026, 3, 23, 0, 0, 0, 0).getTime();
    expect(start).toBe(expected);
    expect(logicalToday(now, 0)).toBe('2026-04-23');
  });

  it('rolls back one calendar day when the moment is before SoD', () => {
    // 2026-04-23 03:15 local, SoD = 4 → logical day started 2026-04-22 04:00.
    const now = new Date(2026, 3, 23, 3, 15);
    const start = startOfLogicalDayMs(now, 4);
    const expected = new Date(2026, 3, 22, 4, 0, 0, 0).getTime();
    expect(start).toBe(expected);
    expect(logicalToday(now, 4)).toBe('2026-04-22');
  });

  it('uses today when the moment is after SoD', () => {
    // 2026-04-23 05:30 local, SoD = 4 → logical day started 2026-04-23 04:00.
    const now = new Date(2026, 3, 23, 5, 30);
    const start = startOfLogicalDayMs(now, 4);
    const expected = new Date(2026, 3, 23, 4, 0, 0, 0).getTime();
    expect(start).toBe(expected);
    expect(logicalToday(now, 4)).toBe('2026-04-23');
  });

  it('endOfLogicalDayMs is start + 24h', () => {
    const now = new Date(2026, 3, 23, 12, 0);
    expect(endOfLogicalDayMs(now, 0) - startOfLogicalDayMs(now, 0)).toBe(
      86_400_000,
    );
    expect(endOfLogicalDayMs(now, 4) - startOfLogicalDayMs(now, 4)).toBe(
      86_400_000,
    );
  });
});
