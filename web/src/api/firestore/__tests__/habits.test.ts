import { describe, it, expect, vi, beforeEach } from 'vitest';

const {
  addDocMock,
  updateDocMock,
  getDocMock,
  getDocsMock,
  docMock,
  collectionMock,
  queryMock,
  whereMock,
  deleteDocMock,
} = vi.hoisted(() => ({
  addDocMock: vi.fn(),
  updateDocMock: vi.fn(),
  getDocMock: vi.fn(),
  getDocsMock: vi.fn(),
  docMock: vi.fn(),
  collectionMock: vi.fn(),
  queryMock: vi.fn(),
  whereMock: vi.fn(),
  deleteDocMock: vi.fn(),
}));

vi.mock('firebase/firestore', () => ({
  addDoc: addDocMock,
  updateDoc: updateDocMock,
  getDoc: getDocMock,
  getDocs: getDocsMock,
  doc: docMock,
  collection: collectionMock,
  query: queryMock,
  where: whereMock,
  orderBy: vi.fn(),
  onSnapshot: vi.fn(),
  deleteDoc: deleteDocMock,
}));
vi.mock('@/lib/firebase', () => ({ firestore: {} }));

import {
  createHabit,
  updateHabit,
  toggleCompletion,
} from '@/api/firestore/habits';
import { logicalToday } from '@/utils/dayBoundary';

beforeEach(() => {
  addDocMock.mockReset();
  updateDocMock.mockReset();
  getDocMock.mockReset();
  getDocsMock.mockReset();
  docMock.mockReset();
  collectionMock.mockReset();
  queryMock.mockReset();
  whereMock.mockReset();
  deleteDocMock.mockReset();
  docMock.mockReturnValue({ id: 'doc-ref' });
  collectionMock.mockReturnValue({});
  queryMock.mockReturnValue({});
});

// ── Android-only fields the web app must NEVER hardcode on write
//    (otherwise it clobbers state owned by Android UI). ────────────
const ANDROID_ONLY_HABIT_FIELDS = [
  'isBookable',
  'isBooked',
  'bookedDate',
  'bookedNote',
  'trackBooking',
  'trackPreviousPeriod',
  'hasLogging',
  'showStreak',
  'reminderTimesPerDay',
  'reminderIntervalMillis',
  'nagSuppressionOverrideEnabled',
  'nagSuppressionDaysOverride',
  'todaySkipAfterCompleteDays',
  'todaySkipBeforeScheduleDays',
  'isBuiltIn',
  'templateKey',
  'sourceVersion',
  'isUserModified',
  'isDetachedFromTemplate',
] as const;

describe('createHabit (web → Firestore merge-on-write parity)', () => {
  it('does not write hardcoded Android-only field defaults when web does not supply them', async () => {
    addDocMock.mockResolvedValueOnce({ id: 'new-habit-id' });

    await createHabit('uid-1', {
      name: 'Hydrate',
      icon: '💧',
      color: '#06b6d4',
      frequency: 'daily',
      target_count: 1,
    });

    expect(addDocMock).toHaveBeenCalledTimes(1);
    const written = addDocMock.mock.calls[0][1] as Record<string, unknown>;

    // The fields web actually owns are still written:
    expect(written.name).toBe('Hydrate');
    expect(written.icon).toBe('💧');
    expect(written.color).toBe('#06b6d4');
    expect(written.frequencyPeriod).toBe('daily');
    expect(written.targetFrequency).toBe(1);

    // Every Android-only field must be ABSENT from the payload — not
    // present-but-false. A present-but-false write loses the user's
    // Android-side toggle on the next sync.
    for (const field of ANDROID_ONLY_HABIT_FIELDS) {
      expect(
        Object.prototype.hasOwnProperty.call(written, field),
        `expected createHabit payload to omit Android-only field "${field}", got ${JSON.stringify(written[field])}`,
      ).toBe(false);
    }
  });
});

