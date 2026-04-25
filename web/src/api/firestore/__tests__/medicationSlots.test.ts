import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('@/lib/firebase', () => ({ firestore: {} }));
vi.mock('firebase/firestore', () => ({
  addDoc: vi.fn(),
  collection: vi.fn(),
  deleteDoc: vi.fn(),
  doc: vi.fn(),
  getDoc: vi.fn(),
  getDocs: vi.fn(),
  onSnapshot: vi.fn(),
  orderBy: vi.fn(),
  query: vi.fn(),
  setDoc: vi.fn(),
  updateDoc: vi.fn(),
  where: vi.fn(),
}));

import { normalizeTier } from '@/api/firestore/medicationSlots';

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
