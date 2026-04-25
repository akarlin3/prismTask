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
  DEFAULT_REMINDER_MODE_PREFERENCES,
  REMINDER_INTERVAL_MAX_MINUTES,
  REMINDER_INTERVAL_MIN_MINUTES,
  getReminderModePreferences,
  setReminderModePreferences,
} from '@/api/firestore/medicationPreferences';

beforeEach(() => {
  getDocMock.mockReset();
  setDocMock.mockReset();
  docMock.mockReset();
  onSnapshotMock.mockReset();
  docMock.mockReturnValue({});
});

describe('getReminderModePreferences', () => {
  it('returns the CLOCK default when the doc does not exist', async () => {
    getDocMock.mockResolvedValueOnce({ exists: () => false, data: () => undefined });
    const prefs = await getReminderModePreferences('uid-1');
    expect(prefs).toEqual(DEFAULT_REMINDER_MODE_PREFERENCES);
  });

  it('reads the persisted mode + interval', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        reminderModeDefault: 'INTERVAL',
        reminderIntervalDefaultMinutes: 360,
      }),
    });
    const prefs = await getReminderModePreferences('uid-1');
    expect(prefs).toEqual({ mode: 'INTERVAL', interval_default_minutes: 360 });
  });

  it('clamps the interval to the [60, 1440] window when the doc is malformed', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        reminderModeDefault: 'INTERVAL',
        reminderIntervalDefaultMinutes: 9999,
      }),
    });
    const prefs = await getReminderModePreferences('uid-1');
    expect(prefs.interval_default_minutes).toBe(REMINDER_INTERVAL_MAX_MINUTES);
  });

  it('falls back to CLOCK when the mode string is unknown', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({ reminderModeDefault: 'BOGUS' }),
    });
    const prefs = await getReminderModePreferences('uid-1');
    expect(prefs.mode).toBe('CLOCK');
  });
});

describe('setReminderModePreferences', () => {
  it('persists camelCase fields with merge=true', async () => {
    setDocMock.mockResolvedValueOnce(undefined);
    await setReminderModePreferences('uid-1', {
      mode: 'INTERVAL',
      interval_default_minutes: 240,
    });
    const [, payload, options] = setDocMock.mock.calls[0];
    expect(payload).toMatchObject({
      reminderModeDefault: 'INTERVAL',
      reminderIntervalDefaultMinutes: 240,
    });
    expect(options).toEqual({ merge: true });
  });

  it('clamps interval below the minimum on save', async () => {
    setDocMock.mockResolvedValueOnce(undefined);
    await setReminderModePreferences('uid-1', {
      mode: 'INTERVAL',
      interval_default_minutes: 5,
    });
    expect(setDocMock.mock.calls[0][1].reminderIntervalDefaultMinutes).toBe(
      REMINDER_INTERVAL_MIN_MINUTES,
    );
  });
});
