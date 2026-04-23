import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDocs,
  orderBy,
  query,
  updateDoc,
  where,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';

/**
 * Firestore-native mood & energy logs. Mirrors Android's
 * `MoodEnergyLogEntity` (`app/.../data/local/entity/MoodEnergyLogEntity.kt`)
 * without a backend round-trip — the backend has no mood endpoints.
 *
 * Stored at `users/{uid}/mood_energy_logs`. `dateIso` is the ISO
 * `YYYY-MM-DD` of the logical day the entry belongs to (not the
 * creation instant), so multi-entry days (morning + afternoon) are
 * possible and range queries are simple.
 */

export type TimeOfDay = 'morning' | 'afternoon' | 'evening' | 'night';

export interface MoodEnergyLog {
  id: string;
  date_iso: string;
  /** 1–5, with 5 = best. */
  mood: number;
  /** 1–5, with 5 = peak energy. */
  energy: number;
  notes: string;
  time_of_day: TimeOfDay;
  created_at: number;
  updated_at: number;
}

function logsCol(uid: string) {
  return collection(firestore, 'users', uid, 'mood_energy_logs');
}

function logDoc(uid: string, id: string) {
  return doc(firestore, 'users', uid, 'mood_energy_logs', id);
}

function clampScale(n: unknown): number {
  const v = typeof n === 'number' ? Math.round(n) : 3;
  if (!Number.isFinite(v)) return 3;
  if (v < 1) return 1;
  if (v > 5) return 5;
  return v;
}

function docToLog(id: string, data: DocumentData): MoodEnergyLog {
  const tod = data.timeOfDay;
  const time_of_day: TimeOfDay =
    tod === 'afternoon' || tod === 'evening' || tod === 'night'
      ? tod
      : 'morning';
  return {
    id,
    date_iso: typeof data.dateIso === 'string' ? data.dateIso : '',
    mood: clampScale(data.mood),
    energy: clampScale(data.energy),
    notes: typeof data.notes === 'string' ? data.notes : '',
    time_of_day,
    created_at: typeof data.createdAt === 'number' ? data.createdAt : Date.now(),
    updated_at: typeof data.updatedAt === 'number' ? data.updatedAt : Date.now(),
  };
}

export interface MoodEnergyLogInput {
  date_iso: string;
  mood: number;
  energy: number;
  notes?: string;
  time_of_day?: TimeOfDay;
}

export async function createLog(
  uid: string,
  input: MoodEnergyLogInput,
): Promise<MoodEnergyLog> {
  const now = Date.now();
  const payload = {
    dateIso: input.date_iso,
    mood: clampScale(input.mood),
    energy: clampScale(input.energy),
    notes: input.notes ?? '',
    timeOfDay: input.time_of_day ?? 'morning',
    createdAt: now,
    updatedAt: now,
  };
  const ref = await addDoc(logsCol(uid), payload);
  return docToLog(ref.id, payload);
}

export async function updateLog(
  uid: string,
  id: string,
  input: Partial<MoodEnergyLogInput>,
): Promise<void> {
  const payload: Record<string, unknown> = { updatedAt: Date.now() };
  if (input.date_iso !== undefined) payload.dateIso = input.date_iso;
  if (input.mood !== undefined) payload.mood = clampScale(input.mood);
  if (input.energy !== undefined) payload.energy = clampScale(input.energy);
  if (input.notes !== undefined) payload.notes = input.notes;
  if (input.time_of_day !== undefined) payload.timeOfDay = input.time_of_day;
  await updateDoc(logDoc(uid, id), payload);
}

export async function deleteLog(uid: string, id: string): Promise<void> {
  await deleteDoc(logDoc(uid, id));
}

export async function getLogsInRange(
  uid: string,
  startIso: string,
  endIso: string,
): Promise<MoodEnergyLog[]> {
  const snap = await getDocs(
    query(
      logsCol(uid),
      where('dateIso', '>=', startIso),
      where('dateIso', '<=', endIso),
      orderBy('dateIso', 'asc'),
      orderBy('createdAt', 'asc'),
    ),
  );
  return snap.docs.map((d) => docToLog(d.id, d.data()));
}
