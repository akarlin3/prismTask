import {
  collection,
  doc,
  getDoc,
  getDocs,
  addDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  onSnapshot,
  type Unsubscribe,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';
import type { Habit, HabitCompletion } from '@/types/habit';
import { timestampToIso, timestampToDateStr } from './converters';

// ── Collection references ─────────────────────────────────────

function habitsCol(uid: string) {
  return collection(firestore, 'users', uid, 'habits');
}

function habitDoc(uid: string, habitId: string) {
  return doc(firestore, 'users', uid, 'habits', habitId);
}

function completionsCol(uid: string) {
  return collection(firestore, 'users', uid, 'habit_completions');
}

// ── Firestore doc → Web Habit ─────────────────────────────────

function docToHabit(docId: string, data: DocumentData, uid: string): Habit {
  return {
    id: docId,
    user_id: uid,
    name: data.name ?? '',
    description: data.description ?? null,
    icon: data.icon ?? '⭐',
    color: data.color ?? '#4A90D9',
    category: data.category ?? null,
    frequency: mapFrequency(data.frequencyPeriod),
    target_count: data.targetFrequency ?? 1,
    active_days_json: data.activeDays ?? null,
    is_active: !data.isArchived,
    created_at: timestampToIso(data.createdAt) ?? new Date().toISOString(),
    updated_at: timestampToIso(data.updatedAt) ?? new Date().toISOString(),
  };
}

function mapFrequency(period: string | undefined): 'daily' | 'weekly' {
  if (period === 'weekly') return 'weekly';
  return 'daily';
}

function docToCompletion(docId: string, data: DocumentData): HabitCompletion {
  // Prefer the timezone-neutral `completedDateLocal` field (Android v50,
  // migration 49→50). Fall back to deriving the date from the legacy
  // `completedDate` epoch for completions written by older clients.
  // Mirrors Android's `SyncMapper.mapToHabitCompletion`.
  const localKey =
    typeof data.completedDateLocal === 'string' && data.completedDateLocal.length > 0
      ? data.completedDateLocal
      : null;
  return {
    id: docId,
    habit_id: data.habitCloudId ?? '',
    date: localKey ?? timestampToDateStr(data.completedDate) ?? '',
    count: 1,
    created_at: timestampToIso(data.completedAt) ?? new Date().toISOString(),
  };
}

// ── Web Habit → Firestore doc ─────────────────────────────────

/**
 * Build the Firestore payload for a brand-new web-created habit.
 *
 * Web only owns ~10 of the 35+ fields on `HabitEntity` (see
 * `app/src/main/java/com/averycorp/prismtask/data/local/entity/HabitEntity.kt`).
 * The Android-only fields (booking, built-in identity, today-skip
 * windows, nag-suppression overrides, multi-reminder cadence,
 * source-version reconciliation) are intentionally **omitted** from
 * this payload rather than written as `false` / `null` defaults.
 *
 * Why omission instead of writing-defaults: cross-device sync. If the
 * user creates a habit on web, then on Android toggles `isBookable =
 * true`, then re-edits the habit on web, the next web-side `updateHabit`
 * also goes through this merge-only contract. A subsequent
 * Android-side pull then keeps `isBookable = true` because the web
 * payload never overwrote it. If web wrote `isBookable: false` here on
 * create (or in any later partial update), Android's flag would get
 * silently flipped back to `false` on the next sync round-trip.
 *
 * See parity audit `H-S2` (Surface 2 — habits write path).
 */
function habitCreateToDoc(data: {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
  category?: string;
  frequency?: string;
  target_count?: number;
  active_days_json?: string;
}): Record<string, unknown> {
  const now = Date.now();
  return {
    name: data.name,
    description: data.description ?? null,
    icon: data.icon ?? '⭐',
    color: data.color ?? '#4A90D9',
    category: data.category ?? null,
    targetFrequency: data.target_count ?? 1,
    frequencyPeriod: data.frequency ?? 'daily',
    activeDays: data.active_days_json ?? null,
    reminderTime: null,
    sortOrder: 0,
    isArchived: false,
    createDailyTask: false,
    createdAt: now,
    updatedAt: now,
    // NOTE: Android-only fields (`isBookable`, `isBooked`, `bookedDate`,
    // `bookedNote`, `trackBooking`, `trackPreviousPeriod`, `hasLogging`,
    // `showStreak`, `reminderTimesPerDay`, `reminderIntervalMillis`,
    // `nagSuppressionOverrideEnabled`, `nagSuppressionDaysOverride`,
    // `todaySkipAfterCompleteDays`, `todaySkipBeforeScheduleDays`,
    // `isBuiltIn`, `templateKey`, `sourceVersion`, `isUserModified`,
    // `isDetachedFromTemplate`) are intentionally OMITTED. Android's
    // `SyncMapper.mapToHabit` uses sensible defaults for missing keys.
  };
}

function habitUpdateToDoc(data: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = { updatedAt: Date.now() };
  if (data.name !== undefined) result.name = data.name;
  if (data.description !== undefined) result.description = data.description;
  if (data.icon !== undefined) result.icon = data.icon;
  if (data.color !== undefined) result.color = data.color;
  if (data.category !== undefined) result.category = data.category;
  if (data.frequency !== undefined) result.frequencyPeriod = data.frequency;
  if (data.target_count !== undefined) result.targetFrequency = data.target_count;
  if (data.active_days_json !== undefined) result.activeDays = data.active_days_json;
  if (data.is_active !== undefined) result.isArchived = !data.is_active;
  return result;
}

// ── CRUD operations ──────────────────────────────────────────

export async function getHabits(uid: string): Promise<Habit[]> {
  const q = query(habitsCol(uid), orderBy('createdAt', 'desc'));
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToHabit(d.id, d.data(), uid));
}

