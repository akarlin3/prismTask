import {
  doc,
  getDoc,
  onSnapshot,
  setDoc,
  type DocumentData,
  type Unsubscribe,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';
import type { MedicationReminderMode } from './medicationSlots';

/**
 * User-level medication preferences. Stored at
 * `users/{uid}/medication_preferences/global` so a single setDoc with
 * merge=true round-trips both the mode and the interval default.
 *
 * Web is settings-only — Android delivers the actual reminders. The
 * banner on the medication-reminder-mode settings page makes that
 * explicit. When Web Push lands (Phase G) the same Firestore fields
 * back the web reminder pipeline.
 */

export const REMINDER_INTERVAL_MIN_MINUTES = 60;
export const REMINDER_INTERVAL_MAX_MINUTES = 1440;

export interface MedicationReminderModePreferences {
  mode: MedicationReminderMode;
  interval_default_minutes: number;
}

export const DEFAULT_REMINDER_MODE_PREFERENCES: MedicationReminderModePreferences = {
  mode: 'CLOCK',
  interval_default_minutes: 240,
};

const GLOBAL_DOC_ID = 'global';

function prefsDoc(uid: string) {
  return doc(firestore, 'users', uid, 'medication_preferences', GLOBAL_DOC_ID);
}

function clampInterval(minutes: number): number {
  if (!Number.isFinite(minutes)) return DEFAULT_REMINDER_MODE_PREFERENCES.interval_default_minutes;
  return Math.min(
    REMINDER_INTERVAL_MAX_MINUTES,
    Math.max(REMINDER_INTERVAL_MIN_MINUTES, Math.round(minutes)),
  );
}

function docToPrefs(data: DocumentData | undefined): MedicationReminderModePreferences {
  if (!data) return DEFAULT_REMINDER_MODE_PREFERENCES;
  const mode: MedicationReminderMode =
    data.reminderModeDefault === 'INTERVAL' ? 'INTERVAL' : 'CLOCK';
  const interval = clampInterval(
    typeof data.reminderIntervalDefaultMinutes === 'number'
      ? data.reminderIntervalDefaultMinutes
      : DEFAULT_REMINDER_MODE_PREFERENCES.interval_default_minutes,
  );
  return { mode, interval_default_minutes: interval };
}

export async function getReminderModePreferences(
  uid: string,
): Promise<MedicationReminderModePreferences> {
  const snap = await getDoc(prefsDoc(uid));
  return docToPrefs(snap.exists() ? snap.data() : undefined);
}

export async function setReminderModePreferences(
  uid: string,
  prefs: MedicationReminderModePreferences,
): Promise<void> {
  await setDoc(
    prefsDoc(uid),
    {
      reminderModeDefault: prefs.mode,
      reminderIntervalDefaultMinutes: clampInterval(prefs.interval_default_minutes),
      updatedAt: Date.now(),
    },
    { merge: true },
  );
}

export function subscribeToReminderModePreferences(
  uid: string,
  cb: (prefs: MedicationReminderModePreferences) => void,
): Unsubscribe {
  return onSnapshot(prefsDoc(uid), (snap) =>
    cb(docToPrefs(snap.exists() ? snap.data() : undefined)),
  );
}
