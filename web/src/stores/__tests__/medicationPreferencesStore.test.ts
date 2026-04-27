import { describe, it, expect, beforeEach, vi } from 'vitest';

const { subscribeMock, unsubscribeMock } = vi.hoisted(() => ({
  subscribeMock: vi.fn(),
  unsubscribeMock: vi.fn(),
}));

vi.mock('@/api/firestore/medicationPreferences', async () => {
  const actual = await vi.importActual<
    typeof import('@/api/firestore/medicationPreferences')
  >('@/api/firestore/medicationPreferences');
  return {
    ...actual,
    subscribeToReminderModePreferences: subscribeMock,
  };
});
vi.mock('@/lib/firebase', () => ({ firestore: { __mock: true } }));

import { useMedicationPreferencesStore } from '@/stores/medicationPreferencesStore';
import {
  DEFAULT_REMINDER_MODE_PREFERENCES,
  type MedicationReminderModePreferences,
} from '@/api/firestore/medicationPreferences';

function resetStore() {
  useMedicationPreferencesStore.setState({
    prefs: DEFAULT_REMINDER_MODE_PREFERENCES,
  });
}

const customPrefs: MedicationReminderModePreferences = {
  mode: 'INTERVAL',
  interval_default_minutes: 360,
};

describe('useMedicationPreferencesStore', () => {
  beforeEach(() => {
    subscribeMock.mockReset();
    unsubscribeMock.mockReset();
    subscribeMock.mockReturnValue(unsubscribeMock);
    resetStore();
  });

  it('starts with the default reminder-mode preferences', () => {
    expect(useMedicationPreferencesStore.getState().prefs).toEqual(
      DEFAULT_REMINDER_MODE_PREFERENCES,
    );
  });

  it('applyRemotePreferences replaces local state with the remote snapshot', () => {
    useMedicationPreferencesStore
      .getState()
      .applyRemotePreferences(customPrefs);
    expect(useMedicationPreferencesStore.getState().prefs).toEqual(customPrefs);
  });

  it('subscribeToPreferences forwards uid and pipes snapshots into state', () => {
    const unsub = useMedicationPreferencesStore
      .getState()
      .subscribeToPreferences('uid-2');

    expect(subscribeMock).toHaveBeenCalledTimes(1);
    expect(subscribeMock).toHaveBeenCalledWith('uid-2', expect.any(Function));
    expect(unsub).toBe(unsubscribeMock);

    const callback = subscribeMock.mock.calls[0][1] as (
      prefs: MedicationReminderModePreferences,
    ) => void;
    callback(customPrefs);
    expect(useMedicationPreferencesStore.getState().prefs).toEqual(customPrefs);
  });

  it('reset restores the default preferences', () => {
    useMedicationPreferencesStore.setState({ prefs: customPrefs });
    useMedicationPreferencesStore.getState().reset();
    expect(useMedicationPreferencesStore.getState().prefs).toEqual(
      DEFAULT_REMINDER_MODE_PREFERENCES,
    );
  });
});
