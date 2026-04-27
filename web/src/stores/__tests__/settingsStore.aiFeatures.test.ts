import { describe, it, expect, vi, beforeEach } from 'vitest';

const { setAiFeaturesEnabledMock, getAiFeaturesEnabledMock, getFirebaseUidMock } =
  vi.hoisted(() => ({
    setAiFeaturesEnabledMock: vi.fn(),
    getAiFeaturesEnabledMock: vi.fn(),
    getFirebaseUidMock: vi.fn(),
  }));

vi.mock('@/api/firestore/aiPreferences', () => ({
  DEFAULT_AI_FEATURES_ENABLED: true,
  getAiFeaturesEnabled: getAiFeaturesEnabledMock,
  setAiFeaturesEnabled: setAiFeaturesEnabledMock,
}));

vi.mock('@/stores/firebaseUid', () => ({
  getFirebaseUid: getFirebaseUidMock,
  setFirebaseUid: vi.fn(),
}));

// We don't need the real axios client for these tests.
vi.mock('@/api/client', () => ({
  default: {},
  setAiFeaturesEnabledProvider: vi.fn(),
}));

import { useSettingsStore } from '@/stores/settingsStore';

beforeEach(() => {
  setAiFeaturesEnabledMock.mockReset();
  getAiFeaturesEnabledMock.mockReset();
  getFirebaseUidMock.mockReset();
  // Reset store back to default-on between tests.
  useSettingsStore.setState({ aiFeaturesEnabled: true });
});

describe('settingsStore — aiFeaturesEnabled', () => {
  it('defaults to true (opt-out, not opt-in — matches Android)', () => {
    expect(useSettingsStore.getState().aiFeaturesEnabled).toBe(true);
  });

  it('toggling updates local state immediately (optimistic)', () => {
    useSettingsStore.getState().setSetting('aiFeaturesEnabled', false);
    expect(useSettingsStore.getState().aiFeaturesEnabled).toBe(false);
  });

  it('toggling pushes the new value to Firestore when signed in', async () => {
    getFirebaseUidMock.mockReturnValue('uid-1');
    setAiFeaturesEnabledMock.mockResolvedValueOnce(undefined);

    useSettingsStore.getState().setSetting('aiFeaturesEnabled', false);

    // Push is fire-and-forget via a microtask + dynamic import; flush.
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));

    expect(setAiFeaturesEnabledMock).toHaveBeenCalledTimes(1);
    expect(setAiFeaturesEnabledMock).toHaveBeenCalledWith('uid-1', false);
  });

  it('does NOT push to Firestore when signed out (local-only is fine)', async () => {
    getFirebaseUidMock.mockImplementation(() => {
      throw new Error('Not authenticated');
    });

    useSettingsStore.getState().setSetting('aiFeaturesEnabled', false);
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));

    expect(setAiFeaturesEnabledMock).not.toHaveBeenCalled();
    // Local toggle still applied.
    expect(useSettingsStore.getState().aiFeaturesEnabled).toBe(false);
  });

  it('Firestore push failure does NOT roll back the local toggle', async () => {
    getFirebaseUidMock.mockReturnValue('uid-1');
    setAiFeaturesEnabledMock.mockRejectedValueOnce(new Error('network down'));
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    useSettingsStore.getState().setSetting('aiFeaturesEnabled', false);
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));

    expect(useSettingsStore.getState().aiFeaturesEnabled).toBe(false);
    expect(consoleWarnSpy).toHaveBeenCalled();
    consoleWarnSpy.mockRestore();
  });

  it('loadAiFeaturesFromFirestore pulls the remote value and updates local state', async () => {
    getAiFeaturesEnabledMock.mockResolvedValueOnce(false);

    await useSettingsStore.getState().loadAiFeaturesFromFirestore('uid-99');

    expect(getAiFeaturesEnabledMock).toHaveBeenCalledWith('uid-99');
    expect(useSettingsStore.getState().aiFeaturesEnabled).toBe(false);
  });

  it('loadAiFeaturesFromFirestore swallows read errors (offline tolerance)', async () => {
    getAiFeaturesEnabledMock.mockRejectedValueOnce(new Error('offline'));
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    // Pre-set so we can confirm state isn't clobbered.
    useSettingsStore.setState({ aiFeaturesEnabled: true });

    await expect(
      useSettingsStore.getState().loadAiFeaturesFromFirestore('uid-99'),
    ).resolves.toBeUndefined();

    expect(useSettingsStore.getState().aiFeaturesEnabled).toBe(true);
    expect(consoleWarnSpy).toHaveBeenCalled();
    consoleWarnSpy.mockRestore();
  });

  it('round-trip: setSetting writes Firestore, load reads back the same value', async () => {
    getFirebaseUidMock.mockReturnValue('uid-rt');
    let capturedWrite: boolean | null = null;
    setAiFeaturesEnabledMock.mockImplementation(async (_uid, v) => {
      capturedWrite = v;
    });
    getAiFeaturesEnabledMock.mockImplementation(async () => capturedWrite ?? true);

    useSettingsStore.getState().setSetting('aiFeaturesEnabled', false);
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));
    expect(capturedWrite).toBe(false);

    // Simulate fresh page load: clobber local state to "true" then pull.
    useSettingsStore.setState({ aiFeaturesEnabled: true });
    await useSettingsStore.getState().loadAiFeaturesFromFirestore('uid-rt');
    expect(useSettingsStore.getState().aiFeaturesEnabled).toBe(false);
  });
});
