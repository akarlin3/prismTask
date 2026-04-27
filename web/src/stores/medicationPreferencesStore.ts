import { create } from 'zustand';
import type { Unsubscribe } from 'firebase/firestore';
import {
  DEFAULT_REMINDER_MODE_PREFERENCES,
  subscribeToReminderModePreferences,
  type MedicationReminderModePreferences,
} from '@/api/firestore/medicationPreferences';

/**
 * Live cache of the current user's medication reminder-mode preferences.
 *
 * Populated by the Firestore real-time listener wired from `App.tsx`
 * (see `useFirestoreSync`). The existing
 * `MedicationReminderModeSection` settings UI still does an imperative
 * `getReminderModePreferences()` to keep its local form state — this
 * store is additive so other surfaces (slot editor inheritance,
 * future Web Push pipeline) can read the latest prefs without their
 * own one-shot fetch.
 */
interface MedicationPreferencesState {
  prefs: MedicationReminderModePreferences;

  /** Apply a remote snapshot. Last-write wins. */
  applyRemotePreferences: (prefs: MedicationReminderModePreferences) => void;

  /** Wire Firestore real-time listener. Returns a cleanup function. */
  subscribeToPreferences: (uid: string) => Unsubscribe;

  /** Reset to defaults (used on sign-out). */
  reset: () => void;
}

export const useMedicationPreferencesStore = create<MedicationPreferencesState>(
  (set) => ({
    prefs: DEFAULT_REMINDER_MODE_PREFERENCES,

    applyRemotePreferences: (prefs) => set({ prefs }),

    subscribeToPreferences: (uid) => {
      return subscribeToReminderModePreferences(uid, (prefs) => {
        set({ prefs });
      });
    },

    reset: () => set({ prefs: DEFAULT_REMINDER_MODE_PREFERENCES }),
  }),
);
