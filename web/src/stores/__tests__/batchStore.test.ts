import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

/**
 * Store unit tests that avoid Firestore by mocking every module that
 * touches the Firebase SDK. The store itself is a thin orchestration
 * layer; its meat is the commit/undo flow which is exercised indirectly
 * through the mocked applier.
 */

vi.mock('@/api/firestore/tasks', () => ({ getAllTasks: vi.fn(async () => []) }));
vi.mock('@/api/firestore/habits', () => ({ getHabits: vi.fn(async () => []) }));
vi.mock('@/api/firestore/projects', () => ({ getProjects: vi.fn(async () => []) }));
vi.mock('@/api/nlpBatch', () => ({
  nlpBatchApi: {
    parse: vi.fn(async () => ({
      mutations: [
        {
          entity_type: 'TASK',
          entity_id: 't1',
          mutation_type: 'COMPLETE',
          proposed_new_values: {},
          human_readable_description: 'Complete task 1',
        },
      ],
      confidence: 0.9,
      ambiguous_entities: [],
      proposed: true,
    })),
  },
}));
vi.mock('@/stores/firebaseUid', () => ({
  getFirebaseUid: vi.fn(() => 'test-uid'),
  setFirebaseUid: vi.fn(),
}));
vi.mock('@/features/batch/batchApplier', () => ({
  applyMutation: vi.fn(async (_uid, m) => ({
    applied: true,
    entry: {
      entity_type: m.entity_type,
      entity_id: m.entity_id,
      mutation_type: m.mutation_type,
      pre_state: { example: true },
      applied: true,
    },
  })),
  undoEntry: vi.fn(async () => true),
}));

// Silence toast side-effects in the commit path.
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import { useBatchStore } from '@/stores/batchStore';
import type { BatchHistoryRecord } from '@/types/batch';

function resetStore() {
  useBatchStore.setState({
    pendingCommand: null,
    pendingResponse: null,
    isParsing: false,
    parseError: null,
    history: [],
  });
}

