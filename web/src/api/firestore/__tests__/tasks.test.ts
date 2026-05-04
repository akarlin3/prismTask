import { describe, it, expect, vi, beforeEach } from 'vitest';

const {
  addDocMock,
  updateDocMock,
  getDocMock,
  docMock,
  collectionMock,
} = vi.hoisted(() => ({
  addDocMock: vi.fn(),
  updateDocMock: vi.fn(),
  getDocMock: vi.fn(),
  docMock: vi.fn(),
  collectionMock: vi.fn(),
}));

vi.mock('firebase/firestore', () => ({
  addDoc: addDocMock,
  updateDoc: updateDocMock,
  getDoc: getDocMock,
  doc: docMock,
  collection: collectionMock,
  // Unused-by-these-tests primitives — stubbed so the module loads.
  getDocs: vi.fn(),
  deleteDoc: vi.fn(),
  query: vi.fn(),
  where: vi.fn(),
  orderBy: vi.fn(),
  onSnapshot: vi.fn(),
}));
vi.mock('@/lib/firebase', () => ({ firestore: {} }));

import { createTask, updateTask } from '@/api/firestore/tasks';

beforeEach(() => {
  addDocMock.mockReset();
  updateDocMock.mockReset();
  getDocMock.mockReset();
  docMock.mockReset();
  collectionMock.mockReset();
  docMock.mockReturnValue({});
  collectionMock.mockReturnValue({});
  // addDoc returns a fake ref with an id
  addDocMock.mockResolvedValue({ id: 'new-task-id' });
});

describe('createTask payload shape', () => {
  it('does not write `dueTime: null` when no due_time was provided', async () => {
    await createTask('uid-1', { title: 'No time task' });
    expect(addDocMock).toHaveBeenCalledTimes(1);
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect('dueTime' in payload).toBe(false);
  });

  it('writes dueTime when due_time + due_date are both provided', async () => {
    await createTask('uid-1', {
      title: 'Lunch',
      due_date: '2026-05-01',
      due_time: '12:00',
    });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.dueTime).toEqual(expect.any(Number));
    // 2026-05-01T12:00 local-time millis
    const expected = new Date('2026-05-01T12:00:00').getTime();
    expect(payload.dueTime).toBe(expected);
  });

  it('does not write `isFlagged` when the user did not toggle it', async () => {
    await createTask('uid-1', { title: 'Just a title' });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect('isFlagged' in payload).toBe(false);
  });

  it('writes isFlagged: true only when explicitly set', async () => {
    await createTask('uid-1', {
      title: 'Flagged',
      isFlagged: true,
    } as Parameters<typeof createTask>[1] & { isFlagged?: boolean });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.isFlagged).toBe(true);
  });

  it('does not write `lifeCategory` when not provided', async () => {
    await createTask('uid-1', { title: 'No category' });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect('lifeCategory' in payload).toBe(false);
  });

  it('writes lifeCategory when explicitly set', async () => {
    await createTask('uid-1', {
      title: 'Self-care task',
      lifeCategory: 'SELF_CARE',
    } as Parameters<typeof createTask>[1] & { lifeCategory?: string });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.lifeCategory).toBe('SELF_CARE');
  });

  it('writes taskMode when explicitly set', async () => {
    await createTask('uid-1', {
      title: 'Recharge walk',
      taskMode: 'RELAX',
    } as Parameters<typeof createTask>[1] & { taskMode?: string });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.taskMode).toBe('RELAX');
  });

  it('writes cognitiveLoad when explicitly set', async () => {
    await createTask('uid-1', {
      title: 'Tax filing',
      cognitiveLoad: 'HARD',
    } as Parameters<typeof createTask>[1] & { cognitiveLoad?: string });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.cognitiveLoad).toBe('HARD');
  });

  it('does not write taskMode or cognitiveLoad when not provided', async () => {
    await createTask('uid-1', { title: 'No dimensions' });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect('taskMode' in payload).toBe(false);
    expect('cognitiveLoad' in payload).toBe(false);
  });

  it('does not write Android-only fields (archivedAt, eisenhowerReason, focus-release) on create', async () => {
    await createTask('uid-1', { title: 'Plain task' });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect('archivedAt' in payload).toBe(false);
    expect('eisenhowerReason' in payload).toBe(false);
    expect('userOverrodeQuadrant' in payload).toBe(false);
    expect('goodEnoughMinutesOverride' in payload).toBe(false);
    expect('maxRevisionsOverride' in payload).toBe(false);
    expect('revisionCount' in payload).toBe(false);
    expect('revisionLocked' in payload).toBe(false);
    expect('cumulativeEditMinutes' in payload).toBe(false);
    expect('sourceHabitId' in payload).toBe(false);
    expect('scheduledStartTime' in payload).toBe(false);
    expect('reminderOffset' in payload).toBe(false);
  });
});