describe('updateHabit (web → Firestore merge-on-write parity)', () => {
  beforeEach(() => {
    // updateHabit re-reads the doc after writing.
    getDocMock.mockResolvedValue({
      id: 'habit-1',
      data: () => ({
        name: 'Renamed',
        frequencyPeriod: 'daily',
        targetFrequency: 1,
        isArchived: false,
        createdAt: 1_700_000_000_000,
        updatedAt: 1_700_000_000_000,
      }),
    });
  });

  it('only writes the fields the web user actually edited', async () => {
    updateDocMock.mockResolvedValueOnce(undefined);

    await updateHabit('uid-1', 'habit-1', { name: 'Renamed' });

    expect(updateDocMock).toHaveBeenCalledTimes(1);
    const patch = updateDocMock.mock.calls[0][1] as Record<string, unknown>;

    // updatedAt is the only "always written" field besides the edit.
    expect(patch.name).toBe('Renamed');
    expect(typeof patch.updatedAt).toBe('number');

    // No Android-only fields should appear in the patch.
    for (const field of ANDROID_ONLY_HABIT_FIELDS) {
      expect(
        Object.prototype.hasOwnProperty.call(patch, field),
        `updateHabit patch must omit Android-only field "${field}"`,
      ).toBe(false);
    }

    // No web-edit-style fields the user didn't pass should appear.
    expect(Object.prototype.hasOwnProperty.call(patch, 'description')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(patch, 'icon')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(patch, 'color')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(patch, 'category')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(patch, 'frequencyPeriod')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(patch, 'targetFrequency')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(patch, 'activeDays')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(patch, 'isArchived')).toBe(false);
  });

  it('writes is_active by toggling the Android isArchived flag (web⇄Android polarity)', async () => {
    updateDocMock.mockResolvedValueOnce(undefined);

    await updateHabit('uid-1', 'habit-1', { is_active: false });

    const patch = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(patch.isArchived).toBe(true);
  });
});

describe('toggleCompletion (writes completedDateLocal for cross-device DST parity)', () => {
  it('writes completedDateLocal matching the SoD-relative logical-day key for the supplied date', async () => {
    // No existing completion → the toggle path inserts a new doc.
    getDocsMock.mockResolvedValueOnce({ empty: true, docs: [] });
    addDocMock.mockResolvedValueOnce({ id: 'completion-1' });

    const date = '2026-04-26';
    await toggleCompletion('uid-1', 'habit-1', date);

    expect(addDocMock).toHaveBeenCalledTimes(1);
    const written = addDocMock.mock.calls[0][1] as Record<string, unknown>;

    expect(written.habitCloudId).toBe('habit-1');
    expect(written.completedDateLocal).toBe(date);
    // Format must be YYYY-MM-DD — Android v50 contract.
    expect(written.completedDateLocal).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    // The legacy completedDate timestamp is still written for back-compat.
    expect(typeof written.completedDate).toBe('number');
  });

  it('matches useLogicalToday-shaped output for the user-configured SoD hour', async () => {
    // Wall-clock now falls before the SoD boundary → the logical day is
    // yesterday. The completion-write must persist *that* logical day,
    // not the calendar day.
    const wallClock = new Date(2026, 3, 26, 2, 30, 0); // 02:30 local time
    const sodHour = 4; // Logical day rolls over at 04:00.
    const expected = logicalToday(wallClock, sodHour); // '2026-04-25'
    expect(expected).toBe('2026-04-25');

    getDocsMock.mockResolvedValueOnce({ empty: true, docs: [] });
    addDocMock.mockResolvedValueOnce({ id: 'completion-1' });

    // Caller (the React component) computed the logical date with
    // useLogicalToday(sodHour) and passed it through. Verify the
    // firestore layer faithfully writes that string as
    // completedDateLocal so Android can compare it byte-for-byte.
    await toggleCompletion('uid-1', 'habit-1', expected);

    const written = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(written.completedDateLocal).toBe('2026-04-25');
  });

  it('DST spring-forward: a completion at 23:55 local on the spring-forward day rounds to that calendar day (SoD = 0)', async () => {
    // 2026-03-08 in US Pacific: clocks jump 02:00 → 03:00.
    // A completion logged at 2026-03-08 23:55 local should be tagged
    // with completed_date_local = '2026-03-08', regardless of the DST
    // discontinuity earlier in the day. The logicalToday helper is the
    // contract.
    const wallClock = new Date(2026, 2, 8, 23, 55, 0); // March 8, 23:55 local
    const sodHour = 0;
    const expected = logicalToday(wallClock, sodHour);
    expect(expected).toBe('2026-03-08');

    getDocsMock.mockResolvedValueOnce({ empty: true, docs: [] });
    addDocMock.mockResolvedValueOnce({ id: 'completion-2' });

    await toggleCompletion('uid-1', 'habit-1', expected);

    const written = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(written.completedDateLocal).toBe('2026-03-08');
  });
});
