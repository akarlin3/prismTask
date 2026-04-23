import { create } from 'zustand';
import { doc, getDoc, setDoc } from 'firebase/firestore';
import { firestore } from '@/lib/firebase';

/**
 * Onboarding completion state.
 *
 * Per-account (not per-device) by product requirement — a user who
 * completes onboarding on one device should not see it again on
 * another. We persist `onboardingCompletedAt` at `users/{uid}` in
 * Firestore because that is the only write path the web client has
 * today that reaches all devices; the backend User model has no
 * equivalent field.
 *
 * The `users/{uid}` document is not yet written by any other web
 * code, but subcollections under it (e.g. `users/{uid}/tasks`) are
 * written freely. Production Firestore rules should permit writes to
 * the parent doc for the authenticated user; if not, onboarding
 * persistence degrades gracefully to "shown again on next sign-in" —
 * the UI never hard-fails.
 */

export type OnboardingStatus = 'unknown' | 'pending' | 'completed';

function userDoc(uid: string) {
  return doc(firestore, 'users', uid);
}

interface OnboardingState {
  status: OnboardingStatus;
  completedAt: number | null;
  /** Fetch the user doc and update status. Safe to call repeatedly —
   *  it is idempotent. */
  hydrate: (uid: string) => Promise<void>;
  /** Mark onboarding complete for `uid`. Writes the Firestore doc and
   *  flips local state so the onboarding gate releases. */
  markCompleted: (uid: string) => Promise<void>;
  /** Local-only reset, for logout/test cleanup — does not touch
   *  Firestore. */
  reset: () => void;
}

export const useOnboardingStore = create<OnboardingState>((set) => ({
  status: 'unknown',
  completedAt: null,

  hydrate: async (uid) => {
    set({ status: 'unknown' });
    try {
      const snap = await getDoc(userDoc(uid));
      const at = snap.exists() ? (snap.data().onboardingCompletedAt as number | undefined) : undefined;
      if (at && Number.isFinite(at)) {
        set({ status: 'completed', completedAt: at });
      } else {
        set({ status: 'pending', completedAt: null });
      }
    } catch {
      // If the read fails (rules, network), treat as pending so the
      // user still sees onboarding. Completion write will retry; if
      // that also fails, the user sees onboarding again but no crash.
      set({ status: 'pending', completedAt: null });
    }
  },

  markCompleted: async (uid) => {
    const now = Date.now();
    // Optimistic: release the gate first so navigation feels instant.
    set({ status: 'completed', completedAt: now });
    try {
      // `merge: true` because other subsystems may later add fields to
      // this doc (display name, preferences, etc.) — we must not
      // clobber them.
      await setDoc(userDoc(uid), { onboardingCompletedAt: now }, { merge: true });
    } catch {
      // Persistence failed. Leave local state as completed so the user
      // finishes their current session without seeing onboarding again
      // immediately; on next sign-in the hydrate read will retry and
      // redirect back to onboarding.
    }
  },

  reset: () => set({ status: 'unknown', completedAt: null }),
}));
