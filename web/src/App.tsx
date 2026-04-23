import { useEffect } from 'react';
import { RouterProvider } from 'react-router-dom';
import { Toaster } from 'sonner';
import { router } from '@/routes';
import { useAuthStore } from '@/stores/authStore';
import { useThemeStore } from '@/stores/themeStore';
import { useBatchStore } from '@/stores/batchStore';
import { useOnboardingStore } from '@/stores/onboardingStore';
import { ErrorBoundary } from '@/components/shared/ErrorBoundary';
import { OfflineBanner } from '@/components/shared/OfflineBanner';

export default function App() {
  const hydrateFromStorage = useAuthStore((s) => s.hydrateFromStorage);
  const applyTheme = useThemeStore((s) => s.applyTheme);
  const themeKey = useThemeStore((s) => s.themeKey);

  const initFirebaseAuthListener = useAuthStore((s) => s.initFirebaseAuthListener);
  const firebaseUid = useAuthStore((s) => s.firebaseUser?.uid);
  const hydrateBatch = useBatchStore((s) => s.hydrate);

  // Initialize Firebase Auth listener + hydrate JWT tokens on mount
  useEffect(() => {
    const unsubscribe = initFirebaseAuthListener();
    hydrateFromStorage();
    return unsubscribe;
  }, [initFirebaseAuthListener, hydrateFromStorage]);

  // Load per-uid batch history from localStorage once the user is known
  // so SettingsScreen + Snackbar undo have access after a refresh.
  useEffect(() => {
    if (firebaseUid) hydrateBatch(firebaseUid);
  }, [firebaseUid, hydrateBatch]);

  // Hydrate onboarding status from Firestore on sign-in. Reset back to
  // "unknown" on sign-out so the next user sees the onboarding flow
  // gate correctly.
  const hydrateOnboarding = useOnboardingStore((s) => s.hydrate);
  const resetOnboarding = useOnboardingStore((s) => s.reset);
  useEffect(() => {
    if (firebaseUid) {
      hydrateOnboarding(firebaseUid);
    } else {
      resetOnboarding();
    }
  }, [firebaseUid, hydrateOnboarding, resetOnboarding]);

  // Apply theme on mount and whenever the user picks a new one. All four
  // named themes are dark-first with no system/light variant — matches
  // Android, so no media-query listener is needed.
  useEffect(() => {
    applyTheme();
  }, [applyTheme, themeKey]);

  // Register service worker for PWA
  useEffect(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => {
        // Service worker registration failed — silent fallback
      });
    }
  }, []);

  return (
    <ErrorBoundary>
      <OfflineBanner />
      <RouterProvider router={router} />
      <Toaster
        position="bottom-right"
        toastOptions={{
          style: {
            background: 'var(--color-bg-card)',
            color: 'var(--color-text-primary)',
            border: '1px solid var(--color-border)',
          },
        }}
      />
    </ErrorBoundary>
  );
}
