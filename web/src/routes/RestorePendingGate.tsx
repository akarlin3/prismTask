import type { ReactNode } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { SplashScreen } from '@/components/shared/SplashScreen';
import { RestorePendingScreen } from '@/features/auth/RestorePendingScreen';

/**
 * Sits between `ProtectedRoute` (auth required) and `OnboardingGate`
 * (onboarding required). When the backend reports the account is in the
 * deletion grace window, takes over the entire authed surface with a
 * full-screen restore-or-sign-out prompt — mirrors Android's
 * `AuthScreen.kt:72-80` `RestorePending` branch.
 *
 *   unknown   → splash (waiting for /auth/me/deletion to resolve)
 *   pending   → render RestorePendingScreen (no AppShell, no nav)
 *   active    → render children (normal flow)
 *
 * The choice MUST happen before any sync runs — otherwise a quiet web sync
 * would re-establish the user as active and silently overwrite the
 * deletion mark, defeating the grace-period guarantee.
 */
interface RestorePendingGateProps {
  children: ReactNode;
}

export function RestorePendingGate({ children }: RestorePendingGateProps) {
  const status = useAuthStore((s) => s.deletionStatus);

  if (status === 'unknown') {
    return <SplashScreen />;
  }
  if (status === 'pending') {
    return <RestorePendingScreen />;
  }
  return <>{children}</>;
}
