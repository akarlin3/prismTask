import { describe, it, expect } from 'vitest';
import { isBacklogged } from '@/features/medication/backloggedHelpers';

/**
 * Tests for the `isBacklogged` predicate that drives the
 * backlogged-indicator clock icon on the tier picker. Mirrors the
 * Android `MedicationSlotTodayState.isBacklogged` cases so cross-platform
 * behaviour stays in lockstep — same 60s tolerance window.
 */
describe('isBacklogged', () => {
  it('returns false when intended_time is null', () => {
    expect(isBacklogged(null, 1_000_000)).toBe(false);
  });

  it('returns false when logged_at is null', () => {
    expect(isBacklogged(1_000_000, null)).toBe(false);
  });

  it('returns false when difference is within the 60s tolerance', () => {
    expect(isBacklogged(1_000_000, 1_030_000)).toBe(false);
  });

  it('returns true when logged_at exceeds intended_time by > 60s', () => {
    // Took meds at 8:00, didn't log until 8:02 — backlogged.
    expect(isBacklogged(1_000_000, 1_120_000)).toBe(true);
  });

  it('returns true symmetrically when intended_time > logged_at by > 60s', () => {
    // Defensive — forward-dating is capped server-side, but the flag
    // logic must stay symmetric in case something slips through.
    expect(isBacklogged(1_120_000, 1_000_000)).toBe(true);
  });

  it('returns false when intended_time and logged_at are equal', () => {
    expect(isBacklogged(1_000_000, 1_000_000)).toBe(false);
  });
});
