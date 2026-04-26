import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const { batchSetMock, batchCommitMock, writeBatchMock, docMock } = vi.hoisted(() => {
  const batchSetMock = vi.fn();
  const batchCommitMock = vi.fn();
  const writeBatchMock = vi.fn(() => ({
    set: batchSetMock,
    commit: batchCommitMock,
  }));
  const docMock = vi.fn(
    (_db: unknown, ..._segments: string[]) => ({ id: _segments[_segments.length - 1] }),
  );
  return { batchSetMock, batchCommitMock, writeBatchMock, docMock };
});

vi.mock('@/lib/firebase', () => ({ firestore: {} }));
vi.mock('firebase/firestore', () => ({
  addDoc: vi.fn(),
  collection: vi.fn(),
  deleteDoc: vi.fn(),
  doc: docMock,
  getDoc: vi.fn(),
  getDocs: vi.fn(),
  onSnapshot: vi.fn(),
  orderBy: vi.fn(),
  query: vi.fn(),
  setDoc: vi.fn(),
  updateDoc: vi.fn(),
  where: vi.fn(),
  writeBatch: writeBatchMock,
}));

import { normalizeTier, setTierStatesAtomic } from '@/api/firestore/medicationSlots';

describe('normalizeTier', () => {
  let warnSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    warnSpy.mockRestore();
  });

  describe('canonical lowercase 4-tier values', () => {
    it('passes through "skipped"', () => {
      expect(normalizeTier('skipped')).toBe('skipped');
      expect(warnSpy).not.toHaveBeenCalled();
    });

    it('passes through "essential"', () => {
      expect(normalizeTier('essential')).toBe('essential');
      expect(warnSpy).not.toHaveBeenCalled();
    });

    it('passes through "prescription"', () => {
      expect(normalizeTier('prescription')).toBe('prescription');
      expect(warnSpy).not.toHaveBeenCalled();
    });

    it('passes through "complete"', () => {
      expect(normalizeTier('complete')).toBe('complete');
      expect(warnSpy).not.toHaveBeenCalled();
    });
  });

  describe('legacy uppercase 3-tier values', () => {
    it('maps "SKIPPED" → "skipped" and warns', () => {
      expect(normalizeTier('SKIPPED')).toBe('skipped');
      expect(warnSpy).toHaveBeenCalledTimes(1);
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining('Normalizing legacy tier "SKIPPED" → "skipped"'),
      );
    });

    it('maps "PARTIAL" → "essential" (conservative) and warns', () => {
      expect(normalizeTier('PARTIAL')).toBe('essential');
      expect(warnSpy).toHaveBeenCalledTimes(1);
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining('Normalizing legacy tier "PARTIAL" → "essential"'),
      );
    });

    it('maps "COMPLETE" → "complete" and warns', () => {
      expect(normalizeTier('COMPLETE')).toBe('complete');
      expect(warnSpy).toHaveBeenCalledTimes(1);
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining('Normalizing legacy tier "COMPLETE" → "complete"'),
      );
    });
  });

  describe('garbage values', () => {
    it('returns "skipped" for unknown strings', () => {
      expect(normalizeTier('foo')).toBe('skipped');
      expect(normalizeTier('')).toBe('skipped');
      expect(normalizeTier('Skipped')).toBe('skipped'); // wrong casing, not legacy
    });

    it('returns "skipped" for non-string input', () => {
      expect(normalizeTier(null)).toBe('skipped');
      expect(normalizeTier(undefined)).toBe('skipped');
      expect(normalizeTier(42)).toBe('skipped');
      expect(normalizeTier({})).toBe('skipped');
    });
  });
});

describe('setTierStatesAtomic', () => {
  beforeEach(() => {
    batchSetMock.mockReset();
    batchCommitMock.mockReset();
    writeBatchMock.mockClear();
    docMock.mockClear();
  });

  it('returns an empty array and never opens a batch when given no updates', async () => {
    const ids = await setTierStatesAtomic('uid-1', []);
    expect(ids).toEqual([]);
    expect(writeBatchMock).not.toHaveBeenCalled();
    expect(batchCommitMock).not.toHaveBeenCalled();
  });

  it('uses a single writeBatch for N tier-state writes', async () => {
    batchCommitMock.mockResolvedValueOnce(undefined);
    const ids = await setTierStatesAtomic('uid-1', [
      { dateIso: '2026-04-25', slotKey: 'morning', tier: 'complete' },
      { dateIso: '2026-04-25', slotKey: 'evening', tier: 'complete' },
      { dateIso: '2026-04-25', slotKey: 'bedtime', tier: 'complete' },
    ]);
    expect(writeBatchMock).toHaveBeenCalledTimes(1);
    expect(batchSetMock).toHaveBeenCalledTimes(3);
    expect(batchCommitMock).toHaveBeenCalledTimes(1);
    // doc ids match the deterministic `${dateIso}__${slotKey}` shape.
    expect(ids).toEqual([
      '2026-04-25__morning',
      '2026-04-25__evening',
      '2026-04-25__bedtime',
    ]);
  });

  it('passes merge: true on every set so existing intended_time survives', async () => {
    batchCommitMock.mockResolvedValueOnce(undefined);
    await setTierStatesAtomic('uid-1', [
      { dateIso: '2026-04-25', slotKey: 'morning', tier: 'essential' },
    ]);
    const setCall = batchSetMock.mock.calls[0];
    // Args: [docRef, payload, options]
    const payload = setCall[1] as Record<string, unknown>;
    const options = setCall[2] as { merge: boolean };
    expect(options).toEqual({ merge: true });
    // Payload carries the slot/date/tier/source/loggedAt/updatedAt fields,
    // matches single-target setTierState semantics.
    expect(payload.slotKey).toBe('morning');
    expect(payload.dateIso).toBe('2026-04-25');
    expect(payload.tier).toBe('essential');
    expect(payload.source).toBe('user_set');
    expect(typeof payload.loggedAt).toBe('number');
    expect(typeof payload.updatedAt).toBe('number');
  });

  it('honors caller-supplied source override (e.g. auto vs user_set)', async () => {
    batchCommitMock.mockResolvedValueOnce(undefined);
    await setTierStatesAtomic('uid-1', [
      { dateIso: '2026-04-25', slotKey: 'morning', tier: 'complete', source: 'auto' },
    ]);
    const payload = batchSetMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.source).toBe('auto');
  });

  it('propagates batch.commit() rejections as the caller-visible error', async () => {
    batchCommitMock.mockRejectedValueOnce(new Error('Firestore down'));
    await expect(
      setTierStatesAtomic('uid-1', [
        { dateIso: '2026-04-25', slotKey: 'morning', tier: 'complete' },
      ]),
    ).rejects.toThrow('Firestore down');
  });
});
