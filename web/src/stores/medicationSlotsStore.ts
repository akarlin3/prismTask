import { create } from 'zustand';
import type { Unsubscribe } from 'firebase/firestore';
import * as firestoreMedicationSlots from '@/api/firestore/medicationSlots';
import type { MedicationSlotDef } from '@/api/firestore/medicationSlots';

/**
 * Live cache of the current user's medication slot definitions.
 *
 * Populated by the Firestore real-time listener wired from `App.tsx`
 * (see `useFirestoreSync`). Components that need slot defs should
 * prefer reading from this store so cross-device edits land without a
 * page refresh. The existing imperative reads in
 * `MedicationScreen` / `MedicationSlotEditor` still work — this store
 * is additive and exists to back the live-sync path.
 */
interface MedicationSlotsState {
  slotDefs: MedicationSlotDef[];

  /** Apply a remote snapshot. Last-write wins (Firestore is the source
   *  of truth on web; LWW timestamp guards are a separate G.0 item). */
  applyRemoteSlotDefs: (defs: MedicationSlotDef[]) => void;

  /** Wire Firestore real-time listener. Returns a cleanup function. */
  subscribeToSlotDefs: (uid: string) => Unsubscribe;

  /** Reset to empty state (used on sign-out). */
  reset: () => void;
}

export const useMedicationSlotsStore = create<MedicationSlotsState>((set) => ({
  slotDefs: [],

  applyRemoteSlotDefs: (defs) => set({ slotDefs: defs }),

  subscribeToSlotDefs: (uid) => {
    return firestoreMedicationSlots.subscribeToSlotDefs(uid, (defs) => {
      set({ slotDefs: defs });
    });
  },

  reset: () => set({ slotDefs: [] }),
}));
