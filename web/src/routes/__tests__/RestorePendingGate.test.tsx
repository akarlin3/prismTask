import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const storeState = {
  deletionStatus: 'unknown' as 'unknown' | 'active' | 'pending',
};

vi.mock('@/stores/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof storeState) => T) => selector(storeState),
}));

vi.mock('@/components/shared/SplashScreen', () => ({
  SplashScreen: () => <div data-testid="splash" />,
}));

vi.mock('@/features/auth/RestorePendingScreen', () => ({
  RestorePendingScreen: () => <div data-testid="restore-pending" />,
}));

import { RestorePendingGate } from '@/routes/RestorePendingGate';

function ChildMarker() {
  return <div data-testid="protected-child" />;
}

describe('RestorePendingGate', () => {
  beforeEach(() => {
    storeState.deletionStatus = 'unknown';
  });

  it('shows the splash while the deletion check is pending', () => {
    storeState.deletionStatus = 'unknown';
    render(
      <RestorePendingGate>
        <ChildMarker />
      </RestorePendingGate>,
    );
    expect(screen.getByTestId('splash')).toBeInTheDocument();
    expect(screen.queryByTestId('protected-child')).toBeNull();
    expect(screen.queryByTestId('restore-pending')).toBeNull();
  });

  it('takes over with RestorePendingScreen when status is "pending"', () => {
    storeState.deletionStatus = 'pending';
    render(
      <RestorePendingGate>
        <ChildMarker />
      </RestorePendingGate>,
    );
    expect(screen.getByTestId('restore-pending')).toBeInTheDocument();
    // CRITICAL parity assertion: the protected child must NOT render
    // when deletion is pending. If this regresses, the user could reach
    // sync surfaces and silently overwrite the deletion mark.
    expect(screen.queryByTestId('protected-child')).toBeNull();
  });

  it('renders children normally when status is "active"', () => {
    storeState.deletionStatus = 'active';
    render(
      <RestorePendingGate>
        <ChildMarker />
      </RestorePendingGate>,
    );
    expect(screen.getByTestId('protected-child')).toBeInTheDocument();
    expect(screen.queryByTestId('restore-pending')).toBeNull();
    expect(screen.queryByTestId('splash')).toBeNull();
  });
});
