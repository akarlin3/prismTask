import { describe, it, expect, beforeEach, vi } from 'vitest';

const { getDocMock, setDocMock } = vi.hoisted(() => ({
  getDocMock: vi.fn(),
  setDocMock: vi.fn(),
}));

vi.mock('firebase/firestore', () => ({
  doc: (...args: unknown[]) => ({ __doc: args }),
  getDoc: getDocMock,
  setDoc: setDocMock,
}));
vi.mock('@/lib/firebase', () => ({ firestore: { __mock: true } }));

import { useOnboardingStore } from '@/stores/onboardingStore';

function resetStore() {
  useOnboardingStore.setState({ status: 'unknown', completedAt: null });
}

describe('useOnboardingStore', () => {
  beforeEach(() => {
    getDocMock.mockReset();
    setDocMock.mockReset();
    resetStore();
  });

  it('hydrates to pending when the user doc has no onboardingCompletedAt', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => false,
      data: () => undefined,
    });
    await useOnboardingStore.getState().hydrate('uid-1');
    expect(useOnboardingStore.getState().status).toBe('pending');
    expect(useOnboardingStore.getState().completedAt).toBeNull();
  });

  it('hydrates to completed when the user doc carries onboardingCompletedAt', async () => {
    getDocMock.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({ onboardingCompletedAt: 1735689600_000 }),
    });
    await useOnboardingStore.getState().hydrate('uid-1');
    expect(useOnboardingStore.getState().status).toBe('completed');
    expect(useOnboardingStore.getState().completedAt).toBe(1735689600_000);
  });

  it('falls back to pending on Firestore read error', async () => {
    getDocMock.mockRejectedValueOnce(new Error('network'));
    await useOnboardingStore.getState().hydrate('uid-1');
    expect(useOnboardingStore.getState().status).toBe('pending');
  });

  it('markCompleted flips local state first then writes Firestore with merge', async () => {
    setDocMock.mockResolvedValueOnce(undefined);
    const before = Date.now();
    await useOnboardingStore.getState().markCompleted('uid-1');
    const after = Date.now();
    const state = useOnboardingStore.getState();
    expect(state.status).toBe('completed');
    expect(state.completedAt).toBeGreaterThanOrEqual(before);
    expect(state.completedAt).toBeLessThanOrEqual(after);
    expect(setDocMock).toHaveBeenCalledTimes(1);
    const [, payload, opts] = setDocMock.mock.calls[0];
    expect(payload).toMatchObject({ onboardingCompletedAt: expect.any(Number) });
    expect(opts).toEqual({ merge: true });
  });

  it('markCompleted keeps local state complete even if the write fails', async () => {
    setDocMock.mockRejectedValueOnce(new Error('rules'));
    await useOnboardingStore.getState().markCompleted('uid-1');
    // Local state must still be completed so the user can finish their
    // session — next hydrate on reload will redirect back if the write
    // actually failed to persist.
    expect(useOnboardingStore.getState().status).toBe('completed');
  });

  it('reset wipes to unknown', () => {
    useOnboardingStore.setState({ status: 'completed', completedAt: 123 });
    useOnboardingStore.getState().reset();
    expect(useOnboardingStore.getState().status).toBe('unknown');
    expect(useOnboardingStore.getState().completedAt).toBeNull();
  });
});
