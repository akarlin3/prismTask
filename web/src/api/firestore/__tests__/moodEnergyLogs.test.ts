import { describe, it, expect, vi, beforeEach } from 'vitest';

const { setDocMock, docMock, collectionMock } = vi.hoisted(() => {
  const setDocMock = vi.fn(async () => undefined);
  // Mocked `doc(...)` returns an object identifying the doc by the
  // segments passed to it — last segment is the doc id we care about.
  const docMock = vi.fn((..._segments: unknown[]) => {
    const segs = _segments.filter((s): s is string => typeof s === 'string');
    return { id: segs[segs.length - 1], path: segs.join('/') };
  });
  const collectionMock = vi.fn(
    (_db: unknown, ...segments: string[]) => ({ path: segments.join('/') }),
  );
  return { setDocMock, docMock, collectionMock };
});

vi.mock('@/lib/firebase', () => ({ firestore: {} }));
vi.mock('firebase/firestore', () => ({
  addDoc: vi.fn(),
  collection: collectionMock,
  deleteDoc: vi.fn(),
  doc: docMock,
  getDocs: vi.fn(),
  orderBy: vi.fn(),
  query: vi.fn(),
  setDoc: setDocMock,
  updateDoc: vi.fn(),
  where: vi.fn(),
}));

import { createLog } from '@/api/firestore/moodEnergyLogs';

const UID = 'user-123';

describe('createLog uniqueness guard', () => {
  beforeEach(() => {
    setDocMock.mockClear();
    docMock.mockClear();
  });

  it('uses a deterministic doc id of `${dateIso}__${timeOfDay}`', async () => {
    const result = await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 4,
      energy: 3,
      time_of_day: 'morning',
    });

    expect(result.id).toBe('2026-04-26__morning');
    expect(setDocMock).toHaveBeenCalledTimes(1);
    // First positional arg to setDoc is the DocumentReference (our mock).
    const ref = (setDocMock.mock.calls[0] as unknown[])[0] as { id: string; path: string };
    expect(ref.id).toBe('2026-04-26__morning');
    expect(ref.path).toContain('mood_energy_logs/2026-04-26__morning');
  });

  it('two consecutive calls for the same (dateIso, timeOfDay) write to one doc id', async () => {
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 2,
      energy: 2,
      time_of_day: 'evening',
    });
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 5,
      energy: 4,
      time_of_day: 'evening',
      notes: 'much better after a walk',
    });

    expect(setDocMock).toHaveBeenCalledTimes(2);
    const firstRef = (setDocMock.mock.calls[0] as unknown[])[0] as { id: string };
    const secondRef = (setDocMock.mock.calls[1] as unknown[])[0] as { id: string };
    expect(firstRef.id).toBe(secondRef.id);
    expect(firstRef.id).toBe('2026-04-26__evening');
  });

  it('uses { merge: true } so a second write updates rather than replaces', async () => {
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 5,
      energy: 4,
      time_of_day: 'afternoon',
      notes: 'updated notes',
    });

    const opts = (setDocMock.mock.calls[0] as unknown[])[2] as { merge: boolean } | undefined;
    expect(opts).toEqual({ merge: true });
  });

  it('second call updates payload fields (mood / energy / notes)', async () => {
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 1,
      energy: 1,
      time_of_day: 'night',
      notes: 'first',
    });
    const updated = await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 4,
      energy: 5,
      time_of_day: 'night',
      notes: 'second',
    });

    expect(updated.mood).toBe(4);
    expect(updated.energy).toBe(5);
    expect(updated.notes).toBe('second');
    expect(updated.time_of_day).toBe('night');
    const secondPayload = (setDocMock.mock.calls[1] as unknown[])[1] as Record<string, unknown>;
    expect(secondPayload.mood).toBe(4);
    expect(secondPayload.energy).toBe(5);
    expect(secondPayload.notes).toBe('second');
  });

  it('different timeOfDay slots get different doc ids', async () => {
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 3,
      energy: 3,
      time_of_day: 'morning',
    });
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 3,
      energy: 3,
      time_of_day: 'evening',
    });

    const morningRef = (setDocMock.mock.calls[0] as unknown[])[0] as { id: string };
    const eveningRef = (setDocMock.mock.calls[1] as unknown[])[0] as { id: string };
    expect(morningRef.id).toBe('2026-04-26__morning');
    expect(eveningRef.id).toBe('2026-04-26__evening');
    expect(morningRef.id).not.toBe(eveningRef.id);
  });

  it('different dates with same timeOfDay get different doc ids', async () => {
    await createLog(UID, {
      date_iso: '2026-04-25',
      mood: 3,
      energy: 3,
      time_of_day: 'morning',
    });
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 3,
      energy: 3,
      time_of_day: 'morning',
    });

    const day1Ref = (setDocMock.mock.calls[0] as unknown[])[0] as { id: string };
    const day2Ref = (setDocMock.mock.calls[1] as unknown[])[0] as { id: string };
    expect(day1Ref.id).toBe('2026-04-25__morning');
    expect(day2Ref.id).toBe('2026-04-26__morning');
  });

  it('defaults timeOfDay to "morning" when omitted (matches Android default)', async () => {
    await createLog(UID, {
      date_iso: '2026-04-26',
      mood: 3,
      energy: 3,
    });

    const ref = (setDocMock.mock.calls[0] as unknown[])[0] as { id: string };
    expect(ref.id).toBe('2026-04-26__morning');
  });
});
