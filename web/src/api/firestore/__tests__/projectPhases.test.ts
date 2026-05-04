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
  getDocs: vi.fn(),
  deleteDoc: vi.fn(),
  query: vi.fn(),
  where: vi.fn(),
  orderBy: vi.fn(),
  onSnapshot: vi.fn(),
}));
vi.mock('@/lib/firebase', () => ({ firestore: {} }));

import { createPhase, updatePhase } from '@/api/firestore/projectPhases';

beforeEach(() => {
  addDocMock.mockReset();
  updateDocMock.mockReset();
  getDocMock.mockReset();
  docMock.mockReset();
  collectionMock.mockReset();
  docMock.mockReturnValue({});
  collectionMock.mockReturnValue({});
  addDocMock.mockResolvedValue({ id: 'new-phase-id' });
});

describe('createPhase payload shape', () => {
  it('writes projectCloudId discriminator + title verbatim', async () => {
    await createPhase('uid-1', 'project-abc', { title: 'Phase F' });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.projectCloudId).toBe('project-abc');
    expect(payload.title).toBe('Phase F');
  });

  it('rounds optional fields through to Android camelCase keys', async () => {
    await createPhase('uid-1', 'p1', {
      title: 'Phase X',
      description: 'desc',
      color_key: 'tertiary',
      start_date: 1_700_000_000_000,
      end_date: 1_710_000_000_000,
      version_anchor: 'v1.9.0',
      version_note: 'release notes',
      order_index: 3,
    });
    const payload = addDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.colorKey).toBe('tertiary');
    expect(payload.startDate).toBe(1_700_000_000_000);
    expect(payload.endDate).toBe(1_710_000_000_000);
    expect(payload.versionAnchor).toBe('v1.9.0');
    expect(payload.versionNote).toBe('release notes');
    expect(payload.orderIndex).toBe(3);
  });

  it('returns a ProjectPhase with the new doc id and snake_case fields', async () => {
    const phase = await createPhase('uid-1', 'project-abc', {
      title: 'Phase F',
      version_anchor: 'v1.9.0',
    });
    expect(phase.id).toBe('new-phase-id');
    expect(phase.project_id).toBe('project-abc');
    expect(phase.version_anchor).toBe('v1.9.0');
  });
});

describe('updatePhase payload shape (merge semantics)', () => {
  beforeEach(() => {
    getDocMock.mockResolvedValue({
      id: 'p1',
      data: () => ({
        title: 'Phase F',
        projectCloudId: 'project-abc',
        createdAt: 1,
        updatedAt: 2,
      }),
    });
  });

  it('only writes fields the caller passed (omit-on-undefined merge)', async () => {
    await updatePhase('uid-1', 'p1', { title: 'Renamed' });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.title).toBe('Renamed');
    expect('description' in payload).toBe(false);
    expect('versionAnchor' in payload).toBe(false);
    expect(payload.updatedAt).toEqual(expect.any(Number));
  });

  it('writes null when the caller explicitly clears a field', async () => {
    await updatePhase('uid-1', 'p1', { description: null });
    const payload = updateDocMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.description).toBeNull();
  });
});
