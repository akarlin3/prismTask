import {
  collection,
  doc,
  getDoc,
  getDocs,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';

/**
 * Read-only access to medication documents the Android client syncs at
 * `users/{uid}/medications/{cloudId}`. Web does not currently write to
 * this collection — it only needs name + displayLabel + archived flag for
 * the batch ops disambiguation picker. Schema mirrors
 * `MedicationSyncMapper.medicationToMap` on Android.
 */

export interface MedicationDoc {
  /** Firestore document id (Android calls this the `cloudId`). */
  id: string;
  name: string;
  display_label: string | null;
  is_archived: boolean;
}

function medicationsCol(uid: string) {
  return collection(firestore, 'users', uid, 'medications');
}

function docToMedication(docId: string, data: DocumentData): MedicationDoc {
  return {
    id: docId,
    name: typeof data.name === 'string' ? data.name : '',
    display_label:
      typeof data.displayLabel === 'string' && data.displayLabel.length > 0
        ? data.displayLabel
        : null,
    is_archived: data.isArchived === true,
  };
}

export async function getMedications(uid: string): Promise<MedicationDoc[]> {
  const snap = await getDocs(medicationsCol(uid));
  return snap.docs
    .map((d) => docToMedication(d.id, d.data()))
    .filter((m) => !m.is_archived && m.name.length > 0);
}

export async function getMedicationsByIds(
  uid: string,
  ids: readonly string[],
): Promise<MedicationDoc[]> {
  if (ids.length === 0) return [];
  const unique = [...new Set(ids)];
  const results: MedicationDoc[] = [];
  for (const id of unique) {
    const snap = await getDoc(doc(firestore, 'users', uid, 'medications', id));
    if (!snap.exists()) continue;
    const med = docToMedication(snap.id, snap.data());
    if (!med.is_archived && med.name.length > 0) results.push(med);
  }
  return results;
}