describe('useBatchStore', () => {
  beforeEach(() => {
    resetStore();
    localStorage.clear();
  });

  afterEach(() => {
    resetStore();
    localStorage.clear();
  });

  it('setPendingCommand resets response and error', () => {
    useBatchStore.setState({
      pendingResponse: {
        mutations: [],
        confidence: 0,
        ambiguous_entities: [],
        proposed: true,
      },
      parseError: 'stale',
    });
    useBatchStore.getState().setPendingCommand('reschedule all tasks tomorrow');
    const s = useBatchStore.getState();
    expect(s.pendingCommand).toBe('reschedule all tasks tomorrow');
    expect(s.pendingResponse).toBeNull();
    expect(s.parseError).toBeNull();
  });

  it('parsePendingCommand populates response from API', async () => {
    useBatchStore.getState().setPendingCommand('reschedule all tasks');
    await useBatchStore.getState().parsePendingCommand();
    const s = useBatchStore.getState();
    expect(s.isParsing).toBe(false);
    expect(s.parseError).toBeNull();
    expect(s.pendingResponse?.mutations).toHaveLength(1);
  });

  it('parsePendingCommand strips mutations whose entity_id appears in ambiguous_entities', async () => {
    // Web parity for the Android auto-strip safeguard. Even if Haiku
    // emits a mutation despite flagging the phrase as ambiguous, the
    // store must drop the mutation before it reaches the preview UI.
    const { nlpBatchApi } = await import('@/api/nlpBatch');
    (nlpBatchApi.parse as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      mutations: [
        {
          entity_type: 'MEDICATION',
          entity_id: 'med-1',
          mutation_type: 'COMPLETE',
          proposed_new_values: { slot_key: 'morning', date: '2026-04-25' },
          human_readable_description: 'Mark medication taken',
        },
      ],
      confidence: 0.4,
      ambiguous_entities: [
        {
          phrase: 'Wellbutrin',
          candidate_entity_type: 'MEDICATION',
          candidate_entity_ids: ['med-1', 'med-2'],
          note: 'Two medications match',
        },
      ],
      proposed: true,
    });

    useBatchStore.getState().setPendingCommand('took my Wellbutrin');
    await useBatchStore.getState().parsePendingCommand();

    const s = useBatchStore.getState();
    expect(s.pendingResponse?.mutations).toHaveLength(0);
    expect(s.pendingResponse?.stripped_ambiguous_count).toBe(1);
    // Hint stays so the banner renders.
    expect(s.pendingResponse?.ambiguous_entities).toHaveLength(1);
  });

  it('parsePendingCommand keeps mutations whose entity_id is NOT in ambiguous_entities', async () => {
    // Don't be overzealous: a mutation whose entity_id is unrelated to
    // the ambiguous candidates passes through unchanged.
    const { nlpBatchApi } = await import('@/api/nlpBatch');
    (nlpBatchApi.parse as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      mutations: [
        {
          entity_type: 'TASK',
          entity_id: 'task-99',
          mutation_type: 'COMPLETE',
          proposed_new_values: {},
          human_readable_description: 'Complete unrelated task',
        },
      ],
      confidence: 0.7,
      ambiguous_entities: [
        {
          phrase: 'Wellbutrin',
          candidate_entity_type: 'MEDICATION',
          candidate_entity_ids: ['med-1', 'med-2'],
        },
      ],
      proposed: true,
    });

    useBatchStore.getState().setPendingCommand('mixed command');
    await useBatchStore.getState().parsePendingCommand();

    const s = useBatchStore.getState();
    expect(s.pendingResponse?.mutations).toHaveLength(1);
    expect(s.pendingResponse?.stripped_ambiguous_count).toBe(0);
  });

  it('clearPending wipes the preview state', () => {
    useBatchStore.setState({
      pendingCommand: 'foo',
      pendingResponse: {
        mutations: [],
        confidence: 0,
        ambiguous_entities: [],
        proposed: true,
      },
      parseError: 'err',
    });
    useBatchStore.getState().clearPending();
    const s = useBatchStore.getState();
    expect(s.pendingCommand).toBeNull();
    expect(s.pendingResponse).toBeNull();
    expect(s.parseError).toBeNull();
  });

  it('hydrate loads saved history from localStorage and drops expired records', () => {
    const now = Date.now();
    const records: BatchHistoryRecord[] = [
      {
        batch_id: 'fresh',
        command_text: 'fresh cmd',
        created_at: now,
        expires_at: now + 60_000,
        undone_at: null,
        entries: [],
        applied_count: 0,
        skipped_count: 0,
      },
      {
        batch_id: 'stale',
        command_text: 'stale cmd',
        created_at: now - 86_400_000 - 1000,
        expires_at: now - 1000,
        undone_at: null,
        entries: [],
        applied_count: 0,
        skipped_count: 0,
      },
    ];
    localStorage.setItem(
      'prismtask_batch_history_test-uid',
      JSON.stringify(records),
    );

    useBatchStore.getState().hydrate('test-uid');
    const loaded = useBatchStore.getState().history;
    expect(loaded.map((r) => r.batch_id)).toEqual(['fresh']);
    // Expired record should also be purged from the persisted store.
    const persisted: BatchHistoryRecord[] = JSON.parse(
      localStorage.getItem('prismtask_batch_history_test-uid') || '[]',
    );
    expect(persisted.map((r) => r.batch_id)).toEqual(['fresh']);
  });

  it('commit writes a history record with applied and skipped counts', async () => {
    const mutations = [
      {
        entity_type: 'TASK' as const,
        entity_id: 't1',
        mutation_type: 'COMPLETE' as const,
        proposed_new_values: {},
        human_readable_description: 'Complete t1',
      },
      {
        entity_type: 'TASK' as const,
        entity_id: 't2',
        mutation_type: 'COMPLETE' as const,
        proposed_new_values: {},
        human_readable_description: 'Complete t2',
      },
    ];
    const record = await useBatchStore.getState().commit('cmd', mutations);
    expect(record.applied_count).toBe(2);
    expect(record.skipped_count).toBe(0);
    expect(record.entries).toHaveLength(2);
    expect(useBatchStore.getState().history[0]?.batch_id).toBe(record.batch_id);
  });

  it('undo marks a batch undone and calls reverse for each applied entry', async () => {
    const record = await useBatchStore.getState().commit('cmd', [
      {
        entity_type: 'TASK',
        entity_id: 't1',
        mutation_type: 'COMPLETE',
        proposed_new_values: {},
        human_readable_description: 'Complete t1',
      },
    ]);
    const restored = await useBatchStore.getState().undo(record.batch_id);
    expect(restored).toBe(1);
    expect(useBatchStore.getState().history[0]?.undone_at).not.toBeNull();
    // Undoing twice is a no-op.
    const again = await useBatchStore.getState().undo(record.batch_id);
    expect(again).toBe(0);
  });
});
