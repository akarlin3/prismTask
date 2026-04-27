import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { restoreMock, abandonMock, toastSuccessMock, toastMock } = vi.hoisted(
  () => ({
    restoreMock: vi.fn(),
    abandonMock: vi.fn(),
    toastSuccessMock: vi.fn(),
    toastMock: vi.fn(),
  }),
);

const storeState = {
  deletionScheduledFor: '2026-05-25T12:00:00Z' as string | null,
  restoreAccount: restoreMock,
  abandonRestore: abandonMock,
};

vi.mock('@/stores/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof storeState) => T) => selector(storeState),
}));

vi.mock('sonner', () => {
  const fn = (...args: unknown[]) => toastMock(...args);
  fn.success = (...args: unknown[]) => toastSuccessMock(...args);
  return { toast: fn };
});

import { RestorePendingScreen } from '@/features/auth/RestorePendingScreen';

describe('RestorePendingScreen', () => {
  beforeEach(() => {
    restoreMock.mockReset();
    abandonMock.mockReset();
    toastSuccessMock.mockReset();
    toastMock.mockReset();
    storeState.deletionScheduledFor = '2026-05-25T12:00:00Z';
  });

  it('renders the takeover header and the formatted deletion date', () => {
    render(<RestorePendingScreen />);
    expect(
      screen.getByText(/Your Account Is Scheduled for Deletion/i),
    ).toBeInTheDocument();
    // Date is locale-formatted; we check that the year/month appear in
    // some form rather than an exact string (avoids locale flakiness).
    expect(screen.getByText(/2026/)).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Restore Account/i }),
    ).toBeEnabled();
    expect(screen.getByRole('button', { name: /Sign Out/i })).toBeEnabled();
  });

  it('falls back to a generic body when deletionScheduledFor is null', () => {
    storeState.deletionScheduledFor = null;
    render(<RestorePendingScreen />);
    expect(
      screen.getByText(/in the deletion grace window/i),
    ).toBeInTheDocument();
  });

  it('Restore Account calls restoreAccount and shows a success toast', async () => {
    const user = userEvent.setup();
    restoreMock.mockResolvedValueOnce(undefined);

    render(<RestorePendingScreen />);
    await user.click(screen.getByRole('button', { name: /Restore Account/i }));

    await waitFor(() => expect(restoreMock).toHaveBeenCalledTimes(1));
    expect(toastSuccessMock).toHaveBeenCalledWith(
      expect.stringMatching(/Account restored/i),
    );
    expect(abandonMock).not.toHaveBeenCalled();
  });

  it('surfaces an error message on the screen if Restore Account fails', async () => {
    const user = userEvent.setup();
    restoreMock.mockRejectedValueOnce(new Error('Backend exploded'));

    render(<RestorePendingScreen />);
    await user.click(screen.getByRole('button', { name: /Restore Account/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Backend exploded/);
    });
    // No success toast on failure.
    expect(toastSuccessMock).not.toHaveBeenCalled();
  });

  it('Sign Out calls abandonRestore and shows a confirmation toast', async () => {
    const user = userEvent.setup();
    abandonMock.mockResolvedValueOnce(undefined);

    render(<RestorePendingScreen />);
    await user.click(screen.getByRole('button', { name: /Sign Out/i }));

    await waitFor(() => expect(abandonMock).toHaveBeenCalledTimes(1));
    expect(toastMock).toHaveBeenCalledWith(
      expect.stringMatching(/Signed out/i),
    );
    expect(restoreMock).not.toHaveBeenCalled();
  });
});
