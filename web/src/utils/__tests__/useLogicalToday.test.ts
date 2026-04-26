import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useLogicalToday } from '@/utils/useLogicalToday';

/**
 * Tests for the {@link useLogicalToday} React hook. Drives Vitest fake
 * timers + `vi.setSystemTime(...)` so the hook's internal `setTimeout`
 * fires at the simulated logical-day boundary and the component re-renders
 * with the new ISO date.
 */
describe('useLogicalToday', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns the current logical date on mount, anchored to user SoD', () => {
    // 2026-04-26 01:00 local with SoD = 4 → logical = 2026-04-25.
    vi.setSystemTime(new Date(2026, 3, 26, 1, 0));

    const { result } = renderHook(() => useLogicalToday(4));

    expect(result.current).toBe('2026-04-25');
  });

  it('advances to the next logical date when the wall-clock crosses SoD', () => {
    // 2026-04-25 23:00 local, SoD = 4 → logical = 2026-04-25.
    vi.setSystemTime(new Date(2026, 3, 25, 23, 0));

    const { result } = renderHook(() => useLogicalToday(4));
    expect(result.current).toBe('2026-04-25');

    // Advance 6 h → 2026-04-26 05:00 local, past SoD = 4.
    act(() => {
      vi.setSystemTime(new Date(2026, 3, 26, 5, 0));
      vi.advanceTimersByTime(6 * 60 * 60 * 1_000);
    });

    expect(result.current).toBe('2026-04-26');
  });

  it('does not re-render when the wall-clock advances within the same logical day', () => {
    // 2026-04-26 04:00 local, SoD = 4 → logical = 2026-04-26.
    vi.setSystemTime(new Date(2026, 3, 26, 4, 0));

    let renderCount = 0;
    const { result } = renderHook(() => {
      renderCount += 1;
      return useLogicalToday(4);
    });
    const initialRenders = renderCount;
    expect(result.current).toBe('2026-04-26');

    // Advance 23 h — still inside the same logical day (boundary at
    // 2026-04-27 04:00 local).
    act(() => {
      vi.setSystemTime(new Date(2026, 3, 27, 3, 0));
      vi.advanceTimersByTime(23 * 60 * 60 * 1_000);
    });

    expect(result.current).toBe('2026-04-26');
    // The setState call inside the tick is no-op when the value is
    // unchanged, so React skips the re-render.
    expect(renderCount).toBe(initialRenders);
  });

  it('re-keys when the startOfDayHour prop changes', () => {
    // 2026-04-26 02:00 local. SoD = 0 → logical = 2026-04-26.
    // Switching SoD to 4 → logical = 2026-04-25 (we're before the new SoD).
    vi.setSystemTime(new Date(2026, 3, 26, 2, 0));

    const { result, rerender } = renderHook(
      ({ sod }) => useLogicalToday(sod),
      { initialProps: { sod: 0 } },
    );
    expect(result.current).toBe('2026-04-26');

    rerender({ sod: 4 });

    expect(result.current).toBe('2026-04-25');
  });
});
