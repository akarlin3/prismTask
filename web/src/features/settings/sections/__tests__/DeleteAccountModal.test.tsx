import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { requestMock, logoutMock, updateDocMock } = vi.hoisted(() => ({
  requestMock: vi.fn(),
  logoutMock: vi.fn(),
  updateDocMock: vi.fn(),
}));

vi.mock('@/api/auth', () => ({
  authApi: { requestAccountDeletion: requestMock },
}));

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({ firebaseUid: 'uid-123', logout: logoutMock }),
}));

vi.mock('firebase/firestore', async () => {
  const actual = await vi.importActual<typeof import('firebase/firestore')>(
    'firebase/firestore',
  );
  return {
    ...actual,
    doc: vi.fn(() => ({})),
    updateDoc: updateDocMock,
    serverTimestamp: vi.fn(() => 'SERVER_TS'),
  };
});

vi.mock('@/lib/firebase', () => ({
  firestore: {},
}));

import { DeleteAccountModal } from '@/features/settings/sections/DeleteAccountModal';

describe('DeleteAccountModal', () => {
  beforeEach(() => {
    requestMock.mockReset();
    logoutMock.mockReset();
    updateDocMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function renderOpen() {
    return render(<DeleteAccountModal isOpen onClose={() => {}} />);
  }

  it('opens in the explain step and advances to confirming on Continue', async () => {
    const user = userEvent.setup();
    renderOpen();

    expect(screen.getByText(/Deleting your account will:/)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('DELETE')).toBeNull();

    await user.click(screen.getByRole('button', { name: /Continue/i }));

    // After advancing, the typed-DELETE input is present and the
    // explain copy is gone.
    expect(screen.getByPlaceholderText('DELETE')).toBeInTheDocument();
    expect(screen.queryByText(/Deleting your account will:/)).toBeNull();
  });

  it('disables the destructive submit until typed text trimmed equals DELETE', async () => {
    const user = userEvent.setup();
    renderOpen();
    await user.click(screen.getByRole('button', { name: /Continue/i }));

    const input = screen.getByPlaceholderText('DELETE');
    const submit = screen.getByRole('button', { name: /Delete account/i });

    expect(submit).toBeDisabled();

    await user.type(input, 'delete'); // wrong case
    expect(submit).toBeDisabled();

    await user.clear(input);
    await user.type(input, '  DELETE  '); // trim-tolerant
    expect(submit).toBeEnabled();
  });

  it('calls requestAccountDeletion("web"), mirrors to Firestore, then logs out on success', async () => {
    requestMock.mockResolvedValueOnce({
      deletion_pending_at: '2026-04-25T12:00:00Z',
      deletion_scheduled_for: '2026-05-25T12:00:00Z',
      deletion_initiated_from: 'web',
    });
    updateDocMock.mockResolvedValueOnce(undefined);

    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    renderOpen();
    await user.click(screen.getByRole('button', { name: /Continue/i }));
    await user.type(screen.getByPlaceholderText('DELETE'), 'DELETE');
    await user.click(screen.getByRole('button', { name: /Delete account/i }));

    await waitFor(() => expect(requestMock).toHaveBeenCalledWith('web'));
    await waitFor(() => expect(updateDocMock).toHaveBeenCalled());
    const updatePayload = updateDocMock.mock.calls[0][1];
    expect(updatePayload.deletion_initiated_from).toBe('web');
    expect(updatePayload.deletion_pending_at).toBe('SERVER_TS');

    expect(logoutMock).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(900);
    expect(logoutMock).toHaveBeenCalledTimes(1);
  });

  it('keeps the modal open on backend failure and surfaces the error', async () => {
    requestMock.mockRejectedValueOnce(new Error('Backend exploded'));
    const user = userEvent.setup();

    renderOpen();
    await user.click(screen.getByRole('button', { name: /Continue/i }));
    await user.type(screen.getByPlaceholderText('DELETE'), 'DELETE');
    await user.click(screen.getByRole('button', { name: /Delete account/i }));

    await waitFor(() => {
      expect(screen.getByText('Backend exploded')).toBeInTheDocument();
    });
    expect(logoutMock).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /Retry/i })).toBeEnabled();
  });

  it('proceeds with logout even if the Firestore mirror write fails', async () => {
    requestMock.mockResolvedValueOnce({
      deletion_pending_at: '2026-04-25T12:00:00Z',
      deletion_scheduled_for: '2026-05-25T12:00:00Z',
      deletion_initiated_from: 'web',
    });
    updateDocMock.mockRejectedValueOnce(new Error('Firestore down'));

    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    renderOpen();
    await user.click(screen.getByRole('button', { name: /Continue/i }));
    await user.type(screen.getByPlaceholderText('DELETE'), 'DELETE');
    await user.click(screen.getByRole('button', { name: /Delete account/i }));

    await waitFor(() => expect(requestMock).toHaveBeenCalled());
    await vi.advanceTimersByTimeAsync(900);
    expect(logoutMock).toHaveBeenCalledTimes(1);
  });
});
