import { useEffect, useState } from 'react';
import { endOfLogicalDayMs, logicalToday } from '@/utils/dayBoundary';

/**
 * React hook that returns the user's current **logical** today as ISO
 * `YYYY-MM-DD`, advancing automatically when the wall-clock crosses the
 * Start-of-Day boundary.
 *
 * Replaces the broken
 * `const todayIso = logicalToday(Date.now(), startOfDayHour);` pattern at
 * component-render time, which snapshotted the date once and never
 * refreshed without an external trigger (store update, prop change). See
 * `docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md` (Phase 2 §4) for context.
 *
 * Implementation: schedules a one-shot `setTimeout` to fire at the next
 * logical-day boundary, then re-schedules. Cleans up on unmount and on
 * `startOfDayHour` change. Cheaper than a 1 Hz polling interval and more
 * accurate (the timeout duration is computed from `endOfLogicalDayMs`).
 */
export function useLogicalToday(startOfDayHour: number): string {
  const [todayIso, setTodayIso] = useState(() =>
    logicalToday(Date.now(), startOfDayHour),
  );

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | null = null;

    const tick = () => {
      if (cancelled) return;
      // Re-read both the date AND the next boundary on every wake-up so
      // we don't drift if the system clock or DST shifts during the
      // logical day.
      const now = Date.now();
      const next = logicalToday(now, startOfDayHour);
      setTodayIso((prev) => (prev === next ? prev : next));
      const nextBoundary = endOfLogicalDayMs(now, startOfDayHour);
      // Coerce to a positive minimum so a clock skew that puts the
      // boundary in the past still re-arms reliably.
      const delay = Math.max(1_000, nextBoundary - now);
      timer = setTimeout(tick, delay);
    };

    // Sync immediately in case the parent re-rendered for a reason that
    // also crossed the boundary (e.g. tab regained focus after a sleep).
    tick();

    return () => {
      cancelled = true;
      if (timer !== null) clearTimeout(timer);
    };
  }, [startOfDayHour]);

  return todayIso;
}
