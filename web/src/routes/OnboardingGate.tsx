import { Navigate } from 'react-router-dom';
import { useOnboardingStore } from '@/stores/onboardingStore';
import { SplashScreen } from '@/components/shared/SplashScreen';
import type { ReactNode } from 'react';

/**
 * Gates the authed AppShell routes on onboarding completion. Sits
 * between `ProtectedRoute` (which guarantees the user is signed in)
 * and `AppShell` (the main app layout).
 *
 *   unknown   → splash (waiting for Firestore hydrate)
 *   pending   → redirect to /onboarding
 *   completed → render children
 *
 * The /onboarding route itself skips this gate — it lives outside the
 * AppShell in the route tree.
 */
interface OnboardingGateProps {
  children: ReactNode;
}

export function OnboardingGate({ children }: OnboardingGateProps) {
  const status = useOnboardingStore((s) => s.status);

  if (status === 'unknown') {
    return <SplashScreen />;
  }
  if (status === 'pending') {
    return <Navigate to="/onboarding" replace />;
  }
  return <>{children}</>;
}
