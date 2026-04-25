import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { BatchUndoLogEntry, ProposedMutation } from '@/types/batch';

/**
 * Web parity for medication batch ops. Mirrors the Android
 * `BatchOperationsRepositoryMedicationTest.kt` shape but operates on the
 * (date, slot_key) tier-state surface — web has no per-medication dose
 * collection. Multiple mutations targeting the same slot collapse onto
 * one tier-state row idempotently, and DELETE on MEDICATION is rejected
 * because there is no per-dose record to remove.
 */

// Hoisted mocks for Firestore medication-slot APIs.
const { getTierStateMock, setTierStateMock, clearTierStateMock } = vi.hoisted(
  () => ({
    getTierStateMock: vi.fn(),
    setTierStateMock: vi.fn(),
    clearTierStateMock: vi.fn(),
  }),
);
vi.mock('@/api/firestore/medicationSlots', async () => {
  // Don't mock the type re-export.
  const actual = await vi.importActual<typeof import('@/api/firestore/medicationSlots')>(
    '@/api/firestore/medicationSlots',
  );
  return {
    ...actual,
    getTierState: getTierStateMock,
    setTierState: setTierStateMock,
    clearTierState: clearTierStateMock,
  };
});

// Stub out other Firestore touchpoints batchApplier imports — none of the
// medication paths exercise tasks, habits, projects, or tags.
vi.mock('firebase/firestore', () => ({
  doc: vi.fn(),
  updateDoc: vi.fn(),
  getDoc: vi.fn(),
  addDoc: vi.fn(),
  deleteDoc: vi.fn(),
  collection: vi.fn(),
  query: vi.fn(),
  where: vi.fn(),
  getDocs: vi.fn(),
}));
vi.mock('@/lib/firebase', () => ({ firestore: {} }));
vi.mock('@/api/firestore/converters', () => ({
  dateStrToTimestamp: (iso: string) => Date.parse(iso),
}));
vi.mock('@/api/firestore/tasks', () => ({ setTagsForTask: vi.fn() }));
vi.mock('@/api/firestore/tags', () => ({
  getTags: vi.fn().mockResolvedValue([]),
  createTag: vi.fn(),
}));

import { applyMutation, undoEntry } from '../batchApplier';

const UID = 'user-1';

function medicationMutation(
  mutationType: ProposedMutation['mutation_type'],
  values: Record<string, unknown>,
): ProposedMutation {
  return {
    entity_type: 'MEDICATION',
    entity_id: 'med-1',
    mutation_type: mutationType,
    proposed_new_values: values,
    human_readable_description: `${mutationType} on med-1`,
  };
}

beforeEach(() => {
  getTierStateMock.mockReset();
  setTierStateMock.mockReset();
  clearTierStateMock.mockReset();
});

describe('applyMutation — MEDICATION', () => {
  it('COMPLETE writes tier=complete with source=user_set', async () => {
    getTierStateMock.mockResolvedValueOnce(null);
    setTierStateMock.mockResolvedValueOnce({});

    const result = await applyMutation(
      UID,
      medicationMutation('COMPLETE', { date: '2026-04-25', slot_key: 'morning' }),
    );

    expect(result.applied).toBe(true);
    expect(setTierStateMock).toHaveBeenCalledWith(
      UID,
      '2026-04-25',
      'morning',
      'complete',
      'user_set',
    );
    expect(result.entry?.pre_state.prior_existed).toBe(false);
  });

  it('SKIP writes tier=skipped and snapshots prior tier', async () => {
    getTierStateMock.mockResolvedValueOnce({
      id: '2026-04-25__morning',
      slot_key: 'morning',
      date_iso: '2026-04-25',
      tier: 'essential',
      source: 'auto',
      intended_time: null,
      logged_at: 1,
      updated_at: 1,
    });
    setTierStateMock.mockResolvedValueOnce({});

    const result = await applyMutation(
      UID,
      medicationMutation('SKIP', { date: '2026-04-25', slot_key: 'morning' }),
    );

    expect(result.applied).toBe(true);
    expect(setTierStateMock).toHaveBeenCalledWith(
      UID,
      '2026-04-25',
      'morning',
      'skipped',
      'user_set',
    );
    expect(result.entry?.pre_state).toMatchObject({
      prior_existed: true,
      prior_tier: 'essential',
      prior_source: 'auto',
    });
  });

  it('STATE_CHANGE with a valid tier writes that tier with source=user_set', async () => {
    getTierStateMock.mockResolvedValueOnce(null);
    setTierStateMock.mockResolvedValueOnce({});

    const result = await applyMutation(
      UID,
      medicationMutation('STATE_CHANGE', {
        date: '2026-04-25',
        slot_key: 'evening',
        tier: 'prescription',
      }),
    );

    expect(result.applied).toBe(true);
    expect(setTierStateMock).toHaveBeenCalledWith(
      UID,
      '2026-04-25',
      'evening',
      'prescription',
      'user_set',
    );
  });

  it('STATE_CHANGE with an unknown tier is rejected without writing', async () => {
    const result = await applyMutation(
      UID,
      medicationMutation('STATE_CHANGE', {
        date: '2026-04-25',
        slot_key: 'evening',
        tier: 'gold-medal',
      }),
    );

    expect(result.applied).toBe(false);
    expect(setTierStateMock).not.toHaveBeenCalled();
  });

  it('DELETE on MEDICATION is not supported on web', async () => {
    const result = await applyMutation(
      UID,
      medicationMutation('DELETE', { date: '2026-04-25', slot_key: 'morning' }),
    );

    expect(result.applied).toBe(false);
    expect(result.reason).toMatch(/not supported on web/i);
    expect(setTierStateMock).not.toHaveBeenCalled();
  });

  it('missing slot_key is rejected without writing', async () => {
    const result = await applyMutation(
      UID,
      medicationMutation('COMPLETE', { date: '2026-04-25' }),
    );

    expect(result.applied).toBe(false);
    expect(setTierStateMock).not.toHaveBeenCalled();
  });
});

describe('undoEntry — MEDICATION', () => {
  it('restores prior tier when one existed before the batch', async () => {
    setTierStateMock.mockResolvedValueOnce({});
    const entry: BatchUndoLogEntry = {
      entity_type: 'MEDICATION',
      entity_id: 'med-1',
      mutation_type: 'STATE_CHANGE',
      pre_state: {
        date_iso: '2026-04-25',
        slot_key: 'morning',
        prior_existed: true,
        prior_tier: 'essential',
        prior_source: 'auto',
      },
      applied: true,
    };

    const ok = await undoEntry(UID, entry);

    expect(ok).toBe(true);
    expect(setTierStateMock).toHaveBeenCalledWith(
      UID,
      '2026-04-25',
      'morning',
      'essential',
      'auto',
    );
    expect(clearTierStateMock).not.toHaveBeenCalled();
  });

  it('clears the tier-state row if the batch created it', async () => {
    clearTierStateMock.mockResolvedValueOnce(undefined);
    const entry: BatchUndoLogEntry = {
      entity_type: 'MEDICATION',
      entity_id: 'med-1',
      mutation_type: 'COMPLETE',
      pre_state: {
        date_iso: '2026-04-25',
        slot_key: 'morning',
        prior_existed: false,
        prior_tier: null,
        prior_source: null,
      },
      applied: true,
    };

    const ok = await undoEntry(UID, entry);

    expect(ok).toBe(true);
    expect(clearTierStateMock).toHaveBeenCalledWith(UID, '2026-04-25', 'morning');
    expect(setTierStateMock).not.toHaveBeenCalled();
  });
});
