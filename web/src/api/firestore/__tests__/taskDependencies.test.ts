import { describe, it, expect, vi, beforeEach } from 'vitest';

const {
  addDocMock,
  getDocsMock,
  docMock,
  collectionMock,
} = vi.hoisted(() => ({
  addDocMock: vi.fn(),
  getDocsMock: vi.fn(),
  docMock: vi.fn(),
  collectionMock: vi.fn(),
}));

vi.mock('firebase/firestore', () => ({
  addDoc: addDocMock,
  updateDoc: vi.fn(),
  getDoc: vi.fn(),
  doc: docMock,
  collection: collectionMock,
  getDocs: getDocsMock,
  deleteDoc: vi.fn(),
  query: vi.fn(),
  where: vi.fn(),
  orderBy: vi.fn(),
  onSnapshot: vi.fn(),
}));
vi.mock('@/lib/firebase', () => ({ firestore: {} }));

import {
  addDependency,
  DependencyCycleError,
} from '@/api/firestore/taskDependencies';

beforeEach(() => {
  addDocMock.mockReset();
  getDocsMock.mockReset();
  docMock.mockReset();
  collectionMock.mockReset();
  docMock.mockReturnValue({});
  collectionMock.mockReturnValue({});
  addDocMock.mockResolvedValue({ id: 'new-dep-id' });
});

function snap(deps: Array<{ blockerTaskCloudId: string; blockedTaskCloudId: string }>) {
  return {
    docs: deps.map((d, i) => ({
      id: `existing-${i}`,
      data: () => ({ ...d, createdAt: i }),
    })),
  };
}

describe('addDependency', () => {
  it('writes both task cloud ids in camelCase + createdAt', async () => {
    getDocsMock.mockResolvedValue(snap([]));
    await addDependency('uid-1', {
      blocker_task_id: 'task-a',
      blocked_task_id: 'task-b',
    });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.blockerTaskCloudId).toBe('task-a');
    expect(payload.blockedTaskCloudId).toBe('task-b');
    expect(payload.createdAt).toEqual(expect.any(Number));
  });

  it('rejects a self-edge with DependencyCycleError before write', async () => {
    getDocsMock.mockResolvedValue(snap([]));
    await expect(
      addDependency('uid-1', {
        blocker_task_id: 'task-a',
        blocked_task_id: 'task-a',
      }),
    ).rejects.toBeInstanceOf(DependencyCycleError);
    expect(addDocMock).not.toHaveBeenCalled();
  });

  it('rejects a cycle-closing edge given existing edges', async () => {
    // Existing: a → b. Proposing b → a closes the cycle.
    getDocsMock.mockResolvedValue(
      snap([{ blockerTaskCloudId: 'a', blockedTaskCloudId: 'b' }]),
    );
    await expect(
      addDependency('uid-1', {
        blocker_task_id: 'b',
        blocked_task_id: 'a',
      }),
    ).rejects.toBeInstanceOf(DependencyCycleError);
    expect(addDocMock).not.toHaveBeenCalled();
  });

  it('accepts a non-cycle edge when other edges exist', async () => {
    getDocsMock.mockResolvedValue(
      snap([{ blockerTaskCloudId: 'a', blockedTaskCloudId: 'b' }]),
    );
    const created = await addDependency('uid-1', {
      blocker_task_id: 'c',
      blocked_task_id: 'd',
    });
    expect(created.id).toBe('new-dep-id');
    expect(created.blocker_task_id).toBe('c');
    expect(created.blocked_task_id).toBe('d');
  });
});
