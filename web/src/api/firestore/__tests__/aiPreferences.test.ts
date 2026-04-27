import { describe, it, expect, vi, beforeEach } from 'vitest';

const {
  getDocMock,
  setDocMock,
  docMock,
  onSnapshotMock,
} = vi.hoisted(() => ({
  getDocMock: vi.fn(),
  setDocMock: vi.fn(),
  docMock: vi.fn(),
  onSnapshotMock: vi.fn(),
}));

vi.mock('firebase/firestore', () => ({
  doc: docMock,
  getDoc: getDocMock,
  setDoc: setDocMock,
  onSnapshot: onSnapshotMock,
}));
vi.mock('@/lib/firebase', () => ({ firestore: {} }));

import {
  DEFAULT_AI_FEATURES_ENABLED,
  getAiFeaturesEnabled,
  setAiFeaturesEnabled,
} from '@/api/firestore/aiPreferences';

beforeEach(() => {
  getDocMock.mockReset();
  setDocMock.mockReset();
  docMock.mockReset();
  onSnapshotMock.mockReset();
  docMock.mockReturnValue({});
});

describe('getAiFeaturesEnabled', () => {
  it('returns the default (true) when the doc does not exist', async () => {
    getDocMock.mockResolvedValueOnce({ exists: () => false, data: () => undefined });
    const enabled = await getAiFeaturesEnabled('uid-1');
    expect(enabled).toBe(DEFAULT_AI_FEATURES_ENABLED);
    expect(enabled).toBe(true);
  });

  it('reads the persisted boolean (false)', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({ ai_features_enabled: false }),
    });
    expect(await getAiFeaturesEnabled('uid-1')).toBe(false);
  });

  it('reads the persisted boolean (true)', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({ ai_features_enabled: true }),
    });
    expect(await getAiFeaturesEnabled('uid-1')).toBe(true);
  });

  it('falls back to the default when the field is missing on an existing doc', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({ unrelated_field: 1 }),
    });
    expect(await getAiFeaturesEnabled('uid-1')).toBe(DEFAULT_AI_FEATURES_ENABLED);
  });

  it('falls back to the default when the field is a non-boolean (defensive)', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({ ai_features_enabled: 'nope' }),
    });
    expect(await getAiFeaturesEnabled('uid-1')).toBe(DEFAULT_AI_FEATURES_ENABLED);
  });

  it('targets users/{uid}/prefs/user_prefs (matches Android sync path)', async () => {
    getDocMock.mockResolvedValueOnce({ exists: () => false, data: () => undefined });
    await getAiFeaturesEnabled('uid-42');
    expect(docMock).toHaveBeenCalledWith({}, 'users', 'uid-42', 'prefs', 'user_prefs');
  });
});

describe('setAiFeaturesEnabled', () => {
  it('writes ai_features_enabled with merge=true', async () => {
    setDocMock.mockResolvedValueOnce(undefined);
    await setAiFeaturesEnabled('uid-1', false);
    const [, payload, options] = setDocMock.mock.calls[0];
    expect(payload.ai_features_enabled).toBe(false);
    expect(options).toEqual({ merge: true });
  });

  it('tags the field type as "bool" for Android pull-side reconstruction', async () => {
    setDocMock.mockResolvedValueOnce(undefined);
    await setAiFeaturesEnabled('uid-1', true);
    const [, payload] = setDocMock.mock.calls[0];
    expect(payload.__pref_types).toEqual({ ai_features_enabled: 'bool' });
  });

  it('stamps __pref_updated_at so Android last-write-wins picks the newer value', async () => {
    setDocMock.mockResolvedValueOnce(undefined);
    const before = Date.now();
    await setAiFeaturesEnabled('uid-1', true);
    const after = Date.now();
    const [, payload] = setDocMock.mock.calls[0];
    expect(typeof payload.__pref_updated_at).toBe('number');
    expect(payload.__pref_updated_at).toBeGreaterThanOrEqual(before);
    expect(payload.__pref_updated_at).toBeLessThanOrEqual(after);
  });

  it('round-trips: write then read returns the written value', async () => {
    setDocMock.mockResolvedValueOnce(undefined);
    await setAiFeaturesEnabled('uid-1', false);
    const [, payload] = setDocMock.mock.calls[0];

    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => payload,
    });
    expect(await getAiFeaturesEnabled('uid-1')).toBe(false);
  });
});
