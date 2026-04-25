import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { setDocMock, getDocMock } = vi.hoisted(() => ({
  setDocMock: vi.fn(),
  getDocMock: vi.fn(),
}));

vi.mock('firebase/firestore', () => ({
  doc: vi.fn(() => ({ id: 'mock-doc' })),
  collection: vi.fn(() => ({})),
  setDoc: setDocMock,
  getDoc: getDocMock,
  getDocs: vi.fn(),
  addDoc: vi.fn(),
  updateDoc: vi.fn(),
  deleteDoc: vi.fn(),
  onSnapshot: vi.fn(),
  query: vi.fn(),
  orderBy: vi.fn(),
  where: vi.fn(),
}));

vi.mock('@/lib/firebase', () => ({
  firestore: {},
}));

import {
  setTierState,
  setTierStateIntendedTime,
} from '@/api/firestore/medicationSlots';

describe('setTierState', () => {
  beforeEach(() => {
    setDocMock.mockReset();
    getDocMock.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('writes loggedAt + tier without touching intendedTime', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-04-23T08:00:00.000Z'));
    setDocMock.mockResolvedValueOnce(undefined);
    // The post-write read returns the merged doc — it must include any
    // pre-existing intended_time from the prior write.
    getDocMock.mockResolvedValueOnce({
      data: () => ({
        slotKey: 'morning',
        dateIso: '2026-04-23',
        tier: 'complete',
        source: 'user_set',
        loggedAt: Date.now(),
        intendedTime: 1_700_000_000_000,
        updatedAt: Date.now(),
      }),
    });

    const result = await setTierState(
      'uid-1',
      '2026-04-23',
      'morning',
      'complete',
      'user_set',
    );

    // setDoc payload must NOT include intendedTime — preserves prior value.
    const setDocCall = setDocMock.mock.calls[0];
    expect(setDocCall[1]).not.toHaveProperty('intendedTime');
    expect(setDocCall[1].loggedAt).toBe(Date.now());
    expect(setDocCall[1].tier).toBe('complete');
    expect(setDocCall[2]).toEqual({ merge: true });

    // Returned shape preserves the pre-existing intended_time.
    expect(result.intended_time).toBe(1_700_000_000_000);
    expect(result.logged_at).toBe(Date.now());
  });
});

describe('setTierStateIntendedTime', () => {
  beforeEach(() => {
    setDocMock.mockReset();
    getDocMock.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('writes intendedTime and caps future values to now', async () => {
    vi.useFakeTimers();
    const now = new Date('2026-04-23T10:00:00.000Z').getTime();
    vi.setSystemTime(now);

    const future = now + 60 * 60 * 1000; // 1 hour in the future

    setDocMock.mockResolvedValueOnce(undefined);
    getDocMock.mockResolvedValueOnce({
      data: () => ({
        slotKey: 'morning',
        dateIso: '2026-04-23',
        tier: 'complete',
        source: 'user_set',
        loggedAt: now,
        intendedTime: now,
        updatedAt: now,
      }),
    });

    await setTierStateIntendedTime('uid-1', '2026-04-23', 'morning', future);

    const payload = setDocMock.mock.calls[0][1];
    expect(payload.intendedTime).toBe(now); // capped
    expect(payload).not.toHaveProperty('tier'); // doesn't touch tier
    expect(payload).not.toHaveProperty('loggedAt'); // doesn't touch logged_at
  });

  it('writes a backdated intendedTime unchanged', async () => {
    vi.useFakeTimers();
    const now = new Date('2026-04-23T10:00:00.000Z').getTime();
    vi.setSystemTime(now);

    const earlier = now - 2 * 60 * 60 * 1000; // 2 hours earlier

    setDocMock.mockResolvedValueOnce(undefined);
    getDocMock.mockResolvedValueOnce({
      data: () => ({
        slotKey: 'morning',
        dateIso: '2026-04-23',
        tier: 'complete',
        source: 'user_set',
        loggedAt: now,
        intendedTime: earlier,
        updatedAt: now,
      }),
    });

    const result = await setTierStateIntendedTime(
      'uid-1',
      '2026-04-23',
      'morning',
      earlier,
    );

    const payload = setDocMock.mock.calls[0][1];
    expect(payload.intendedTime).toBe(earlier); // unchanged
    expect(result.intended_time).toBe(earlier);
  });
});