describe('updateTask merge-write payload shape', () => {
  beforeEach(() => {
    // updateTask re-reads the doc after writing; return a usable snapshot
    getDocMock.mockResolvedValue({
      id: 'task-1',
      exists: () => true,
      data: () => ({
        title: 'Existing title',
        priority: 2,
        isCompleted: false,
        // Android-only fields the web reader doesn't surface — round-tripped untouched
        isFlagged: true,
        lifeCategory: 'WORK',
        eisenhowerReason: 'Auto: keyword=urgent',
        archivedAt: null,
        userOverrodeQuadrant: true,
        goodEnoughMinutesOverride: 25,
      }),
    });
    updateDocMock.mockResolvedValue(undefined);
  });

  it('writes only the fields the caller passed (title-only edit)', async () => {
    await updateTask('uid-1', 'task-1', { title: 'New title' });
    expect(updateDocMock).toHaveBeenCalledTimes(1);
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.title).toBe('New title');
    // Field set must not include any Android-only keys we'd be clobbering.
    const keys = Object.keys(payload).sort();
    expect(keys).toEqual(['title', 'updatedAt'].sort());
  });

  it('does not include `dueTime`, `isFlagged`, `lifeCategory` in payload when they were not passed', async () => {
    await updateTask('uid-1', 'task-1', { description: 'A description' });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect('dueTime' in payload).toBe(false);
    expect('isFlagged' in payload).toBe(false);
    expect('lifeCategory' in payload).toBe(false);
    expect('eisenhowerReason' in payload).toBe(false);
    expect('archivedAt' in payload).toBe(false);
    expect('userOverrodeQuadrant' in payload).toBe(false);
  });

  it('includes lifeCategory when explicitly set on update', async () => {
    await updateTask('uid-1', 'task-1', { lifeCategory: 'PERSONAL' });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.lifeCategory).toBe('PERSONAL');
  });

  it('includes taskMode when explicitly set on update', async () => {
    await updateTask('uid-1', 'task-1', { taskMode: 'PLAY' });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.taskMode).toBe('PLAY');
  });

  it('includes cognitiveLoad when explicitly set on update', async () => {
    await updateTask('uid-1', 'task-1', { cognitiveLoad: 'EASY' });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.cognitiveLoad).toBe('EASY');
  });

  it('clears taskMode (explicit null) when set to null', async () => {
    await updateTask('uid-1', 'task-1', { taskMode: null });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.taskMode).toBeNull();
  });

  it('writes userOverrodeQuadrant: true alongside an explicit eisenhower_quadrant move', async () => {
    await updateTask('uid-1', 'task-1', {
      eisenhower_quadrant: 'Q1',
      userOverrodeQuadrant: true,
    });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.eisenhowerQuadrant).toBe('Q1');
    expect(payload.userOverrodeQuadrant).toBe(true);
  });

  it('writes dueTime when due_time is provided alongside due_date', async () => {
    await updateTask('uid-1', 'task-1', {
      due_date: '2026-06-01',
      due_time: '09:30',
    });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    const expected = new Date('2026-06-01T09:30:00').getTime();
    expect(payload.dueTime).toBe(expected);
  });

  it('clears dueTime (explicit null) when due_time is set to null', async () => {
    await updateTask('uid-1', 'task-1', { due_time: null });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.dueTime).toBeNull();
  });

  it('round-trips Android-only fields on the returned Task (read passes them through unchanged)', async () => {
    // The point of this test: even though we wrote a partial update, the
    // re-read snapshot still carries the Android-only fields, and updateTask
    // doesn't strip them.
    const result = await updateTask('uid-1', 'task-1', { title: 'Touched' });
    expect(result.title).toBe('Existing title'); // value from re-read snapshot
    // Verify we didn't touch isFlagged / lifeCategory / etc on the write
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    for (const key of [
      'isFlagged',
      'lifeCategory',
      'eisenhowerReason',
      'archivedAt',
      'userOverrodeQuadrant',
      'goodEnoughMinutesOverride',
      'maxRevisionsOverride',
      'revisionCount',
      'revisionLocked',
      'cumulativeEditMinutes',
      'scheduledStartTime',
      'sourceHabitId',
      'reminderOffset',
    ]) {
      expect(key in payload).toBe(false);
    }
  });
});
