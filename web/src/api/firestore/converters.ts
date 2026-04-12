/**
 * Conversion utilities between Firestore document format (Android)
 * and web app types.
 *
 * Firestore field names are camelCase (matching Android SyncMapper).
 * Timestamps are stored as milliseconds (Long) in Firestore.
 *
 * Priority mapping:
 *   Android: 0=None, 1=Low, 2=Medium, 3=High, 4=Urgent
 *   Web:     1=Urgent, 2=High, 3=Medium, 4=Low
 */

import { format } from 'date-fns';

// ── Timestamp helpers ──────────────────────────────────────────

export function timestampToDateStr(millis: number | null | undefined): string | null {
  if (millis == null) return null;
  return format(new Date(millis), 'yyyy-MM-dd');
}

export function timestampToTimeStr(millis: number | null | undefined): string | null {
  if (millis == null) return null;
  return format(new Date(millis), 'HH:mm');
}

export function timestampToIso(millis: number | null | undefined): string | null {
  if (millis == null) return null;
  return new Date(millis).toISOString();
}

export function dateStrToTimestamp(dateStr: string | null | undefined): number | null {
  if (!dateStr) return null;
  return new Date(dateStr + 'T00:00:00').getTime();
}

export function isoToTimestamp(iso: string | null | undefined): number | null {
  if (!iso) return null;
  return new Date(iso).getTime();
}

/** Start of today as millis */
export function startOfTodayMs(): number {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** End of today (start of tomorrow) as millis */
export function endOfTodayMs(): number {
  return startOfTodayMs() + 86_400_000;
}

/** Start of N days from now as millis */
export function startOfDaysFromNowMs(days: number): number {
  return startOfTodayMs() + days * 86_400_000;
}

// ── Priority helpers ───────────────────────────────────────────

export function androidToWebPriority(androidPri: number): 1 | 2 | 3 | 4 {
  // Android 4→Web 1, 3→2, 2→3, 1→4, 0→4
  if (androidPri >= 4) return 1;
  if (androidPri === 3) return 2;
  if (androidPri === 2) return 3;
  return 4;
}

export function webToAndroidPriority(webPri: number): number {
  // Web 1→Android 4, 2→3, 3→2, 4→1
  if (webPri <= 1) return 4;
  if (webPri === 2) return 3;
  if (webPri === 3) return 2;
  return 1;
}