export async function getHabit(uid: string, habitId: string): Promise<Habit | null> {
  const snap = await getDoc(habitDoc(uid, habitId));
  if (!snap.exists()) return null;
  return docToHabit(snap.id, snap.data()!, uid);
}

export async function createHabit(
  uid: string,
  data: {
    name: string;
    description?: string;
    icon?: string;
    color?: string;
    category?: string;
    frequency?: string;
    target_count?: number;
    active_days_json?: string;
  },
): Promise<Habit> {
  const firestoreData = habitCreateToDoc(data);
  const ref = await addDoc(habitsCol(uid), firestoreData);
  return docToHabit(ref.id, firestoreData, uid);
}

export async function updateHabit(
  uid: string,
  habitId: string,
  data: Record<string, unknown>,
): Promise<Habit> {
  const firestoreData = habitUpdateToDoc(data);
  await updateDoc(habitDoc(uid, habitId), firestoreData);
  const snap = await getDoc(habitDoc(uid, habitId));
  return docToHabit(snap.id, snap.data()!, uid);
}

export async function deleteHabit(uid: string, habitId: string): Promise<void> {
  await deleteDoc(habitDoc(uid, habitId));
}

// ── Completions ──────────────────────────────────────────────

export async function getCompletions(
  uid: string,
  habitId: string,
  startDate?: string,
  endDate?: string,
): Promise<HabitCompletion[]> {
  const q = query(
    completionsCol(uid),
    where('habitCloudId', '==', habitId),
  );

  // Firestore doesn't allow inequality on different fields easily, so
  // we fetch all completions for the habit and filter client-side for date range
  const snap = await getDocs(q);
  let completions = snap.docs.map((d) => docToCompletion(d.id, d.data()));

  if (startDate) {
    completions = completions.filter((c) => c.date >= startDate);
  }
  if (endDate) {
    completions = completions.filter((c) => c.date <= endDate);
  }

  return completions;
}

export async function getAllCompletions(uid: string): Promise<HabitCompletion[]> {
  const snap = await getDocs(completionsCol(uid));
  return snap.docs.map((d) => docToCompletion(d.id, d.data()));
}

export async function toggleCompletion(
  uid: string,
  habitId: string,
  date: string,
): Promise<{ action: 'added' | 'removed'; completion?: HabitCompletion }> {
  // Check if completion already exists for this habit+date
  const dateMs = new Date(date + 'T00:00:00').getTime();
  const q = query(
    completionsCol(uid),
    where('habitCloudId', '==', habitId),
    where('completedDate', '==', dateMs),
  );
  const snap = await getDocs(q);

  if (snap.empty) {
    // Add a new completion. Populate the `completedDateLocal` field
    // (Android v50, migration 49→50) with the SoD-relative logical
    // day key the caller already computed via `useLogicalToday(...)`.
    // The `date` argument is the contractually correct value here:
    // callers in `HabitListScreen` / `TodayScreen` pass the user's
    // current logical day in `YYYY-MM-DD` form, which matches Android's
    // `DayBoundary` shape byte-for-byte. Without this, Android's
    // day-comparison drifts across DST transitions because the legacy
    // `completedDate` epoch decomposes back to a different calendar
    // day in the device's current timezone vs. the device that wrote
    // it. See parity audit H-S4.
    const now = Date.now();
    const firestoreData = {
      habitCloudId: habitId,
      completedDate: dateMs,
      completedDateLocal: date,
      completedAt: now,
      notes: null,
    };
    const ref = await addDoc(completionsCol(uid), firestoreData);
    const completion = docToCompletion(ref.id, firestoreData);
    return { action: 'added', completion };
  } else {
    // Remove the existing completion(s)
    for (const d of snap.docs) {
      await deleteDoc(d.ref);
    }
    return { action: 'removed' };
  }
}

// ── Real-time listeners ──────────────────────────────────────

export function subscribeToHabits(
  uid: string,
  callback: (habits: Habit[]) => void,
): Unsubscribe {
  const q = query(habitsCol(uid), orderBy('createdAt', 'desc'));
  return onSnapshot(q, (snap) => {
    const habits = snap.docs.map((d) => docToHabit(d.id, d.data(), uid));
    callback(habits);
  });
}

export function subscribeToCompletions(
  uid: string,
  callback: (completions: HabitCompletion[]) => void,
): Unsubscribe {
  return onSnapshot(completionsCol(uid), (snap) => {
    const completions = snap.docs.map((d) => docToCompletion(d.id, d.data()));
    callback(completions);
  });
}
