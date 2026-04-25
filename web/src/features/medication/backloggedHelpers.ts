/**
 * Pure helpers for the medication time-logging "backlogged" flag.
 * Lives in its own file so React Fast Refresh stays clean — exporting
 * non-component utilities alongside a component breaks HMR.
 */

/**
 * Backlogged-indicator predicate. True when `intended_time` differs
 * from `logged_at` by more than 60 seconds. Mirrors Android's
 * `MedicationSlotTodayState.isBacklogged` — same 60s tolerance to
 * avoid flicker for the trivial gap between user action and DB write.
 */
export function isBacklogged(
  intendedTime: number | null,
  loggedAt: number | null,
): boolean {
  if (intendedTime === null || loggedAt === null) return false;
  return Math.abs(loggedAt - intendedTime) > 60_000;
}

/**
 * Compose an HH:mm string from an epoch-millis timestamp using the
 * device's local timezone. Used to seed the time picker in the modal.
 */
export function epochToHHMM(epochMillis: number): string {
  const d = new Date(epochMillis);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}
